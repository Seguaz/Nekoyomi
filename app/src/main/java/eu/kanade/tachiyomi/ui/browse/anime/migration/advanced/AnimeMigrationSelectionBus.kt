package eu.kanade.tachiyomi.ui.browse.anime.migration.advanced

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.domain.entries.anime.model.Anime

/**
 * Lightweight in-memory bridge used by the mass migration screen to receive a manually picked
 * alternative for a given entry from the search screen, without migrating it immediately.
 *
 * The emission is keyed by the original entry id so the list screen knows which card to update.
 */
object AnimeMigrationSelectionBus {

    private val _selections = MutableSharedFlow<Pair<Long, Anime>>(extraBufferCapacity = 16)
    val selections = _selections.asSharedFlow()

    fun select(oldAnimeId: Long, newAnime: Anime) {
        _selections.tryEmit(oldAnimeId to newAnime)
    }
}
