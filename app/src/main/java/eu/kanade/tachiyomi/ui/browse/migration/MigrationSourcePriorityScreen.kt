package eu.kanade.tachiyomi.ui.browse.migration

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcePriorityScreen(private val isManga: Boolean) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrationSourcePriorityScreenModel(isManga) }
        val sources by screenModel.sources.collectAsState()
        val excludedSources by screenModel.excludedSources.collectAsState()
        val excludedLanguages by screenModel.excludedLanguages.collectAsState()

        MigrationSourcePriorityContent(
            sources = sources,
            languages = screenModel.languages,
            excludedSources = excludedSources,
            excludedLanguages = excludedLanguages,
            navigateUp = navigator::pop,
            onSaveOrder = screenModel::save,
            onToggleSource = screenModel::toggleSource,
            onToggleLanguage = screenModel::toggleLanguage,
        )
    }
}

data class MigrationPrioritySource(
    val id: Long,
    val name: String,
    val lang: String,
)

class MigrationSourcePriorityScreenModel(
    private val isManga: Boolean,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) : ScreenModel {

    private val pref = if (isManga) {
        sourcePreferences.migrationSourcePriorityManga()
    } else {
        sourcePreferences.migrationSourcePriorityAnime()
    }
    private val excludedSourcesPref: Preference<Set<String>> = if (isManga) {
        sourcePreferences.migrationExcludedSourcesManga()
    } else {
        sourcePreferences.migrationExcludedSourcesAnime()
    }
    private val excludedLanguagesPref: Preference<Set<String>> = if (isManga) {
        sourcePreferences.migrationExcludedLanguagesManga()
    } else {
        sourcePreferences.migrationExcludedLanguagesAnime()
    }

    private val _sources = MutableStateFlow(load())
    val sources = _sources.asStateFlow()

    /** Distinct languages present among the sources, for the language filter chips. */
    val languages: List<String> = _sources.value.map { it.lang }.distinct().sorted()

    private val _excludedSources = MutableStateFlow(excludedSourcesPref.get())
    val excludedSources = _excludedSources.asStateFlow()

    private val _excludedLanguages = MutableStateFlow(excludedLanguagesPref.get())
    val excludedLanguages = _excludedLanguages.asStateFlow()

    private fun load(): List<MigrationPrioritySource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabled = if (isManga) {
            sourcePreferences.disabledMangaSources().get()
        } else {
            sourcePreferences.disabledAnimeSources().get()
        }
        val all = if (isManga) {
            mangaSourceManager.getCatalogueSources().map { MigrationPrioritySource(it.id, it.name, it.lang) }
        } else {
            animeSourceManager.getCatalogueSources().map { MigrationPrioritySource(it.id, it.name, it.lang) }
        }.filter { it.lang in enabledLanguages && "${it.id}" !in disabled }

        val order = pref.get().split(",").mapNotNull { it.trim().toLongOrNull() }
        val rank = { id: Long -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
        return all.sortedWith(compareBy({ rank(it.id) }, { "${it.name.lowercase()} (${it.lang})" }))
    }

    /**
     * Persists the full order. Called once when a drag gesture ends (NOT on every reorder tick).
     */
    fun save(orderedIds: List<Long>) {
        pref.set(orderedIds.joinToString(","))
        val byId = _sources.value.associateBy { it.id }
        _sources.value = orderedIds.mapNotNull { byId[it] }
    }

    /** Toggle whether a single source is searched during migration. */
    fun toggleSource(id: Long) {
        val key = id.toString()
        val current = excludedSourcesPref.get()
        val updated = if (key in current) current - key else current + key
        excludedSourcesPref.set(updated)
        _excludedSources.value = updated
    }

    /** Toggle whether a whole language is searched during migration. */
    fun toggleLanguage(lang: String) {
        val current = excludedLanguagesPref.get()
        val updated = if (lang in current) current - lang else current + lang
        excludedLanguagesPref.set(updated)
        _excludedLanguages.value = updated
    }
}

@Composable
private fun MigrationSourcePriorityContent(
    sources: List<MigrationPrioritySource>,
    languages: List<String>,
    excludedSources: Set<String>,
    excludedLanguages: Set<String>,
    navigateUp: () -> Unit,
    onSaveOrder: (List<Long>) -> Unit,
    onToggleSource: (Long) -> Unit,
    onToggleLanguage: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(AYMR.strings.migration_source_priority),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()
        val localSources = remember { sources.toMutableStateList() }
        val listPadding = PaddingValues(MaterialTheme.padding.medium)
        var dirty by remember { mutableStateOf(false) }
        val reorderableState = rememberReorderableLazyListState(lazyListState, listPadding) { from, to ->
            localSources.add(to.index, localSources.removeAt(from.index))
            dirty = true
        }

        // Persist only when the drag gesture ends, not on every reorder tick.
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging && dirty) {
                onSaveOrder(localSources.map { it.id })
                dirty = false
            }
        }

        // Re-sync from the model only when not dragging.
        LaunchedEffect(sources) {
            if (!reorderableState.isAnyItemDragging && !dirty) {
                localSources.clear()
                localSources.addAll(sources)
            }
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(AYMR.strings.migration_source_priority_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            )

            if (languages.size > 1) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    languages.forEach { lang ->
                        FilterChip(
                            selected = lang !in excludedLanguages,
                            onClick = { onToggleLanguage(lang) },
                            label = { Text(text = lang.uppercase()) },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(
                    items = localSources,
                    key = { "source-${it.id}" },
                ) { source ->
                    val languageOff = source.lang in excludedLanguages
                    val included = "${source.id}" !in excludedSources && !languageOff
                    ReorderableItem(reorderableState, key = "source-${source.id}") {
                        ElevatedCard(modifier = Modifier.animateItem()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.padding.small)
                                    .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(MaterialTheme.padding.medium)
                                        .draggableHandle(),
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .alpha(if (included) 1f else 0.4f),
                                ) {
                                    Text(text = source.name)
                                    Text(
                                        text = source.lang.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(
                                    checked = "${source.id}" !in excludedSources,
                                    onCheckedChange = { onToggleSource(source.id) },
                                    enabled = !languageOff,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
