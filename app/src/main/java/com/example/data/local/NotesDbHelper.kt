package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotesDbHelper(context: Context) :
  SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(SQL_CREATE_NOTES_TABLE)
    db.execSQL(SQL_CREATE_DELETED_NOTES_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Future schema migrations can be handled incrementally by version number.
  }

  companion object {
    const val DATABASE_NAME = "folionotes.db"
    const val DATABASE_VERSION = 1

    const val TABLE_NOTES = "notes"
    const val COL_ID = "id"
    const val COL_TITLE = "title"
    const val COL_CONTENT = "content"
    const val COL_CREATED_AT = "created_at"
    const val COL_UPDATED_AT = "updated_at"
    const val COL_IS_SYNCED = "is_synced"
    const val COL_LAST_SYNCED_AT = "last_synced_at"

    const val TABLE_DELETED_NOTES = "deleted_notes"
    const val COL_DELETED_NOTE_ID = "note_id"
    const val COL_DELETED_AT = "deleted_at"

    private const val SQL_CREATE_NOTES_TABLE =
      """
      CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
        $COL_ID TEXT PRIMARY KEY,
        $COL_TITLE TEXT NOT NULL,
        $COL_CONTENT TEXT NOT NULL,
        $COL_CREATED_AT INTEGER NOT NULL,
        $COL_UPDATED_AT INTEGER NOT NULL,
        $COL_IS_SYNCED INTEGER NOT NULL DEFAULT 0,
        $COL_LAST_SYNCED_AT INTEGER
      )
      """

    private const val SQL_CREATE_DELETED_NOTES_TABLE =
      """
      CREATE TABLE IF NOT EXISTS $TABLE_DELETED_NOTES (
        $COL_DELETED_NOTE_ID TEXT PRIMARY KEY,
        $COL_DELETED_AT INTEGER NOT NULL
      )
      """
  }
}
