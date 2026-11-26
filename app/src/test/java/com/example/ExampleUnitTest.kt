package com.example

import com.example.data.local.SharedPrefsNoteStore
import com.example.data.local.SqliteNoteStore
import com.example.data.model.NoteDisplaySource
import com.example.data.model.NoteFilterTab
import com.example.data.model.PrefsOrigin
import com.example.data.model.SharedPrefsNoteRecord
import com.example.data.model.SqliteNoteRecord
import com.example.data.model.StorageMethod
import com.example.data.model.SyncResult
import com.example.data.repository.NotesRepository
import com.example.ui.notes.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeSqlite: InMemorySqliteStore
  private lateinit var fakePrefs: InMemorySharedPrefsStore
  private lateinit var repository: NotesRepository
  private var fakeTime = 1_000L

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeSqlite = InMemorySqliteStore()
    fakePrefs = InMemorySharedPrefsStore()
    repository =
      NotesRepository(
        sqliteStore = fakeSqlite,
        sharedPrefsStore = fakePrefs,
        ioDispatcher = testDispatcher,
        clock = { ++fakeTime },
      )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `crud and in-place edit preserve uuid and original storage engine`() = runTest {
    assertTrue(repository.createNote("  SQLite Note  ", "  Body A  ", StorageMethod.SQLITE))
    assertTrue(repository.createNote("Prefs Note", "Body B", StorageMethod.SHARED_PREFERENCES))

    val initial = repository.loadSnapshot()
    assertEquals(2, initial.allNotes.size)
    assertEquals(1, initial.sqliteCount)
    assertEquals(1, initial.sharedPrefsCount)

    val sqNote = initial.sqliteNotes.single()
    assertEquals("SQLite Note", sqNote.title)
    assertTrue(repository.updateNote(sqNote.id, "SQLite Edited", "Body A2", StorageMethod.SQLITE))

    val afterEdit = repository.loadSnapshot()
    assertEquals(2, afterEdit.allNotes.size)
    assertEquals(1, afterEdit.sqliteCount)
    assertEquals(sqNote.id, afterEdit.sqliteNotes.single().id)
    assertEquals("SQLite Edited", afterEdit.sqliteNotes.single().title)
  }

  @Test
  fun `editing synced sqlite note marks dirty state and keeps actual sharedprefs mirror until sync`() =
    runTest {
      assertTrue(repository.createNote("Title v1", "Body v1", StorageMethod.SQLITE))
      assertEquals(
        SyncResult.Success(inserted = 1, updated = 0, deleted = 0),
        repository.syncSqliteToSharedPreferences(),
      )

      val syncedNote = repository.loadSnapshot().sqliteNotes.single()
      assertEquals(NoteDisplaySource.SYNCED_IN_BOTH, syncedNote.displaySource)
      assertTrue(syncedNote.isSynced)

      assertTrue(repository.updateNote(syncedNote.id, "Title v2", "Body v2", StorageMethod.SQLITE))
      assertFalse(fakeSqlite.getAllNotes().single().isSynced)

      val dirtySnapshot = repository.loadSnapshot()
      val dirtySqliteNote = dirtySnapshot.sqliteNotes.single()
      val mirrorInPrefsTab = dirtySnapshot.sharedPrefsNotes.single()
      assertEquals(1, dirtySnapshot.pendingSyncCount)
      assertFalse(dirtySqliteNote.isSynced)
      assertEquals(NoteDisplaySource.SQLITE_ONLY, dirtySqliteNote.displaySource)
      assertEquals("Title v2", dirtySqliteNote.title)
      // SharedPrefs tab shows what is actually stored in SharedPreferences until Sync runs.
      assertEquals("Title v1", mirrorInPrefsTab.title)
      assertEquals(NoteDisplaySource.SQLITE_ONLY, mirrorInPrefsTab.displaySource)

      // Opening from SharedPrefs tab loads authoritative SQLite text in ViewModel.
      val viewModel = NotesViewModel(repository)
      advanceUntilIdle()
      viewModel.openEditEditor(mirrorInPrefsTab)
      assertEquals("Title v2", viewModel.uiState.value.editorState.title)
      assertEquals(StorageMethod.SQLITE, viewModel.uiState.value.editorState.storageMethod)
    }

  @Test
  fun `deletion tombstone removes synced copy on sync and preserves SP-only notes`() = runTest {
    repository.createNote("Sync Note", "From SQLite", StorageMethod.SQLITE)
    repository.createNote("Local Prefs", "Only Prefs", StorageMethod.SHARED_PREFERENCES)

    val sync1 = repository.syncSqliteToSharedPreferences()
    assertEquals(SyncResult.Success(inserted = 1, updated = 0, deleted = 0), sync1)

    val snapshotAfterSync = repository.loadSnapshot()
    val syncedNote = snapshotAfterSync.sqliteNotes.single()
    assertEquals(NoteDisplaySource.SYNCED_IN_BOTH, syncedNote.displaySource)
    assertEquals(2, snapshotAfterSync.sharedPrefsCount)

    // Delete synced SQLite note -> tombstone persisted until next sync.
    assertTrue(repository.deleteNote(syncedNote))
    assertEquals(setOf(syncedNote.id), fakeSqlite.getPendingDeletedIds())
    assertEquals(1, repository.loadSnapshot().pendingSyncCount)

    val sync2 = repository.syncSqliteToSharedPreferences()
    assertEquals(SyncResult.Success(inserted = 0, updated = 0, deleted = 1), sync2)
    assertTrue(fakeSqlite.getPendingDeletedIds().isEmpty())

    val finalSnapshot = repository.loadSnapshot()
    assertEquals(0, finalSnapshot.sqliteCount)
    assertEquals(1, finalSnapshot.sharedPrefsCount)
    assertEquals("Local Prefs", finalSnapshot.sharedPrefsNotes.single().title)
  }

  @Test
  fun `deleting pending-removal mirror from sharedprefs clears orphan tombstone and blocks origin hijack`() =
    runTest {
      assertTrue(repository.createNote("Sync Note", "From SQLite", StorageMethod.SQLITE))
      assertEquals(
        SyncResult.Success(inserted = 1, updated = 0, deleted = 0),
        repository.syncSqliteToSharedPreferences(),
      )
      val syncedNote = repository.loadSnapshot().sqliteNotes.single()
      assertTrue(repository.deleteNote(syncedNote))

      val orphanedMirror = repository.loadSnapshot().sharedPrefsNotes.single()
      assertFalse(
        repository.updateNote(
          orphanedMirror.id,
          "Hijack Attempt",
          "Should fail",
          StorageMethod.SHARED_PREFERENCES,
        )
      )
      assertTrue(repository.deleteNote(orphanedMirror))
      assertTrue(fakeSqlite.getPendingDeletedIds().isEmpty())
      assertEquals(0, repository.loadSnapshot().pendingSyncCount)
    }

  @Test
  fun `stage1 failure preserves sqlite state and stage2 failure recovers idempotently`() =
    runTest {
      repository.createNote("Recoverable", "Content", StorageMethod.SQLITE)
      val noteId = repository.loadSnapshot().sqliteNotes.single().id

      // Simulate Stage 1 commit() returning false.
      fakePrefs.failNextCommit = true
      assertEquals(SyncResult.Failure, repository.syncSqliteToSharedPreferences())
      assertFalse(fakeSqlite.getAllNotes().single().isSynced)
      assertNull(fakePrefs.getNoteById(noteId))

      // Stage 1 succeeds, Stage 2 throws -> pending metadata stays in SQLite for safe retry.
      fakeSqlite.failNextFinalize = true
      assertEquals(SyncResult.Failure, repository.syncSqliteToSharedPreferences())
      assertNotNull(fakePrefs.getNoteById(noteId))
      assertFalse(fakeSqlite.getAllNotes().single().isSynced)

      // Retry converges cleanly with zero duplicates.
      val retry = repository.syncSqliteToSharedPreferences()
      assertEquals(SyncResult.Success(inserted = 0, updated = 0, deleted = 0), retry)
      assertTrue(fakeSqlite.getAllNotes().single().isSynced)
      assertEquals(1, fakePrefs.getAllNotes().size)
    }

  @Test
  fun `unexpected uuid collision rekeys independent SharedPreferences note without overwrite`() =
    runTest {
      repository.createNote("SQLite Truth", "SQLite Body", StorageMethod.SQLITE)
      val sqliteId = repository.loadSnapshot().sqliteNotes.single().id

      fakePrefs.saveNote(
        SharedPrefsNoteRecord(
          id = sqliteId,
          title = "Independent SP Note",
          content = "Must survive collision",
          createdAt = 50L,
          updatedAt = 60L,
          origin = PrefsOrigin.SHARED_PREFERENCES,
        )
      )

      val result = repository.syncSqliteToSharedPreferences()
      assertEquals(
        SyncResult.Success(inserted = 1, updated = 0, deleted = 0, collisionRekeyed = 1),
        result,
      )

      val prefsList = fakePrefs.getAllNotes()
      assertEquals(2, prefsList.size)
      val spOriginal = prefsList.single { it.origin == PrefsOrigin.SHARED_PREFERENCES }
      val sqliteMirror = prefsList.single { it.origin == PrefsOrigin.SYNCED_FROM_SQLITE }
      assertEquals("Independent SP Note", spOriginal.title)
      assertTrue(spOriginal.id != sqliteId)
      assertEquals(sqliteId, sqliteMirror.id)
      assertEquals("SQLite Truth", sqliteMirror.title)
    }

  @Test
  fun `repository rejects blank inputs on create and update`() = runTest {
    assertFalse(repository.createNote("   ", "Body", StorageMethod.SQLITE))
    assertFalse(repository.createNote("Title", "\n\t ", StorageMethod.SHARED_PREFERENCES))
    assertEquals(0, repository.loadSnapshot().allNotes.size)

    assertTrue(repository.createNote("Valid", "Body", StorageMethod.SQLITE))
    val id = repository.loadSnapshot().sqliteNotes.single().id
    assertFalse(repository.updateNote(id, "   ", "Updated", StorageMethod.SQLITE))
    assertFalse(repository.updateNote(id, "Updated", "   ", StorageMethod.SQLITE))
    assertEquals("Valid", repository.loadSnapshot().sqliteNotes.single().title)
  }

  @Test
  fun `viewmodel validates blank fields and locks storage location during edit`() = runTest {
    val viewModel = NotesViewModel(repository)
    advanceUntilIdle()

    viewModel.openCreateEditor()
    viewModel.onEditorTitleChanged("   ")
    viewModel.onEditorContentChanged("")
    viewModel.saveNote()
    advanceUntilIdle()

    val errorState = viewModel.uiState.value.editorState
    assertTrue(errorState.isVisible)
    assertEquals(R.string.error_title_empty, errorState.titleErrorRes)
    assertEquals(R.string.error_content_empty, errorState.contentErrorRes)

    viewModel.onEditorTitleChanged("Lab 5 Title")
    viewModel.onEditorContentChanged("Lab 5 Content")
    viewModel.onEditorStorageSelected(StorageMethod.SHARED_PREFERENCES)
    viewModel.saveNote()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.editorState.isVisible)
    assertEquals(1, viewModel.uiState.value.sharedPrefsCount)
    viewModel.selectTab(NoteFilterTab.SHARED_PREFERENCES)
    val saved = viewModel.uiState.value.displayedNotes.single()

    // Opening in edit mode locks original storage engine.
    viewModel.openEditEditor(saved)
    viewModel.onEditorStorageSelected(StorageMethod.SQLITE)
    assertEquals(StorageMethod.SHARED_PREFERENCES, viewModel.uiState.value.editorState.storageMethod)
  }

  @Test
  fun `viewmodel recovers from unexpected storage exceptions without stuck flags`() = runTest {
    val viewModel = NotesViewModel(repository)
    advanceUntilIdle()

    fakeSqlite.failNextInsert = true
    viewModel.openCreateEditor()
    viewModel.onEditorTitleChanged("Crash Guard")
    viewModel.onEditorContentChanged("Should recover")
    viewModel.saveNote()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.editorState.isSaving)
    assertEquals(R.string.msg_operation_failed, viewModel.feedbackEvents.first().messageRes)

    fakeSqlite.failNextRead = true
    viewModel.syncNotes()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSyncing)
    assertEquals(R.string.msg_sync_failed, viewModel.feedbackEvents.first().messageRes)
  }

  private class InMemorySqliteStore : SqliteNoteStore {
    private val notes = linkedMapOf<String, SqliteNoteRecord>()
    private val tombstones = linkedMapOf<String, Long>()
    var failNextFinalize = false
    var failNextInsert = false
    var failNextRead = false

    override fun getAllNotes(): List<SqliteNoteRecord> {
      if (failNextRead) {
        failNextRead = false
        throw IllegalStateException("Simulated SQLite read failure")
      }
      return notes.values.sortedByDescending { it.updatedAt }
    }

    override fun getPendingDeletedIds(): Set<String> = tombstones.keys.toSet()

    override fun insertNote(record: SqliteNoteRecord): Boolean {
      if (failNextInsert) {
        failNextInsert = false
        throw IllegalStateException("Simulated SQLite insert failure")
      }
      notes[record.id] = record
      tombstones.remove(record.id)
      return true
    }

    override fun updateNote(id: String, title: String, content: String, updatedAt: Long): Boolean {
      val existing = notes[id] ?: return false
      notes[id] =
        existing.copy(
          title = title,
          content = content,
          updatedAt = updatedAt,
          isSynced = false,
        )
      return true
    }

    override fun deleteNoteWithTombstone(
      noteId: String,
      hasSyncedCopyInPrefs: Boolean,
      deletedAt: Long,
    ): Boolean {
      val removed = notes.remove(noteId)
      if (removed == null && !hasSyncedCopyInPrefs) return false
      if (removed?.isSynced == true || hasSyncedCopyInPrefs) {
        tombstones[noteId] = deletedAt
      }
      return true
    }

    override fun finalizeSyncMetadata(
      syncedNoteIds: List<String>,
      processedTombstoneIds: Set<String>,
      syncTimestamp: Long,
    ) {
      if (failNextFinalize) {
        failNextFinalize = false
        throw IllegalStateException("Simulated SQLite error")
      }
      processedTombstoneIds.forEach { tombstones.remove(it) }
      syncedNoteIds.forEach { id ->
        notes[id]?.let { existing ->
          notes[id] = existing.copy(isSynced = true, lastSyncedAt = syncTimestamp)
        }
      }
    }
  }

  private class InMemorySharedPrefsStore : SharedPrefsNoteStore {
    private val store = linkedMapOf<String, SharedPrefsNoteRecord>()
    var failNextCommit = false

    override fun getAllNotes(): List<SharedPrefsNoteRecord> =
      store.values.sortedByDescending { it.updatedAt }

    override fun getNoteById(id: String): SharedPrefsNoteRecord? = store[id]

    override fun saveNote(record: SharedPrefsNoteRecord): Boolean {
      store[record.id] = record
      return true
    }

    override fun deleteNote(id: String): Boolean = store.remove(id) != null

    override fun applySyncBatch(
      removals: Set<String>,
      upserts: List<SharedPrefsNoteRecord>,
    ): Boolean {
      if (failNextCommit) {
        failNextCommit = false
        return false
      }
      removals.forEach { store.remove(it) }
      upserts.forEach { store[it.id] = it }
      return true
    }
  }
}
