package com.example.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.data.local.NotesDbHelper.Companion.COL_CONTENT
import com.example.data.local.NotesDbHelper.Companion.COL_CREATED_AT
import com.example.data.local.NotesDbHelper.Companion.COL_DELETED_AT
import com.example.data.local.NotesDbHelper.Companion.COL_DELETED_NOTE_ID
import com.example.data.local.NotesDbHelper.Companion.COL_ID
import com.example.data.local.NotesDbHelper.Companion.COL_IS_SYNCED
import com.example.data.local.NotesDbHelper.Companion.COL_LAST_SYNCED_AT
import com.example.data.local.NotesDbHelper.Companion.COL_TITLE
import com.example.data.local.NotesDbHelper.Companion.COL_UPDATED_AT
import com.example.data.local.NotesDbHelper.Companion.TABLE_DELETED_NOTES
import com.example.data.local.NotesDbHelper.Companion.TABLE_NOTES
import com.example.data.model.SqliteNoteRecord

interface SqliteNoteStore {
  fun getAllNotes(): List<SqliteNoteRecord>

  fun getPendingDeletedIds(): Set<String>

  fun insertNote(record: SqliteNoteRecord): Boolean

  fun updateNote(id: String, title: String, content: String, updatedAt: Long): Boolean

  fun deleteNoteWithTombstone(
    noteId: String,
    hasSyncedCopyInPrefs: Boolean,
    deletedAt: Long,
  ): Boolean

  fun finalizeSyncMetadata(
    syncedNoteIds: List<String>,
    processedTombstoneIds: Set<String>,
    syncTimestamp: Long,
  )
}

class SQLiteDataSource(private val dbHelper: NotesDbHelper) : SqliteNoteStore {

  override fun getAllNotes(): List<SqliteNoteRecord> {
    val db = dbHelper.readableDatabase
    val results = mutableListOf<SqliteNoteRecord>()
    val columns =
      arrayOf(
        COL_ID,
        COL_TITLE,
        COL_CONTENT,
        COL_CREATED_AT,
        COL_UPDATED_AT,
        COL_IS_SYNCED,
        COL_LAST_SYNCED_AT,
      )
    db.query(TABLE_NOTES, columns, null, null, null, null, "$COL_UPDATED_AT DESC").use { cursor ->
      val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
      val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
      val contentIdx = cursor.getColumnIndexOrThrow(COL_CONTENT)
      val createdIdx = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
      val updatedIdx = cursor.getColumnIndexOrThrow(COL_UPDATED_AT)
      val syncedIdx = cursor.getColumnIndexOrThrow(COL_IS_SYNCED)
      val lastSyncedIdx = cursor.getColumnIndexOrThrow(COL_LAST_SYNCED_AT)

      while (cursor.moveToNext()) {
        results.add(
          SqliteNoteRecord(
            id = cursor.getString(idIdx),
            title = cursor.getString(titleIdx),
            content = cursor.getString(contentIdx),
            createdAt = cursor.getLong(createdIdx),
            updatedAt = cursor.getLong(updatedIdx),
            isSynced = cursor.getInt(syncedIdx) == 1,
            lastSyncedAt = if (cursor.isNull(lastSyncedIdx)) null else cursor.getLong(lastSyncedIdx),
          )
        )
      }
    }
    return results
  }

