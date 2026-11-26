package com.example.ui.notes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.Note
import com.example.data.model.NoteFilterTab
import com.example.data.model.NotesSnapshot
import com.example.data.model.StorageMethod
import com.example.data.model.SyncResult
import com.example.data.repository.NotesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorState(
  val isVisible: Boolean = false,
  val editingNoteId: String? = null,
  val title: String = "",
  val content: String = "",
  val storageMethod: StorageMethod = StorageMethod.SQLITE,
  @param:StringRes val titleErrorRes: Int? = null,
  @param:StringRes val contentErrorRes: Int? = null,
  val isSaving: Boolean = false,
) {
  val isEditMode: Boolean
    get() = editingNoteId != null
}

data class UiFeedbackEvent(
  @param:StringRes val messageRes: Int,
  val formatArgs: List<Any> = emptyList(),
)

data class NotesUiState(
  val isLoading: Boolean = true,
  val selectedTab: NoteFilterTab = NoteFilterTab.ALL,
  val allNotes: List<Note> = emptyList(),
  val sqliteNotes: List<Note> = emptyList(),
  val sharedPrefsNotes: List<Note> = emptyList(),
  val sqliteCount: Int = 0,
  val sharedPrefsCount: Int = 0,
  val pendingSyncCount: Int = 0,
  val isSyncing: Boolean = false,
  val editorState: NoteEditorState = NoteEditorState(),
  val notePendingDelete: Note? = null,
) {
  val allCount: Int
    get() = allNotes.size

  val displayedNotes: List<Note>
    get() =
      when (selectedTab) {
        NoteFilterTab.ALL -> allNotes
        NoteFilterTab.SQLITE -> sqliteNotes
        NoteFilterTab.SHARED_PREFERENCES -> sharedPrefsNotes
      }
}

