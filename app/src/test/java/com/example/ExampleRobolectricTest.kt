package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotesDbHelper
import com.example.data.local.SQLiteDataSource
import com.example.data.local.SharedPrefsDataSource
import com.example.data.model.NoteDisplaySource
import com.example.data.model.PrefsOrigin
import com.example.data.model.SharedPrefsNoteRecord
import com.example.data.model.SqliteNoteRecord
import com.example.data.model.StorageMethod
import com.example.data.model.SyncResult
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var context: Context
  private lateinit var dbHelper: NotesDbHelper
  private lateinit var sqliteDataSource: SQLiteDataSource
  private lateinit var sharedPrefsDataSource: SharedPrefsDataSource

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(NotesDbHelper.DATABASE_NAME)
    context
      .getSharedPreferences(SharedPrefsDataSource.PREFS_FILE_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    dbHelper = NotesDbHelper(context)
    sqliteDataSource = SQLiteDataSource(dbHelper)
    sharedPrefsDataSource = SharedPrefsDataSource(context)
  }

  @After
  fun tearDown() {
    dbHelper.close()
  }

  @Test
  fun `read string from context`() {
    val appName = context.getString(R.string.app_name)
    assertEquals("FolioNotes", appName)
  }

  @Test
  fun `real SQLiteDataSource handles CRUD, duplicate PK safely, and resets is_synced on update`() {
    val inserted =
      sqliteDataSource.insertNote(
        SqliteNoteRecord(
          id = "note-1",
          title = "Initial Title",
          content = "Initial Content",
          createdAt = 100L,
          updatedAt = 100L,
          isSynced = false,
          lastSyncedAt = null,
        )
      )
    assertTrue(inserted)

    // Duplicate PK should return false gracefully via SQLiteException handling rather than crashing.
    assertFalse(
      sqliteDataSource.insertNote(
        SqliteNoteRecord(
          id = "note-1",
          title = "Duplicate",
          content = "Duplicate",
          createdAt = 101L,
          updatedAt = 101L,
          isSynced = false,
          lastSyncedAt = null,
        )
      )
    )

    sqliteDataSource.finalizeSyncMetadata(
      syncedNoteIds = listOf("note-1"),
      processedTombstoneIds = emptySet(),
      syncTimestamp = 200L,
    )
    assertTrue(sqliteDataSource.getAllNotes().single().isSynced)

    assertTrue(sqliteDataSource.updateNote("note-1", "Edited Title", "Edited Content", 300L))
    val afterUpdate = sqliteDataSource.getAllNotes().single()
    assertEquals("Edited Title", afterUpdate.title)
    assertEquals("Edited Content", afterUpdate.content)
    assertFalse(afterUpdate.isSynced)
  }

  @Test
  fun `real SharedPrefsDataSource uses map key as authoritative id and ignores corrupt json`() {
    val prefs =
      context.getSharedPreferences(SharedPrefsDataSource.PREFS_FILE_NAME, Context.MODE_PRIVATE)
    prefs
      .edit()
      .putString("corrupt", "{not-json")
      .putString("null_title", """{"id":"null_title","title":null,"content":"Body","createdAt":1,"updatedAt":2}""")
      .putString(
        "key-actual",
        """{"id":"mismatched-id","title":"Valid Title","content":"Valid Content","createdAt":10,"updatedAt":20,"origin":"SHARED_PREFERENCES"}""",
      )
      .commit()

    val notes = sharedPrefsDataSource.getAllNotes()
    assertEquals(1, notes.size)
    assertEquals("key-actual", notes.single().id)
    assertEquals("Valid Title", notes.single().title)
    assertNotNull(sharedPrefsDataSource.getNoteById("key-actual"))
    assertNull(sharedPrefsDataSource.getNoteById("mismatched-id"))
  }

  @Test
  fun `end to end repository sync, tombstone deletion, and SP origin protection on real stores`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      var now = 1_000L
      val repository =
        NotesRepository(
          sqliteStore = sqliteDataSource,
          sharedPrefsStore = sharedPrefsDataSource,
          ioDispatcher = testDispatcher,
          clock = { ++now },
        )

      assertTrue(repository.createNote("SQLite Alpha", "Alpha body", StorageMethod.SQLITE))
      assertTrue(repository.createNote("Prefs Beta", "Beta body", StorageMethod.SHARED_PREFERENCES))

      val syncResult = repository.syncSqliteToSharedPreferences()
      assertEquals(SyncResult.Success(inserted = 1, updated = 0, deleted = 0), syncResult)

      // Recreate stores to verify real disk persistence across restarts.
      dbHelper.close()
      val freshDbHelper = NotesDbHelper(context)
      val freshRepo =
        NotesRepository(
          sqliteStore = SQLiteDataSource(freshDbHelper),
          sharedPrefsStore = SharedPrefsDataSource(context),
          ioDispatcher = testDispatcher,
          clock = { ++now },
        )

      val snapshot = freshRepo.loadSnapshot()
      assertEquals(1, snapshot.sqliteCount)
      assertEquals(2, snapshot.sharedPrefsCount)
      assertEquals(0, snapshot.pendingSyncCount)

      val syncedSqlite = snapshot.sqliteNotes.single()
      assertEquals(NoteDisplaySource.SYNCED_IN_BOTH, syncedSqlite.displaySource)

      // Edit synced note -> pending change tracked across restart.
      assertTrue(freshRepo.updateNote(syncedSqlite.id, "SQLite Alpha v2", "Body v2", StorageMethod.SQLITE))
      val dirty = freshRepo.loadSnapshot()
      assertEquals(1, dirty.pendingSyncCount)
      assertEquals(NoteDisplaySource.SQLITE_ONLY, dirty.sqliteNotes.single().displaySource)
      assertEquals(
        "SQLite Alpha",
        dirty.sharedPrefsNotes.first { it.id == syncedSqlite.id }.title,
      )

      assertEquals(
        SyncResult.Success(inserted = 0, updated = 1, deleted = 0),
        freshRepo.syncSqliteToSharedPreferences(),
      )

      // Delete synced SQLite note -> tombstone removes mirror on next sync, SP-only survives.
      val updatedSynced = freshRepo.loadSnapshot().sqliteNotes.single()
      assertTrue(freshRepo.deleteNote(updatedSynced))
      assertEquals(
        SyncResult.Success(inserted = 0, updated = 0, deleted = 1),
        freshRepo.syncSqliteToSharedPreferences(),
      )

      val afterDelete = freshRepo.loadSnapshot()
      assertEquals(0, afterDelete.sqliteCount)
      assertEquals(1, afterDelete.sharedPrefsCount)
      assertEquals("Prefs Beta", afterDelete.sharedPrefsNotes.single().title)
      assertEquals(
        PrefsOrigin.SHARED_PREFERENCES,
        SharedPrefsDataSource(context).getAllNotes().single().origin,
      )
      freshDbHelper.close()
    }
}
