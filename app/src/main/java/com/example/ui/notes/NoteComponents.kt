package com.example.ui.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Note
import com.example.data.model.NoteDisplaySource
import com.example.data.model.NoteFilterTab
import com.example.ui.theme.LocalStorageColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageBadge(
  displaySource: NoteDisplaySource,
  modifier: Modifier = Modifier,
) {
  val storageColors = LocalStorageColors.current
  val (labelRes, bgColor, fgColor, icon) =
    when (displaySource) {
      NoteDisplaySource.SQLITE_ONLY ->
        Quad(
          R.string.badge_sqlite,
          storageColors.sqliteContainer,
          storageColors.onSqliteContainer,
          Icons.Outlined.Storage,
        )
      NoteDisplaySource.SHARED_PREFS_ONLY ->
        Quad(
          R.string.badge_shared_prefs,
          storageColors.prefsContainer,
          storageColors.onPrefsContainer,
          Icons.Outlined.Settings,
        )
      NoteDisplaySource.SYNCED_IN_BOTH ->
        Quad(
          R.string.badge_synced,
          storageColors.syncedContainer,
          storageColors.onSyncedContainer,
          Icons.Outlined.CloudDone,
        )
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = bgColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fgColor,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = fgColor,
      )
    }
  }
}

@Composable
fun StorageFilterTabs(
  selectedTab: NoteFilterTab,
  allCount: Int,
  sqliteCount: Int,
  sharedPrefsCount: Int,
  onSelectTab: (NoteFilterTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs =
    listOf(
      Triple(NoteFilterTab.ALL, R.string.tab_all, allCount) to "tab_all",
      Triple(NoteFilterTab.SQLITE, R.string.tab_sqlite, sqliteCount) to "tab_sqlite",
      Triple(NoteFilterTab.SHARED_PREFERENCES, R.string.tab_shared_prefs, sharedPrefsCount) to
        "tab_shared_prefs",
    )

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      tabs.forEach { (tabInfo, tag) ->
        val (tab, labelRes, count) = tabInfo
        val isSelected = selectedTab == tab
        val containerColor by
          animateColorAsState(
            targetValue =
              if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            label = "tab_bg",
          )
        val textColor by
          animateColorAsState(
            targetValue =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "tab_fg",
          )

        Box(
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(10.dp))
              .background(containerColor)
              .clickable { onSelectTab(tab) }
              .testTag(tag)
              .padding(horizontal = 8.dp, vertical = 10.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.tab_count_format, stringResource(labelRes), count),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun SyncStatusBanner(
  pendingSyncCount: Int,
  isSyncing: Boolean,
  modifier: Modifier = Modifier,
) {
  val storageColors = LocalStorageColors.current
  val hasPending = pendingSyncCount > 0
  val bgColor =
    if (hasPending) storageColors.pendingContainer else MaterialTheme.colorScheme.surfaceVariant
  val fgColor =
    if (hasPending) storageColors.onPendingContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (hasPending) Icons.Outlined.Sync else Icons.Outlined.CloudDone,
        contentDescription = null,
        tint = fgColor,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text =
          when {
            isSyncing -> stringResource(R.string.sync_in_progress)
            hasPending -> stringResource(R.string.sync_status_pending_format, pendingSyncCount)
            else -> stringResource(R.string.sync_status_all_synced)
          },
        style = MaterialTheme.typography.labelMedium,
        color = fgColor,
      )
    }
  }
}

@Composable
fun NoteCard(
  note: Note,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val formattedDate = remember(note.updatedAt) { formatTimestamp(note.updatedAt) }
  val deleteCd = stringResource(R.string.cd_delete_note, note.title)

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag("note_card_${note.id}"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        StorageBadge(displaySource = note.displaySource)
        IconButton(
          onClick = onDeleteClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_note_button_${note.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = deleteCd,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = note.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 8.dp),
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = note.content,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 8.dp),
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(R.string.note_updated_format, formattedDate),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun EmptyNotesView(
  selectedTab: NoteFilterTab,
  modifier: Modifier = Modifier,
) {
  val (titleRes, subtitleRes) =
    when (selectedTab) {
      NoteFilterTab.ALL -> R.string.empty_all_title to R.string.empty_all_subtitle
      NoteFilterTab.SQLITE -> R.string.empty_sqlite_title to R.string.empty_sqlite_subtitle
      NoteFilterTab.SHARED_PREFERENCES ->
        R.string.empty_prefs_title to R.string.empty_prefs_subtitle
    }

  Surface(
    modifier = modifier.fillMaxWidth().padding(top = 24.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Description,
          contentDescription = stringResource(R.string.cd_empty_notebook),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(30.dp),
        )
      }
      Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(subtitleRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
fun DeleteNoteDialog(
  note: Note,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.dialog_delete_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.dialog_delete_message, note.title),
          style = MaterialTheme.typography.bodyMedium,
        )
        if (note.displaySource == NoteDisplaySource.SYNCED_IN_BOTH) {
          Text(
            text = stringResource(R.string.dialog_delete_synced_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier = Modifier.testTag("confirm_delete_button"),
      ) {
        Text(text = stringResource(R.string.action_delete))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("cancel_delete_button"),
      ) {
        Text(text = stringResource(R.string.action_cancel))
      }
    },
  )
}

private fun formatTimestamp(epochMillis: Long): String {
  val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
  return formatter.format(Date(epochMillis))
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
