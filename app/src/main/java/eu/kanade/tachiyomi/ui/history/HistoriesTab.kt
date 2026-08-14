package eu.kanade.tachiyomi.ui.history

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.HomeTabsMode
import eu.kanade.domain.ui.model.NavTab
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.anime.resumeLastEpisodeSeenEvent
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object HistoriesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 5u,
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastEpisodeSeenEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val fromMore = NavTab.History.prefKey !in currentBottomNavTabs()
        val mode = remember { Injekt.get<UiPreferences>() }.homeTabsMode().get()
        // Hoisted for history tab's search bar
        val mangaHistoryScreenModel = rememberScreenModel { MangaHistoryScreenModel() }
        val mangaSearchQuery by mangaHistoryScreenModel.query.collectAsState()

        val animeHistoryScreenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val animeSearchQuery by animeHistoryScreenModel.query.collectAsState()

        val tabs = when (mode) {
            HomeTabsMode.ANIME_ONLY -> persistentListOf(animeHistoryTab(context, fromMore))
            HomeTabsMode.MANGA_ONLY -> persistentListOf(mangaHistoryTab(context, fromMore))
            else -> persistentListOf(animeHistoryTab(context, fromMore), mangaHistoryTab(context, fromMore))
        }
        // TabbedScreen maps the search query by currentPage % 2 (anime = even). A lone manga tab sits at
        // index 0 (even), so feed the manga query into the anime slot there so its search still works.
        val mangaOnly = mode == HomeTabsMode.MANGA_ONLY

        TabbedScreen(
            titleRes = MR.strings.label_recent_manga,
            tabs = tabs,
            state = rememberPagerState(mode.defaultIndex.coerceAtMost(tabs.lastIndex)) { tabs.size },
            mangaSearchQuery = mangaSearchQuery,
            onChangeMangaSearchQuery = mangaHistoryScreenModel::search,
            animeSearchQuery = if (mangaOnly) mangaSearchQuery else animeSearchQuery,
            onChangeAnimeSearchQuery = if (mangaOnly) {
                mangaHistoryScreenModel::search
            } else {
                animeHistoryScreenModel::search
            },
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1
