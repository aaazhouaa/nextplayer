package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.rememberRestorableFocusState
import dev.anilbeesetti.nextplayer.core.ui.components.restorableFocusGroup
import dev.anilbeesetti.nextplayer.core.ui.components.restorableFocusItem
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusDown
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun MediaLibraryPreferencesScreen(
    viewModel: MediaLibraryPreferencesViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MediaLibraryPreferencesScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaLibraryPreferencesScreenContent(
    state: MediaLibraryPreferencesUiState,
    onAction: (MediaLibraryPreferencesUiEvent) -> Unit,
) {
    val preferences = state.preferences
    val context = LocalContext.current

    var pendingRemovalPath by rememberSaveable { mutableStateOf<String?>(null) }

    val focusState = rememberRestorableFocusState()

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
                navigationIcon = {
                    FilledTonalIconButton(onClick = { onAction(MediaLibraryPreferencesUiEvent.NavigateUp) }, modifier = Modifier.tvFocusDown(focusState.requester)) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .restorableFocusGroup(focusState)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.media_library))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.restorableFocusItem(focusState, "mark_last_played"),
                    title = stringResource(id = R.string.mark_last_played_media),
                    description = stringResource(
                        id = R.string.mark_last_played_media_desc,
                    ),
                    icon = NextIcons.Check,
                    isChecked = preferences.markLastPlayedMedia,
                    onClick = { onAction(MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.scan))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.restorableFocusItem(focusState, "show_hidden_files"),
                    title = stringResource(id = R.string.show_hidden_files),
                    description = stringResource(id = R.string.show_hidden_files_desc),
                    icon = NextIcons.HideSource,
                    isChecked = preferences.showHiddenFiles,
                    onClick = {
                        if (!preferences.showHiddenFiles && !hasAllFilesAccess(context)) {
                            launchAllFilesAccessSettings(context)
                        } else {
                            onAction(MediaLibraryPreferencesUiEvent.ToggleShowHiddenFiles)
                        }
                    },
                    isFirstItem = true,
                    isLastItem = false,
                )
                PreferenceSwitch(
                    modifier = Modifier.restorableFocusItem(focusState, "respect_no_media"),
                    title = stringResource(id = R.string.respect_no_media),
                    description = stringResource(id = R.string.respect_no_media_desc),
                    icon = NextIcons.FolderOff,
                    isChecked = preferences.respectNoMedia,
                    onClick = { onAction(MediaLibraryPreferencesUiEvent.ToggleRespectNoMedia) },
                    isFirstItem = false,
                    isLastItem = false,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.restorableFocusItem(focusState, "add_scan_folder"),
                    title = stringResource(id = R.string.add_scan_folder),
                    description = stringResource(id = R.string.scan_folder_desc),
                    icon = NextIcons.Add,
                    onClick = { onAction(MediaLibraryPreferencesUiEvent.AddScanFolder) },
                    isFirstItem = false,
                    isLastItem = false,
                )
                preferences.scanFolders.forEachIndexed { index, path ->
                    ClickablePreferenceItem(
                        modifier = Modifier.restorableFocusItem(focusState, "scan_folder_$path"),
                        title = path,
                        description = path,
                        icon = NextIcons.Folder,
                        onClick = {},
                        onLongClick = { pendingRemovalPath = path },
                        isFirstItem = false,
                        isLastItem = index == preferences.scanFolders.lastIndex,
                    )
                }
                ClickablePreferenceItem(
                    modifier = Modifier.restorableFocusItem(focusState, "manage_folders"),
                    title = stringResource(id = R.string.manage_folders),
                    description = stringResource(id = R.string.manage_folders_desc),
                    icon = NextIcons.FolderOff,
                    onClick = { onAction(MediaLibraryPreferencesUiEvent.OpenFolders) },
                    isFirstItem = false,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.thumbnail))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.restorableFocusItem(focusState, "thumbnail"),
                    title = stringResource(id = R.string.thumbnail_generation),
                    description = when (preferences.thumbnailGenerationStrategy) {
                        ThumbnailGenerationStrategy.FIRST_FRAME -> stringResource(id = R.string.first_frame)
                        ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> stringResource(R.string.frame_at_position)
                        ThumbnailGenerationStrategy.HYBRID -> stringResource(id = R.string.hybrid)
                    },
                    icon = NextIcons.Image,
                    onClick = { onAction(MediaLibraryPreferencesUiEvent.OpenThumbnails) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }

    pendingRemovalPath?.let { path ->
        NextDialog(
            onDismissRequest = { pendingRemovalPath = null },
            title = {
                Text(text = stringResource(id = R.string.remove_scan_folder))
            },
            content = {
                Text(text = stringResource(id = R.string.remove_scan_folder_confirmation))
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(MediaLibraryPreferencesUiEvent.RemoveScanFolder(path))
                        pendingRemovalPath = null
                    },
                ) {
                    Text(text = stringResource(id = R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovalPath = null }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }
}

private fun hasAllFilesAccess(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

private fun launchAllFilesAccessSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = "package:${context.packageName}".toUri()
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
    context.startActivity(intent)
}

@PreviewLightDark
@Composable
private fun MediaLibraryPreferencesScreenPreview() {
    NextPlayerTheme {
        MediaLibraryPreferencesScreenContent(
            state = MediaLibraryPreferencesUiState(),
            onAction = {},
        )
    }
}
