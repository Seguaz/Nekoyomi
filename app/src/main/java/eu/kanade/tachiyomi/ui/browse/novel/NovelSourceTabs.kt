package eu.kanade.tachiyomi.ui.browse.novel

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.MigrateMangaSourceScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.MangaSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.reader.loader.NovelSourceCompat
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Browse tabs for novels. Novels are manga sources/extensions in the tsundoku / NovelSourcery format
 * (they implement the `NovelSource` marker), so these REUSE the exact manga source/extension screens
 * (identical UI) with a filter that keeps only novels.
 */

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val screenModel = rememberScreenModel("novel-sources") {
        MangaSourcesScreenModel(sourceFilter = { NovelSourceCompat.isNovelSource(it.id) })
    }
    return mangaSourcesTab(screenModel, AYMR.strings.label_novel_sources, novelOnly = true)
}

@Composable
fun novelExtensionsTab(screenModel: MangaExtensionsScreenModel): TabContent {
    return mangaExtensionsTab(
        extensionsScreenModel = screenModel,
        titleRes = AYMR.strings.label_novel_extensions,
        reposScreen = NovelExtensionReposScreen(),
        // TODO: novel search needs a 3rd query slot in TabbedScreen (the anime/manga % 2 mapping);
        //  disabled for now to avoid feeding the novel search into the manga model.
        searchEnabled = false,
    )
}

@Composable
fun Screen.migrateNovelSourceTab(): TabContent {
    val screenModel = rememberScreenModel("novel-migrate") {
        MigrateMangaSourceScreenModel(sourceFilter = { NovelSourceCompat.isNovelSource(it.id) })
    }
    return migrateMangaSourceTab(screenModel, AYMR.strings.label_migration_novel)
}