class NotesViewModel(private val repository: NotesRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(NotesUiState())
  val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

  private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
  val feedbackEvents = _feedbackEvents.receiveAsFlow()

  init {
    refreshNotes()
  }

  fun refreshNotes() {
    viewModelScope.launch {
      try {
        val snapshot = repository.loadSnapshot()
        applySnapshot(snapshot)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false) }
        _feedbackEvents.send(UiFeedbackEvent(R.string.msg_operation_failed))
      }
    }
  }

  fun selectTab(tab: NoteFilterTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  fun openCreateEditor() {
    val defaultStorage =
      when (_uiState.value.selectedTab) {
        NoteFilterTab.SHARED_PREFERENCES -> StorageMethod.SHARED_PREFERENCES
        NoteFilterTab.ALL,
        NoteFilterTab.SQLITE -> StorageMethod.SQLITE
      }
    _uiState.update {
      it.copy(
        editorState =
          NoteEditorState(
            isVisible = true,
            editingNoteId = null,
            title = "",
            content = "",
            storageMethod = defaultStorage,
          )
      )
    }
  }

  fun openEditEditor(note: Note) {
    val authoritativeNote =
      if (note.storageMethod == StorageMethod.SQLITE) {
        _uiState.value.sqliteNotes.firstOrNull { it.id == note.id } ?: note
      } else {
        note
      }
    _uiState.update {
      it.copy(
        editorState =
          NoteEditorState(
            isVisible = true,
            editingNoteId = authoritativeNote.id,
            title = authoritativeNote.title,
            content = authoritativeNote.content,
            storageMethod = authoritativeNote.storageMethod,
          )
      )
    }
  }

  fun dismissEditor() {
    _uiState.update { it.copy(editorState = NoteEditorState()) }
  }

  fun onEditorTitleChanged(value: String) {
    _uiState.update { state ->
      state.copy(
        editorState =
          state.editorState.copy(
            title = value,
            titleErrorRes = if (value.isNotBlank()) null else state.editorState.titleErrorRes,
          )
      )
    }
  }

  fun onEditorContentChanged(value: String) {
    _uiState.update { state ->
      state.copy(
        editorState =
          state.editorState.copy(
            content = value,
            contentErrorRes = if (value.isNotBlank()) null else state.editorState.contentErrorRes,
          )
      )
    }
  }

  fun onEditorStorageSelected(method: StorageMethod) {
    _uiState.update { state ->
      if (state.editorState.isEditMode) return@update state
      state.copy(editorState = state.editorState.copy(storageMethod = method))
    }
  }

  fun saveNote() {
    val editor = _uiState.value.editorState
    if (editor.isSaving) return

    val titleError = if (editor.title.isBlank()) R.string.error_title_empty else null
    val contentError = if (editor.content.isBlank()) R.string.error_content_empty else null
    if (titleError != null || contentError != null) {
      _uiState.update {
        it.copy(
          editorState =
            editor.copy(
              titleErrorRes = titleError,
              contentErrorRes = contentError,
            )
        )
      }
      return
    }

    _uiState.update { it.copy(editorState = editor.copy(isSaving = true)) }

    viewModelScope.launch {
      try {
        val editId = editor.editingNoteId
        val success =
          if (editId == null) {
            repository.createNote(
              title = editor.title,
              content = editor.content,
              storageMethod = editor.storageMethod,
            )
          } else {
            repository.updateNote(
              id = editId,
              title = editor.title,
              content = editor.content,
              storageMethod = editor.storageMethod,
            )
          }

        val snapshot = repository.loadSnapshot()
        if (success) {
          _uiState.update {
            it.copy(
              isLoading = false,
              allNotes = snapshot.allNotes,
              sqliteNotes = snapshot.sqliteNotes,
              sharedPrefsNotes = snapshot.sharedPrefsNotes,
              sqliteCount = snapshot.sqliteCount,
              sharedPrefsCount = snapshot.sharedPrefsCount,
              pendingSyncCount = snapshot.pendingSyncCount,
              editorState = NoteEditorState(),
            )
          }
          val msgRes =
            when {
              editId != null -> R.string.msg_note_updated
              editor.storageMethod == StorageMethod.SQLITE -> R.string.msg_note_created_sqlite
              else -> R.string.msg_note_created_prefs
            }
          _feedbackEvents.send(UiFeedbackEvent(msgRes))
        } else {
          _uiState.update {
            it.copy(
              isLoading = false,
              allNotes = snapshot.allNotes,
              sqliteNotes = snapshot.sqliteNotes,
              sharedPrefsNotes = snapshot.sharedPrefsNotes,
              sqliteCount = snapshot.sqliteCount,
              sharedPrefsCount = snapshot.sharedPrefsCount,
              pendingSyncCount = snapshot.pendingSyncCount,
              editorState = it.editorState.copy(isSaving = false),
            )
          }
          _feedbackEvents.send(UiFeedbackEvent(R.string.msg_operation_failed))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            editorState = it.editorState.copy(isSaving = false),
          )
        }
        _feedbackEvents.send(UiFeedbackEvent(R.string.msg_operation_failed))
      }
    }
  }

  fun requestDeleteNote(note: Note) {
    _uiState.update { it.copy(notePendingDelete = note) }
  }

  fun dismissDeleteDialog() {
    _uiState.update { it.copy(notePendingDelete = null) }
  }

  fun confirmDeleteNote() {
    val note = _uiState.value.notePendingDelete ?: return
    _uiState.update { it.copy(notePendingDelete = null) }

    viewModelScope.launch {
      try {
        val deleted = repository.deleteNote(note)
        val snapshot = repository.loadSnapshot()
        applySnapshot(snapshot)
        _feedbackEvents.send(
          UiFeedbackEvent(if (deleted) R.string.msg_note_deleted else R.string.msg_operation_failed)
        )
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _feedbackEvents.send(UiFeedbackEvent(R.string.msg_operation_failed))
      }
    }
  }

  fun syncNotes() {
    if (_uiState.value.isSyncing) return
    _uiState.update { it.copy(isSyncing = true) }

    viewModelScope.launch {
      try {
        val result = repository.syncSqliteToSharedPreferences()
        val snapshot = repository.loadSnapshot()
        _uiState.update {
          it.copy(
            isLoading = false,
            isSyncing = false,
            allNotes = snapshot.allNotes,
            sqliteNotes = snapshot.sqliteNotes,
            sharedPrefsNotes = snapshot.sharedPrefsNotes,
            sqliteCount = snapshot.sqliteCount,
            sharedPrefsCount = snapshot.sharedPrefsCount,
            pendingSyncCount = snapshot.pendingSyncCount,
          )
        }
        when (result) {
          is SyncResult.Success -> {
            if (result.totalChanges == 0) {
              _feedbackEvents.send(UiFeedbackEvent(R.string.msg_sync_already_up_to_date))
            } else {
              _feedbackEvents.send(
                UiFeedbackEvent(
                  messageRes = R.string.msg_sync_success_format,
                  formatArgs = listOf(result.inserted, result.updated, result.deleted),
                )
              )
            }
          }
          SyncResult.Failure -> {
            _feedbackEvents.send(UiFeedbackEvent(R.string.msg_sync_failed))
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, isSyncing = false) }
        _feedbackEvents.send(UiFeedbackEvent(R.string.msg_sync_failed))
      }
    }
  }

  private fun applySnapshot(snapshot: NotesSnapshot) {
    _uiState.update {
      it.copy(
        isLoading = false,
        allNotes = snapshot.allNotes,
        sqliteNotes = snapshot.sqliteNotes,
        sharedPrefsNotes = snapshot.sharedPrefsNotes,
        sqliteCount = snapshot.sqliteCount,
        sharedPrefsCount = snapshot.sharedPrefsCount,
        pendingSyncCount = snapshot.pendingSyncCount,
      )
    }
  }
}

class NotesViewModelFactory(private val repository: NotesRepository) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
      return NotesViewModel(repository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
