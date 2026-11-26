package com.example.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R

@Composable
fun NotesScreen(
  viewModel: NotesViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  LaunchedEffect(viewModel, context) {
    viewModel.feedbackEvents.collect { event ->
      val message =
        if (event.formatArgs.isEmpty()) {
          context.getString(event.messageRes)
        } else {
          context.getString(event.messageRes, *event.formatArgs.toTypedArray())
        }
      snackbarHostState.showSnackbar(message)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = viewModel::openCreateEditor,
        icon = {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.cd_add_note),
          )
        },
        text = { Text(text = stringResource(R.string.action_new_note)) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_add_note"),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 16.dp),
      ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = stringResource(R.string.app_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          FilledTonalButton(
            onClick = viewModel::syncNotes,
            enabled = !uiState.isSyncing,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_button"),
          ) {
            if (uiState.isSyncing) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = stringResource(R.string.cd_sync_notes),
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.action_sync))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SyncStatusBanner(
          pendingSyncCount = uiState.pendingSyncCount,
          isSyncing = uiState.isSyncing,
        )

        Spacer(modifier = Modifier.height(14.dp))

        StorageFilterTabs(
          selectedTab = uiState.selectedTab,
          allCount = uiState.allCount,
          sqliteCount = uiState.sqliteCount,
          sharedPrefsCount = uiState.sharedPrefsCount,
          onSelectTab = viewModel::selectTab,
        )

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
          visible = !uiState.isLoading && uiState.displayedNotes.isEmpty(),
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          EmptyNotesView(selectedTab = uiState.selectedTab)
        }

        AnimatedVisibility(
          visible = uiState.displayedNotes.isNotEmpty(),
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.weight(1f),
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("notes_list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(
              items = uiState.displayedNotes,
              key = { note -> "${note.storageMethod.name}_${note.id}" },
            ) { note ->
              NoteCard(
                note = note,
                onClick = { viewModel.openEditEditor(note) },
                onDeleteClick = { viewModel.requestDeleteNote(note) },
              )
            }
          }
        }
      }
    }
  }

  if (uiState.editorState.isVisible) {
    NoteEditorSheet(
      editorState = uiState.editorState,
      onTitleChange = viewModel::onEditorTitleChanged,
      onContentChange = viewModel::onEditorContentChanged,
      onStorageSelect = viewModel::onEditorStorageSelected,
      onSave = viewModel::saveNote,
      onDismiss = viewModel::dismissEditor,
    )
  }

  uiState.notePendingDelete?.let { pendingNote ->
    DeleteNoteDialog(
      note = pendingNote,
      onConfirm = viewModel::confirmDeleteNote,
      onDismiss = viewModel::dismissDeleteDialog,
    )
  }
}
