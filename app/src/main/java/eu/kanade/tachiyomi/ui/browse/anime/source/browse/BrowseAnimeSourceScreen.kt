package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.browse.anime.MissingSourceScreen
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceToolbar
import eu.kanade.presentation.browse.components.DeleteLocalEntryDialog
import eu.kanade.presentation.browse.components.LocalSourceImportDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.storage.ImportResult
import eu.kanade.tachiyomi.util.storage.LocalSourceImporter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.LocalAnimeSource

data class BrowseAnimeSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { BrowseAnimeSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(
                    null,
                )
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubAnimeSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? AnimeHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        // In-app import into the local source (pick video files or a folder).
        val context = LocalContext.current
        val isLocalSource = screenModel.source is LocalAnimeSource
        val importer = remember { LocalSourceImporter(context) }
        var showImportDialog by remember { mutableStateOf(false) }
        var importTitle by remember { mutableStateOf("") }
        val importSources = remember { mutableStateListOf<UniFile>() }
        var importing by remember { mutableStateOf(false) }
        val importFilePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            val picked = uris.mapNotNull { UniFile.fromUri(context, it) }
            importSources.addAll(LocalSourceImporter.filterSupported(picked, isAnime = true))
        }
        val importFolderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { treeUri ->
            val tree = treeUri?.let { UniFile.fromUri(context, it) }
            if (tree != null) {
                // Add the folder's children (files and episode subfolders); the importer recurses
                // into subfolders, so they're copied directly under the entry (no extra nesting).
                importSources.addAll(
                    LocalSourceImporter.filterImportable(tree.listFiles().orEmpty().toList(), isAnime = true),
                )
                if (importTitle.isBlank()) importTitle = tree.name.orEmpty()
            }
        }
        val resetImport = {
            showImportDialog = false
            importTitle = ""
            importSources.clear()
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? AnimeHttpSource)?.baseUrl
        }

        val animeList = screenModel.animePagerFlowFlow.collectAsLazyPagingItems()

        var topBarHeight by remember { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { topBarHeight = it.height },
                ) {
                    BrowseAnimeSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(AnimeSourcePreferencesScreen(sourceId)) },
                        onSearch = screenModel::search,
                        onImportClick = { showImportDialog = true }.takeIf { isLocalSource },
                        incognitoMode = state.incognitoMode,
                        onToggleIncognito = screenModel::toggleIncognito
                            .takeIf { screenModel.extensionPackage != null },
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as AnimeCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseAnimeSourceContent(
                source = screenModel.source,
                animeList = animeList,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                entries = screenModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
                topBarHeight = topBarHeight,
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAnimeSourceHelpClick = onHelpClick,
                onAnimeClick = { navigator.push((AnimeScreen(it.id, true))) },
                onAnimeLongClick = { anime ->
                    if (isLocalSource) {
                        // Local entries are user files, so long-press offers to delete them.
                        screenModel.setDialog(BrowseAnimeSourceScreenModel.Dialog.DeleteLocal(anime))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        scope.launchIO {
                            val duplicateAnime = screenModel.getDuplicateAnimelibAnime(anime)
                            when {
                                anime.favorite -> screenModel.setDialog(
                                    BrowseAnimeSourceScreenModel.Dialog.RemoveAnime(anime),
                                )
                                duplicateAnime != null -> screenModel.setDialog(
                                    BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime(
                                        anime,
                                        duplicateAnime,
                                    ),
                                )
                                else -> screenModel.addFavorite(anime)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseAnimeSourceScreenModel.Dialog.Filter -> {
                SourceFilterAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.anime) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.setDialog(
                            BrowseAnimeSourceScreenModel.Dialog.Migrate(dialog.anime, dialog.duplicate),
                        )
                    },
                )
            }

            is BrowseAnimeSourceScreenModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = MigrateAnimeDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.oldAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.RemoveAnime -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeAnimeFavorite(dialog.anime)
                    },
                    entryToRemove = dialog.anime.title,
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.DeleteLocal -> {
                DeleteLocalEntryDialog(
                    entryTitle = dialog.anime.title,
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        scope.launchIO {
                            importer.delete(isAnime = true, url = dialog.anime.url)
                            screenModel.deleteCachedLocalEntry(dialog.anime.id)
                            withUIContext { animeList.refresh() }
                        }
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.ChangeAnimeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab) },
                    onConfirm = { include, _ ->
                        screenModel.changeAnimeFavorite(dialog.anime)
                        screenModel.moveAnimeToCategories(dialog.anime, include)
                    },
                )
            }
            else -> {}
        }

        if (showImportDialog) {
            LocalSourceImportDialog(
                title = importTitle,
                onTitleChange = { importTitle = it },
                fileNames = importSources.map { it.name.orEmpty() },
                importing = importing,
                onAddFiles = { importFilePicker.launch(arrayOf("video/*")) },
                onAddFolder = { importFolderPicker.launch(null) },
                onRemoveAt = { importSources.removeAt(it) },
                onImport = {
                    importing = true
                    val title = importTitle
                    val sources = importSources.toList()
                    scope.launchIO {
                        val result = importer.import(isAnime = true, title = title, sources = sources)
                        withUIContext {
                            importing = false
                            resetImport()
                            animeList.refresh()
                            val message = when (result) {
                                is ImportResult.Done ->
                                    context.stringResource(MR.strings.import_completed, result.copied)
                                ImportResult.NoSpace ->
                                    context.stringResource(MR.strings.import_no_space)
                                else -> context.stringResource(MR.strings.import_no_storage)
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                },
                onDismissRequest = resetImport,
            )
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
