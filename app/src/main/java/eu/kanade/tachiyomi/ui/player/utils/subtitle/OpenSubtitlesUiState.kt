package eu.kanade.tachiyomi.ui.player.utils.subtitle

/**
 * UI state for the OpenSubtitles search dialog. A null value means the dialog is hidden.
 */
sealed interface OpenSubtitlesUiState {
    data object Loading : OpenSubtitlesUiState
    data class Results(val subtitles: List<RemoteSubtitle>) : OpenSubtitlesUiState
    data object Empty : OpenSubtitlesUiState
    data object Error : OpenSubtitlesUiState
}
