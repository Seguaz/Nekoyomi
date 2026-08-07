package eu.kanade.presentation.more.settings.screen.appearance

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavTab
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Lets the user reorder the bottom navigation tabs (drag) and choose which ones show (checkbox).
 * Unchecked tabs are moved into the More menu; the More tab itself is always shown last.
 */
class NavBarOrderScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val enabledPref = uiPreferences.bottomNavTabs()
        val orderPref = uiPreferences.bottomNavOrder()
        val enabled by enabledPref.collectAsState()
        val savedOrder by orderPref.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.pref_navigation_style),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            val lazyListState = rememberLazyListState()
            val listPadding = PaddingValues(MaterialTheme.padding.medium)
            // Working copy of the tab order; the saved order seeds it once.
            val localTabs = remember { NavTab.ordered(savedOrder).toMutableStateList() }
            var dirty by remember { mutableStateOf(false) }
            val reorderableState = rememberReorderableLazyListState(lazyListState, listPadding) { from, to ->
                localTabs.add(to.index, localTabs.removeAt(from.index))
                dirty = true
            }

            // Persist only when the drag gesture ends, not on every reorder tick.
            LaunchedEffect(reorderableState.isAnyItemDragging) {
                if (!reorderableState.isAnyItemDragging && dirty) {
                    orderPref.set(localTabs.joinToString(",") { it.prefKey })
                    dirty = false
                }
            }

            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(AYMR.strings.pref_navigation_style_hint),
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
                        items = localTabs,
                        key = { "tab-${it.prefKey}" },
                    ) { navTab ->
                        val isEnabled = navTab.prefKey in enabled
                        ReorderableItem(reorderableState, key = "tab-${navTab.prefKey}") {
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
                                    Icon(
                                        imageVector = navTab.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = MaterialTheme.padding.medium)
                                            .alpha(if (isEnabled) 1f else 0.4f),
                                    )
                                    Text(
                                        text = stringResource(navTab.titleRes),
                                        modifier = Modifier
                                            .weight(1f)
                                            .alpha(if (isEnabled) 1f else 0.4f),
                                    )
                                    Checkbox(
                                        checked = isEnabled,
                                        onCheckedChange = {
                                            val current = enabledPref.get()
                                            enabledPref.set(
                                                if (navTab.prefKey in current) {
                                                    current - navTab.prefKey
                                                } else {
                                                    current + navTab.prefKey
                                                },
                                            )
                                        },
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
