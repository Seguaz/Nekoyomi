package eu.kanade.tachiyomi.ui.browse.manga.migration.advanced

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tachiyomi.domain.entries.manga.model.Manga

/**
 * Lightweight in-memory bridge used by the mass migration screen to receive a manually picked
 * alternative for a given entry from the search screen, without migrating it immediately.
 *
 * The emission is keyed by the original entry id so the list screen knows which card to update.
 */
object MangaMigrationSelectionBus {

    private val _selections = MutableSharedFlow<Pair<Long, Manga>>(extraBufferCapacity = 16)
    val selections = _selections.asSharedFlow()

    fun select(oldMangaId: Long, newManga: Manga) {
        _selections.tryEmit(oldMangaId to newManga)
    }
}
