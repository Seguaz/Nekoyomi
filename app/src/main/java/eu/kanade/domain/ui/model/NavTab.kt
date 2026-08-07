package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The tabs the user can add to or remove from the bottom navigation bar. Only the More tab is always
 * shown; every unchosen [NavTab] is moved into the More tab instead.
 */
enum class NavTab(
    val tab: Tab,
    val prefKey: String,
    val titleRes: StringResource,
) {
    Anime(AnimeLibraryTab, "anime", AYMR.strings.label_anime_library),
    Manga(MangaLibraryTab, "manga", AYMR.strings.label_manga_library),
    Updates(UpdatesTab, "updates", MR.strings.label_recent_updates),
    History(HistoriesTab, "history", MR.strings.history),
    Browse(BrowseTab, "browse", MR.strings.browse),
    ;

    val icon: ImageVector
        @Composable
        get() = when (this) {
            Anime -> Icons.Outlined.VideoLibrary
            Manga -> Icons.Outlined.CollectionsBookmark
            Updates -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            History -> Icons.Outlined.History
            Browse -> Icons.Outlined.Explore
        }

    companion object {
        // Matches the previous default (history is moved into the More tab).
        val DEFAULT: Set<String> = setOf(Anime.prefKey, Manga.prefKey, Updates.prefKey, Browse.prefKey)

        /**
         * All tabs sorted by the user's saved [order] (a comma-separated list of prefKeys). Tabs
         * missing from [order] keep their enum order at the end, so new tabs appear predictably.
         */
        fun ordered(order: String): List<NavTab> {
            val keys = order.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return entries.sortedBy { keys.indexOf(it.prefKey).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }

        /** Tabs shown in the bottom bar, in the user's [order], for the [enabled] set (More is always last). */
        fun shownTabs(enabled: Set<String>, order: String): List<Tab> = buildList {
            ordered(order).forEach { if (it.prefKey in enabled) add(it.tab) }
            add(MoreTab)
        }

        /** Optional tabs not in the bottom bar; reached from the More tab. */
        fun hidden(enabled: Set<String>): List<NavTab> = entries.filter { it.prefKey !in enabled }
    }
}
