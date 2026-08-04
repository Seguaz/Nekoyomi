package eu.kanade.tachiyomi.ui.browse.migration

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ElevatedCard
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

        MigrationSourcePriorityContent(
            sources = sources,
            navigateUp = navigator::pop,
            onSaveOrder = screenModel::save,
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

    private val _sources = MutableStateFlow(load())
    val sources = _sources.asStateFlow()

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
     * Persists the full order. Called once when a drag gesture ends (NOT on every reorder tick),
     * because each write triggers the migrate list's re-search collector.
     */
    fun save(orderedIds: List<Long>) {
        pref.set(orderedIds.joinToString(","))
        val byId = _sources.value.associateBy { it.id }
        _sources.value = orderedIds.mapNotNull { byId[it] }
    }
}

@Composable
private fun MigrationSourcePriorityContent(
    sources: List<MigrationPrioritySource>,
    navigateUp: () -> Unit,
    onSaveOrder: (List<Long>) -> Unit,
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
                    ReorderableItem(reorderableState, key = "source-${source.id}") {
                        ElevatedCard(modifier = Modifier.animateItem()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.padding.small)
                                    .padding(
                                        start = MaterialTheme.padding.small,
                                        end = MaterialTheme.padding.medium,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(MaterialTheme.padding.medium)
                                        .draggableHandle(),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = source.name)
                                    Text(
                                        text = source.lang.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
