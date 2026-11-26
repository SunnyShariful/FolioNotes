package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.PrefsOrigin
import com.example.data.model.SharedPrefsNoteRecord
import org.json.JSONException
import org.json.JSONObject

interface SharedPrefsNoteStore {
  fun getAllNotes(): List<SharedPrefsNoteRecord>

  fun getNoteById(id: String): SharedPrefsNoteRecord?

  fun saveNote(record: SharedPrefsNoteRecord): Boolean

  fun deleteNote(id: String): Boolean

  fun applySyncBatch(removals: Set<String>, upserts: List<SharedPrefsNoteRecord>): Boolean
}

class SharedPrefsDataSource(context: Context) : SharedPrefsNoteStore {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

  override fun getAllNotes(): List<SharedPrefsNoteRecord> {
    val entries = prefs.all
    val notes = mutableListOf<SharedPrefsNoteRecord>()
    for ((key, value) in entries) {
      val rawJson = value as? String ?: continue
      val parsed = parseRecord(key, rawJson)
      if (parsed != null) {
        notes.add(parsed)
      }
    }
    return notes.sortedByDescending { it.updatedAt }
  }

  override fun getNoteById(id: String): SharedPrefsNoteRecord? {
    val rawJson = prefs.getString(id, null) ?: return null
    return parseRecord(id, rawJson)
  }

  override fun saveNote(record: SharedPrefsNoteRecord): Boolean {
    return prefs.edit().putString(record.id, encodeRecord(record)).commit()
  }

  override fun deleteNote(id: String): Boolean {
    return prefs.edit().remove(id).commit()
  }

  override fun applySyncBatch(
    removals: Set<String>,
    upserts: List<SharedPrefsNoteRecord>,
  ): Boolean {
    val editor = prefs.edit()
    for (id in removals) {
      editor.remove(id)
    }
    for (record in upserts) {
      editor.putString(record.id, encodeRecord(record))
    }
    return editor.commit()
  }

  private fun encodeRecord(record: SharedPrefsNoteRecord): String {
    return JSONObject()
      .apply {
        put(KEY_ID, record.id)
        put(KEY_TITLE, record.title)
        put(KEY_CONTENT, record.content)
        put(KEY_CREATED_AT, record.createdAt)
        put(KEY_UPDATED_AT, record.updatedAt)
        put(KEY_ORIGIN, record.origin.name)
      }
      .toString()
  }

  private fun parseRecord(storageKey: String, rawJson: String): SharedPrefsNoteRecord? {
    if (storageKey.isBlank()) return null
    return try {
      val obj = JSONObject(rawJson)
      val title = obj.opt(KEY_TITLE) as? String ?: return null
      val content = obj.opt(KEY_CONTENT) as? String ?: return null
      if (title.isBlank() || content.isBlank()) return null
      if (obj.isNull(KEY_CREATED_AT) || obj.isNull(KEY_UPDATED_AT)) return null
      val createdAt = obj.getLong(KEY_CREATED_AT)
      val updatedAt = obj.getLong(KEY_UPDATED_AT)
      val originRaw = obj.opt(KEY_ORIGIN) as? String ?: PrefsOrigin.SHARED_PREFERENCES.name
      val origin =
        PrefsOrigin.entries.firstOrNull { it.name == originRaw } ?: PrefsOrigin.SHARED_PREFERENCES
      SharedPrefsNoteRecord(
        id = storageKey,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        origin = origin,
      )
    } catch (_: JSONException) {
      null
    }
  }

  companion object {
    const val PREFS_FILE_NAME = "notes_prefs"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_CONTENT = "content"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_ORIGIN = "origin"
  }
}
