package eu.kanade.presentation.category.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    autoHide: Boolean,
    onRename: () -> Unit,
    onToggleAutoHide: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // Reading-mode-per-category (manga only). Null hides the menu item (e.g. anime categories).
    @DrawableRes readingModeIconRes: Int? = null,
    onSetReadingMode: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
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
            Text(
                text = category.name,
                modifier = Modifier.weight(1f),
            )
            // All actions live in an overflow menu so the name always has room to breathe.
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(MR.strings.label_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_rename_category)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    )
                    if (readingModeIconRes != null && onSetReadingMode != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(AYMR.strings.action_category_reading_mode)) },
                            onClick = {
                                menuExpanded = false
                                onSetReadingMode()
                            },
                            leadingIcon = {
                                Icon(painter = painterResource(readingModeIconRes), contentDescription = null)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(AYMR.strings.action_auto_hide_category)) },
                        onClick = {
                            menuExpanded = false
                            onToggleAutoHide()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (autoHide) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(AYMR.strings.action_hide)) },
                        onClick = {
                            menuExpanded = false
                            onHide()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (category.hidden) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }
}
