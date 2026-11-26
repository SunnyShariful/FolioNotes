package com.example.data.repository

import com.example.data.local.SharedPrefsNoteStore
import com.example.data.local.SqliteNoteStore
import com.example.data.model.Note
import com.example.data.model.NoteDisplaySource
import com.example.data.model.NotesSnapshot
import com.example.data.model.PrefsOrigin
import com.example.data.model.SharedPrefsNoteRecord
import com.example.data.model.SqliteNoteRecord
import com.example.data.model.StorageMethod
import com.example.data.model.SyncResult
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NotesRepository(
  private val sqliteStore: SqliteNoteStore,
  private val sharedPrefsStore: SharedPrefsNoteStore,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
  private val mutex = Mutex()

  suspend fun loadSnapshot(): NotesSnapshot =
    withContext(ioDispatcher) {
      mutex.withLock { readSnapshotLocked() }
    }

  suspend fun createNote(
    title: String,
    content: String,
    storageMethod: StorageMethod,
  ): Boolean =
    withContext(ioDispatcher) {
      mutex.withLock {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
          return@withLock false
        }

        val now = clock()
        val id = generateUniqueIdLocked()
        when (storageMethod) {
          StorageMethod.SQLITE -> {
            sqliteStore.insertNote(
              SqliteNoteRecord(
                id = id,
                title = trimmedTitle,
                content = trimmedContent,
                createdAt = now,
                updatedAt = now,
                isSynced = false,
                lastSyncedAt = null,
              )
            )
          }
          StorageMethod.SHARED_PREFERENCES -> {
            sharedPrefsStore.saveNote(
              SharedPrefsNoteRecord(
                id = id,
                title = trimmedTitle,
                content = trimmedContent,
                createdAt = now,
                updatedAt = now,
                origin = PrefsOrigin.SHARED_PREFERENCES,
              )
            )
          }
        }
      }
    }

  suspend fun updateNote(
    id: String,
    title: String,
    content: String,
    storageMethod: StorageMethod,
  ): Boolean =
    withContext(ioDispatcher) {
      mutex.withLock {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (id.isBlank() || trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
          return@withLock false
        }

        val now = clock()
        when (storageMethod) {
          StorageMethod.SQLITE -> {
            sqliteStore.updateNote(
              id = id,
              title = trimmedTitle,
              content = trimmedContent,
              updatedAt = now,
            )
          }
          StorageMethod.SHARED_PREFERENCES -> {
            val existing = sharedPrefsStore.getNoteById(id) ?: return@withLock false
            if (existing.origin != PrefsOrigin.SHARED_PREFERENCES) {
              return@withLock false
            }
            sharedPrefsStore.saveNote(
              existing.copy(
                title = trimmedTitle,
                content = trimmedContent,
                updatedAt = now,
                origin = PrefsOrigin.SHARED_PREFERENCES,
              )
            )
          }
        }
      }
    }

  suspend fun deleteNote(note: Note): Boolean =
    withContext(ioDispatcher) {
      mutex.withLock {
        when (note.storageMethod) {
          StorageMethod.SQLITE -> {
            val prefsEntry = sharedPrefsStore.getNoteById(note.id)
            val hasSyncedCopy = prefsEntry?.origin == PrefsOrigin.SYNCED_FROM_SQLITE
            sqliteStore.deleteNoteWithTombstone(
              noteId = note.id,
              hasSyncedCopyInPrefs = hasSyncedCopy,
              deletedAt = clock(),
            )
          }
          StorageMethod.SHARED_PREFERENCES -> {
            val deleted = sharedPrefsStore.deleteNote(note.id)
            if (deleted && note.id in sqliteStore.getPendingDeletedIds()) {
              try {
                sqliteStore.finalizeSyncMetadata(
                  syncedNoteIds = emptyList(),
                  processedTombstoneIds = setOf(note.id),
                  syncTimestamp = clock(),
                )
              } catch (_: Exception) {
                // Keep tombstone intact if SQLite cleanup fails; next Sync will clear it safely.
              }
            }
            deleted
          }
        }
      }
    }

  suspend fun syncSqliteToSharedPreferences(): SyncResult =
    withContext(ioDispatcher) {
      mutex.withLock {
        val sqliteNotes: List<SqliteNoteRecord>
        val pendingDeletedIds: Set<String>
        val prefsRecords: List<SharedPrefsNoteRecord>
        try {
          sqliteNotes = sqliteStore.getAllNotes()
          pendingDeletedIds = sqliteStore.getPendingDeletedIds()
          prefsRecords = sharedPrefsStore.getAllNotes()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          return@withLock SyncResult.Failure
        }

        val activeSqliteIds = sqliteNotes.map { it.id }.toSet()
        val prefsMap = prefsRecords.associateBy { it.id }
        val usedIds = (activeSqliteIds + prefsMap.keys).toMutableSet()

        val removals = mutableSetOf<String>()
        val upserts = mutableListOf<SharedPrefsNoteRecord>()
        val syncedNoteIds = sqliteNotes.map { it.id }

        var inserted = 0
        var updated = 0
        var deleted = 0
        var collisionRekeyed = 0

        // 1. Process deletion tombstones for notes removed from SQLite.
        for (tombstoneId in pendingDeletedIds) {
          if (tombstoneId in activeSqliteIds) continue
          val existingPrefs = prefsMap[tombstoneId]
          if (existingPrefs != null && existingPrefs.origin == PrefsOrigin.SYNCED_FROM_SQLITE) {
            removals.add(tombstoneId)
            deleted++
          }
        }

        // 2. Apply SQLite source-of-truth insertions and updates with origin protection.
        for (sq in sqliteNotes) {
          val desired =
            SharedPrefsNoteRecord(
              id = sq.id,
              title = sq.title,
              content = sq.content,
              createdAt = sq.createdAt,
              updatedAt = sq.updatedAt,
              origin = PrefsOrigin.SYNCED_FROM_SQLITE,
            )
          val existing = prefsMap[sq.id]
          when {
            existing == null -> {
              upserts.add(desired)
              inserted++
            }
            existing.origin == PrefsOrigin.SYNCED_FROM_SQLITE -> {
              if (existing != desired) {
                upserts.add(desired)
                updated++
              }
            }
            existing.origin == PrefsOrigin.SHARED_PREFERENCES -> {
              // Unexpected UUID collision: preserve independent SharedPreferences note under a new UUID.
              var rekeyId = idGenerator()
              while (!usedIds.add(rekeyId)) {
                rekeyId = idGenerator()
              }
              upserts.add(existing.copy(id = rekeyId, origin = PrefsOrigin.SHARED_PREFERENCES))
              upserts.add(desired)
              collisionRekeyed++
              inserted++
            }
          }
        }

        // Stage 1: Commit SharedPreferences batch synchronously and verify disk flush.
        if (removals.isNotEmpty() || upserts.isNotEmpty()) {
          val committed =
            try {
              sharedPrefsStore.applySyncBatch(removals = removals, upserts = upserts)
            } catch (e: CancellationException) {
              throw e
            } catch (_: Exception) {
              false
            }
          if (!committed) {
            return@withLock SyncResult.Failure
          }
        }

        // Stage 2: Finalize SQLite sync flags and clear processed tombstones only after Stage 1 succeeds.
        val needsSqliteFinalization =
          pendingDeletedIds.isNotEmpty() || sqliteNotes.any { !it.isSynced } || upserts.isNotEmpty()
        if (needsSqliteFinalization) {
          try {
            sqliteStore.finalizeSyncMetadata(
              syncedNoteIds = syncedNoteIds,
              processedTombstoneIds = pendingDeletedIds,
              syncTimestamp = clock(),
            )
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // Pending tombstones and is_synced flags remain in SQLite for idempotent retry.
            return@withLock SyncResult.Failure
          }
        }

        SyncResult.Success(
          inserted = inserted,
          updated = updated,
          deleted = deleted,
          collisionRekeyed = collisionRekeyed,
        )
      }
    }

  private fun generateUniqueIdLocked(): String {
    val existingIds =
      (sqliteStore.getAllNotes().map { it.id } + sharedPrefsStore.getAllNotes().map { it.id })
        .toSet()
    var candidate = idGenerator()
    while (candidate in existingIds) {
      candidate = idGenerator()
    }
    return candidate
  }

  private fun readSnapshotLocked(): NotesSnapshot {
    val sqliteRecords = sqliteStore.getAllNotes()
    val pendingDeletedIds = sqliteStore.getPendingDeletedIds()
    val prefsRecords = sharedPrefsStore.getAllNotes()
    val prefsById = prefsRecords.associateBy { it.id }

    var pendingNoteSyncs = 0
    val sqliteUiNotes =
      sqliteRecords.map { sq ->
        val mirrored = prefsById[sq.id]?.takeIf { it.origin == PrefsOrigin.SYNCED_FROM_SQLITE }
        val isMirroredAndCurrent =
          sq.isSynced &&
            mirrored != null &&
            mirrored.title == sq.title &&
            mirrored.content == sq.content &&
            mirrored.updatedAt == sq.updatedAt &&
            mirrored.createdAt == sq.createdAt
        if (!isMirroredAndCurrent) {
          pendingNoteSyncs++
        }
        Note(
          id = sq.id,
          title = sq.title,
          content = sq.content,
          createdAt = sq.createdAt,
          updatedAt = sq.updatedAt,
          storageMethod = StorageMethod.SQLITE,
          isSynced = isMirroredAndCurrent,
          displaySource =
            if (isMirroredAndCurrent) NoteDisplaySource.SYNCED_IN_BOTH
            else NoteDisplaySource.SQLITE_ONLY,
        )
      }

    val spOnlyUiNotes =
      prefsRecords
        .filter { it.origin == PrefsOrigin.SHARED_PREFERENCES }
        .map { sp ->
          Note(
            id = sp.id,
            title = sp.title,
            content = sp.content,
            createdAt = sp.createdAt,
            updatedAt = sp.updatedAt,
            storageMethod = StorageMethod.SHARED_PREFERENCES,
            isSynced = false,
            displaySource = NoteDisplaySource.SHARED_PREFS_ONLY,
          )
        }

    val sqliteMapById = sqliteUiNotes.associateBy { it.id }
    val prefsTabNotes =
      prefsRecords
        .map { sp ->
          when (sp.origin) {
            PrefsOrigin.SHARED_PREFERENCES ->
              Note(
                id = sp.id,
                title = sp.title,
                content = sp.content,
                createdAt = sp.createdAt,
                updatedAt = sp.updatedAt,
                storageMethod = StorageMethod.SHARED_PREFERENCES,
                isSynced = false,
                displaySource = NoteDisplaySource.SHARED_PREFS_ONLY,
              )
            PrefsOrigin.SYNCED_FROM_SQLITE -> {
              // Reflect the actual SharedPreferences mirror state while keeping SQLite ownership.
              val sqliteOwner = sqliteMapById[sp.id]
              val isCurrentMirror = sqliteOwner?.isSynced == true
              Note(
                id = sp.id,
                title = sp.title,
                content = sp.content,
                createdAt = sp.createdAt,
                updatedAt = sp.updatedAt,
                storageMethod =
                  if (sqliteOwner != null) StorageMethod.SQLITE
                  else StorageMethod.SHARED_PREFERENCES,
                isSynced = isCurrentMirror,
                displaySource =
                  if (isCurrentMirror) NoteDisplaySource.SYNCED_IN_BOTH
                  else NoteDisplaySource.SQLITE_ONLY,
              )
            }
          }
        }
        .sortedByDescending { it.updatedAt }

    val allNotes = (sqliteUiNotes + spOnlyUiNotes).sortedByDescending { it.updatedAt }

    return NotesSnapshot(
      allNotes = allNotes,
      sqliteNotes = sqliteUiNotes,
      sharedPrefsNotes = prefsTabNotes,
      sqliteCount = sqliteUiNotes.size,
      sharedPrefsCount = prefsTabNotes.size,
      pendingSyncCount = pendingNoteSyncs + pendingDeletedIds.size,
    )
  }
}
