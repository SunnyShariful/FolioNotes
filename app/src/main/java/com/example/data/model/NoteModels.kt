package com.example.data.model

enum class StorageMethod {
  SQLITE,
  SHARED_PREFERENCES,
}

enum class PrefsOrigin {
  SHARED_PREFERENCES,
  SYNCED_FROM_SQLITE,
}

enum class NoteDisplaySource {
  SQLITE_ONLY,
  SHARED_PREFS_ONLY,
  SYNCED_IN_BOTH,
}

enum class NoteFilterTab {
  ALL,
  SQLITE,
  SHARED_PREFERENCES,
}

data class Note(
  val id: String,
  val title: String,
  val content: String,
  val createdAt: Long,
  val updatedAt: Long,
  val storageMethod: StorageMethod,
  val isSynced: Boolean,
  val displaySource: NoteDisplaySource,
)

data class SqliteNoteRecord(
  val id: String,
  val title: String,
  val content: String,
  val createdAt: Long,
  val updatedAt: Long,
  val isSynced: Boolean,
  val lastSyncedAt: Long?,
)

data class SharedPrefsNoteRecord(
  val id: String,
  val title: String,
  val content: String,
  val createdAt: Long,
  val updatedAt: Long,
  val origin: PrefsOrigin,
)

data class NotesSnapshot(
  val allNotes: List<Note>,
  val sqliteNotes: List<Note>,
  val sharedPrefsNotes: List<Note>,
  val sqliteCount: Int,
  val sharedPrefsCount: Int,
  val pendingSyncCount: Int,
)

sealed interface SyncResult {
  data class Success(
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
    val collisionRekeyed: Int = 0,
  ) : SyncResult {
    val totalChanges: Int
      get() = inserted + updated + deleted
  }

  data object Failure : SyncResult
}
