package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StorageColors(
  val sqliteContainer: Color,
  val onSqliteContainer: Color,
  val prefsContainer: Color,
  val onPrefsContainer: Color,
  val syncedContainer: Color,
  val onSyncedContainer: Color,
  val pendingContainer: Color,
  val onPendingContainer: Color,
)

private val LightStorageColors =
  StorageColors(
    sqliteContainer = SqliteBadgeBgLight,
    onSqliteContainer = SqliteBadgeFgLight,
    prefsContainer = PrefsBadgeBgLight,
    onPrefsContainer = PrefsBadgeFgLight,
    syncedContainer = SyncedBadgeBgLight,
    onSyncedContainer = SyncedBadgeFgLight,
    pendingContainer = PendingBannerBgLight,
    onPendingContainer = PendingBannerFgLight,
  )

private val DarkStorageColors =
  StorageColors(
    sqliteContainer = SqliteBadgeBgDark,
    onSqliteContainer = SqliteBadgeFgDark,
    prefsContainer = PrefsBadgeBgDark,
    onPrefsContainer = PrefsBadgeFgDark,
    syncedContainer = SyncedBadgeBgDark,
    onSyncedContainer = SyncedBadgeFgDark,
    pendingContainer = PendingBannerBgDark,
    onPendingContainer = PendingBannerFgDark,
  )

val LocalStorageColors = staticCompositionLocalOf { LightStorageColors }

private val LightColorScheme =
  lightColorScheme(
    primary = SlateInkPrimary,
    onPrimary = Color.White,
    primaryContainer = WarmSurfaceVariant,
    onPrimaryContainer = SlateInkPrimary,
    secondary = PrefsBadgeFgLight,
    onSecondary = Color.White,
    secondaryContainer = PrefsBadgeBgLight,
    onSecondaryContainer = PrefsBadgeFgLight,
    tertiary = SqliteBadgeFgLight,
    tertiaryContainer = SqliteBadgeBgLight,
    onTertiaryContainer = SqliteBadgeFgLight,
    background = ParchmentBackground,
    onBackground = SlateInkPrimary,
    surface = CrispPaperSurface,
    onSurface = SlateInkPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = SlateInkSecondary,
    outline = WarmOutline,
    outlineVariant = WarmOutline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = CrimsonError,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = CreamInkPrimary,
    onPrimary = DarkInkBackground,
    primaryContainer = DarkSlateVariant,
    onPrimaryContainer = CreamInkPrimary,
    secondary = PrefsBadgeFgDark,
    onSecondary = DarkInkBackground,
    secondaryContainer = PrefsBadgeBgDark,
    onSecondaryContainer = PrefsBadgeFgDark,
    tertiary = SqliteBadgeFgDark,
    tertiaryContainer = SqliteBadgeBgDark,
    onTertiaryContainer = SqliteBadgeFgDark,
    background = DarkInkBackground,
    onBackground = CreamInkPrimary,
    surface = DarkSlateSurface,
    onSurface = CreamInkPrimary,
    surfaceVariant = DarkSlateVariant,
    onSurfaceVariant = CreamInkSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkErrorContainer,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkError,
  )

@Composable
fun FolioNotesTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val storageColors = if (darkTheme) DarkStorageColors else LightStorageColors

  CompositionLocalProvider(LocalStorageColors provides storageColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
    )
  }
}