  override fun getPendingDeletedIds(): Set<String> {
    val db = dbHelper.readableDatabase
    val ids = linkedSetOf<String>()
    db.query(
        TABLE_DELETED_NOTES,
        arrayOf(COL_DELETED_NOTE_ID),
        null,
        null,
        null,
        null,
        null,
      )
      .use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(COL_DELETED_NOTE_ID)
        while (cursor.moveToNext()) {
          ids.add(cursor.getString(idIdx))
        }
      }
    return ids
  }

  override fun insertNote(record: SqliteNoteRecord): Boolean {
    return try {
      val db = dbHelper.writableDatabase
      db.beginTransaction()
      try {
        val values =
          ContentValues().apply {
            put(COL_ID, record.id)
            put(COL_TITLE, record.title)
            put(COL_CONTENT, record.content)
            put(COL_CREATED_AT, record.createdAt)
            put(COL_UPDATED_AT, record.updatedAt)
            put(COL_IS_SYNCED, if (record.isSynced) 1 else 0)
            if (record.lastSyncedAt != null) {
              put(COL_LAST_SYNCED_AT, record.lastSyncedAt)
            } else {
              putNull(COL_LAST_SYNCED_AT)
            }
          }
        val rowId =
          db.insertWithOnConflict(TABLE_NOTES, null, values, SQLiteDatabase.CONFLICT_ABORT)
        if (rowId == -1L) return false
        db.delete(TABLE_DELETED_NOTES, "$COL_DELETED_NOTE_ID = ?", arrayOf(record.id))
        db.setTransactionSuccessful()
        true
      } finally {
        db.endTransaction()
      }
    } catch (_: SQLiteException) {
      false
    }
  }

  override fun updateNote(id: String, title: String, content: String, updatedAt: Long): Boolean {
    return try {
      val db = dbHelper.writableDatabase
      val values =
        ContentValues().apply {
          put(COL_TITLE, title)
          put(COL_CONTENT, content)
          put(COL_UPDATED_AT, updatedAt)
          put(COL_IS_SYNCED, 0)
        }
      val updatedRows = db.update(TABLE_NOTES, values, "$COL_ID = ?", arrayOf(id))
      updatedRows > 0
    } catch (_: SQLiteException) {
      false
    }
  }

  override fun deleteNoteWithTombstone(
    noteId: String,
    hasSyncedCopyInPrefs: Boolean,
    deletedAt: Long,
  ): Boolean {
    return try {
      val db = dbHelper.writableDatabase
      db.beginTransaction()
      try {
        var wasPreviouslySynced = false
        db.query(
            TABLE_NOTES,
            arrayOf(COL_IS_SYNCED),
            "$COL_ID = ?",
            arrayOf(noteId),
            null,
            null,
            null,
          )
          .use { cursor ->
            if (cursor.moveToFirst()) {
              wasPreviouslySynced =
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SYNCED)) == 1
            }
          }

        val deletedRows = db.delete(TABLE_NOTES, "$COL_ID = ?", arrayOf(noteId))
        if (deletedRows == 0 && !hasSyncedCopyInPrefs) {
          return false
        }

        if (wasPreviouslySynced || hasSyncedCopyInPrefs) {
          val tombstone =
            ContentValues().apply {
              put(COL_DELETED_NOTE_ID, noteId)
              put(COL_DELETED_AT, deletedAt)
            }
          db.insertWithOnConflict(
            TABLE_DELETED_NOTES,
            null,
            tombstone,
            SQLiteDatabase.CONFLICT_REPLACE,
          )
        }

        db.setTransactionSuccessful()
        true
      } finally {
        db.endTransaction()
      }
    } catch (_: SQLiteException) {
      false
    }
  }

  override fun finalizeSyncMetadata(
    syncedNoteIds: List<String>,
    processedTombstoneIds: Set<String>,
    syncTimestamp: Long,
  ) {
    if (syncedNoteIds.isEmpty() && processedTombstoneIds.isEmpty()) return

    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      for (tombstoneId in processedTombstoneIds) {
        db.delete(TABLE_DELETED_NOTES, "$COL_DELETED_NOTE_ID = ?", arrayOf(tombstoneId))
      }
      for (noteId in syncedNoteIds) {
        val values =
          ContentValues().apply {
            put(COL_IS_SYNCED, 1)
            put(COL_LAST_SYNCED_AT, syncTimestamp)
          }
        db.update(TABLE_NOTES, values, "$COL_ID = ?", arrayOf(noteId))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }
}
