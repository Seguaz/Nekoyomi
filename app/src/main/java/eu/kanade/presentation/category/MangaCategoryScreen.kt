package eu.kanade.presentation.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryScreenState
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MangaCategoryScreen(
    state: MangaCategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickToggleAutoHide: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onSetReadingMode: (Category, ReadingMode) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var pickingModeFor by remember { mutableStateOf<Category?>(null) }
    Scaffold(
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            categories = state.categories,
            autoHideCategoryIds = state.autoHideCategoryIds,
            categoryReadingModes = state.categoryReadingModes,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickToggleAutoHide = onClickToggleAutoHide,
            onClickHide = onClickHide,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
            onClickReadingMode = { pickingModeFor = it },
        )
    }

    pickingModeFor?.let { category ->
        val current = ReadingMode.fromPreference(state.categoryReadingModes[category.id])
        CategoryReadingModeDialog(
            categoryName = category.name,
            selected = current,
            onSelect = { mode ->
                onSetReadingMode(category, mode)
                pickingModeFor = null
            },
            onDismissRequest = { pickingModeFor = null },
        )
    }
}

@Composable
private fun CategoryContent(
    categories: List<Category>,
    autoHideCategoryIds: Set<Long>,
    categoryReadingModes: Map<Long, Int>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickToggleAutoHide: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onClickReadingMode: (Category) -> Unit,
) {
    val categoriesState = remember { categories.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = categoriesState.removeAt(from.index)
        categoriesState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(categories) {
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(categories)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = categoriesState,
            key = { category -> category.key },
        ) { category ->
            ReorderableItem(reorderableState, category.key) {
                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    category = category,
                    autoHide = category.id in autoHideCategoryIds,
                    onRename = { onClickRename(category) },
                    onToggleAutoHide = { onClickToggleAutoHide(category) },
                    onHide = { onClickHide(category) },
                    onDelete = { onClickDelete(category) },
                    readingModeIconRes = ReadingMode.fromPreference(categoryReadingModes[category.id]).iconRes,
                    onSetReadingMode = { onClickReadingMode(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryReadingModeDialog(
    categoryName: String,
    selected: ReadingMode,
    onSelect: (ReadingMode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.category_reading_mode_title, categoryName)) },
        text = {
            Column {
                ReadingMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            onClick = { onSelect(mode) },
                        )
                        Icon(
                            painter = painterResource(mode.iconRes),
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
                        )
                        Text(text = stringResource(mode.stringRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private val Category.key inline get() = "category-$id"
