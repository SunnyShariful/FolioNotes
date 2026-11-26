package com.example.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StorageMethod
import com.example.ui.theme.LocalStorageColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
  editorState: NoteEditorState,
  onTitleChange: (String) -> Unit,
  onContentChange: (String) -> Unit,
  onStorageSelect: (StorageMethod) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .imePadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text =
            stringResource(
              if (editorState.isEditMode) R.string.editor_title_edit
              else R.string.editor_title_create
            ),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("close_editor_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.cd_close_editor),
          )
        }
      }

      OutlinedTextField(
        value = editorState.title,
        onValueChange = onTitleChange,
        label = { Text(text = stringResource(R.string.label_note_title)) },
        placeholder = { Text(text = stringResource(R.string.hint_note_title)) },
        singleLine = true,
        isError = editorState.titleErrorRes != null,
        supportingText = {
          AnimatedVisibility(visible = editorState.titleErrorRes != null) {
            editorState.titleErrorRes?.let { errorRes ->
              Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("title_error_text"),
              )
            }
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("title_input"),
      )

      OutlinedTextField(
        value = editorState.content,
        onValueChange = onContentChange,
        label = { Text(text = stringResource(R.string.label_note_content)) },
        placeholder = { Text(text = stringResource(R.string.hint_note_content)) },
        minLines = 4,
        maxLines = 8,
        isError = editorState.contentErrorRes != null,
        supportingText = {
          AnimatedVisibility(visible = editorState.contentErrorRes != null) {
            editorState.contentErrorRes?.let { errorRes ->
              Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("content_error_text"),
              )
            }
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("content_input"),
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.label_storage_method),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )

        if (editorState.isEditMode) {
          LockedStorageIndicator(storageMethod = editorState.storageMethod)
          Text(
            text = stringResource(R.string.helper_storage_locked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          StorageMethodSelector(
            selectedMethod = editorState.storageMethod,
            onSelect = onStorageSelect,
          )
          Text(
            text = stringResource(R.string.helper_storage_create),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cancel_editor_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.action_cancel))
        }
        Button(
          onClick = onSave,
          enabled = !editorState.isSaving,
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_note_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text =
              stringResource(
                if (editorState.isEditMode) R.string.action_update else R.string.action_save
              )
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun StorageMethodSelector(
  selectedMethod: StorageMethod,
  onSelect: (StorageMethod) -> Unit,
) {
  val storageColors = LocalStorageColors.current

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    StorageOptionCard(
      label = stringResource(R.string.storage_sqlite),
      icon = Icons.Outlined.Storage,
      selected = selectedMethod == StorageMethod.SQLITE,
      selectedBg = storageColors.sqliteContainer,
      selectedFg = storageColors.onSqliteContainer,
      onClick = { onSelect(StorageMethod.SQLITE) },
      modifier = Modifier.weight(1f).testTag("storage_sqlite_chip"),
    )
    StorageOptionCard(
      label = stringResource(R.string.storage_shared_prefs),
      icon = Icons.Outlined.Settings,
      selected = selectedMethod == StorageMethod.SHARED_PREFERENCES,
      selectedBg = storageColors.prefsContainer,
      selectedFg = storageColors.onPrefsContainer,
      onClick = { onSelect(StorageMethod.SHARED_PREFERENCES) },
      modifier = Modifier.weight(1f).testTag("storage_prefs_chip"),
    )
  }
}

@Composable
private fun StorageOptionCard(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  selectedBg: androidx.compose.ui.graphics.Color,
  selectedFg: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bg = if (selected) selectedBg else MaterialTheme.colorScheme.surfaceVariant
  val fg = if (selected) selectedFg else MaterialTheme.colorScheme.onSurfaceVariant
  val border = if (selected) selectedFg else MaterialTheme.colorScheme.outline

  Surface(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = bg,
    border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier.weight(1f),
      )
      if (selected) {
        Icon(
          imageVector = Icons.Outlined.CheckCircle,
          contentDescription = null,
          tint = fg,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun LockedStorageIndicator(storageMethod: StorageMethod) {
  val storageColors = LocalStorageColors.current
  val (labelRes, bg, fg) =
    when (storageMethod) {
      StorageMethod.SQLITE ->
        Triple(
          R.string.storage_sqlite,
          storageColors.sqliteContainer,
          storageColors.onSqliteContainer,
        )
      StorageMethod.SHARED_PREFERENCES ->
        Triple(
          R.string.storage_shared_prefs,
          storageColors.prefsContainer,
          storageColors.onPrefsContainer,
        )
    }

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("locked_storage_badge"),
    shape = RoundedCornerShape(12.dp),
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Lock,
        contentDescription = stringResource(R.string.cd_storage_locked_icon),
        tint = fg,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = fg,
      )
    }
  }
}
