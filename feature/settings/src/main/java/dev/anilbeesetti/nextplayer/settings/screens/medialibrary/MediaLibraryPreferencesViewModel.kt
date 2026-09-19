package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MediaLibraryPreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val systemService: SystemService,
    @InjectedParam internal var output: Output,
) : MviViewModel<MediaLibraryPreferencesUiState, MediaLibraryPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
        val openFolders: () -> Unit,
        val openThumbnails: () -> Unit,
    )

    private val stateInternal = MutableStateFlow(MediaLibraryPreferencesUiState())
    override val state: StateFlow<MediaLibraryPreferencesUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                stateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    override fun onAction(action: MediaLibraryPreferencesUiEvent) {
        when (action) {
            is MediaLibraryPreferencesUiEvent.NavigateUp -> output.navigateUp()
            is MediaLibraryPreferencesUiEvent.OpenFolders -> output.openFolders()
            is MediaLibraryPreferencesUiEvent.OpenThumbnails -> output.openThumbnails()

            is MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
            is MediaLibraryPreferencesUiEvent.ToggleShowHiddenFiles -> toggleShowHiddenFiles()
            is MediaLibraryPreferencesUiEvent.ToggleRespectNoMedia -> toggleRespectNoMedia()
            is MediaLibraryPreferencesUiEvent.PickScanFolder -> pickScanFolder()
            is MediaLibraryPreferencesUiEvent.ClearScanFolder -> clearScanFolder()
        }
    }

    private fun toggleMarkLastPlayedMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(markLastPlayedMedia = !it.markLastPlayedMedia)
            }
        }
    }

    private fun toggleShowHiddenFiles() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(showHiddenFiles = !it.showHiddenFiles)
            }
        }
    }

    private fun toggleRespectNoMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(respectNoMedia = !it.respectNoMedia)
            }
        }
    }

    private fun pickScanFolder() {
        viewModelScope.launch {
            val path = systemService.pickFolderPath()
            if (path != null) {
                preferencesRepository.updateApplicationPreferences {
                    it.copy(scanFolderPath = path)
                }
            }
        }
    }

    private fun clearScanFolder() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(scanFolderPath = null)
            }
        }
    }
}

data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MediaLibraryPreferencesUiEvent {
    data object NavigateUp : MediaLibraryPreferencesUiEvent
    data object OpenFolders : MediaLibraryPreferencesUiEvent
    data object OpenThumbnails : MediaLibraryPreferencesUiEvent

    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
    data object ToggleShowHiddenFiles : MediaLibraryPreferencesUiEvent
    data object ToggleRespectNoMedia : MediaLibraryPreferencesUiEvent
    data object PickScanFolder : MediaLibraryPreferencesUiEvent
    data object ClearScanFolder : MediaLibraryPreferencesUiEvent
}
