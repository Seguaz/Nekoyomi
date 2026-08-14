package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

/**
 * Actions menu shown when long-pressing a custom-series folder: change/remove its cover, rename it,
 * or disband it (ungroup all its members).
 */
@Composable
fun FolderActionsDialog(
    folderName: String,
    hasCover: Boolean,
    onDismissRequest: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onRename: () -> Unit,
    onDisband: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(text = folderName) },
        text = {
            Column {
                FolderActionRow(
                    icon = Icons.Outlined.Image,
                    label = stringResource(MR.strings.action_change_group_cover),
                ) {
                    onDismissRequest()
                    onChangeCover()
                }
                if (hasCover) {
                    FolderActionRow(
                        icon = Icons.Outlined.HideImage,
                        label = stringResource(MR.strings.action_remove_group_cover),
                    ) {
                        onDismissRequest()
                        onRemoveCover()
                    }
                }
                FolderActionRow(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(MR.strings.action_rename_folder),
                ) {
                    // Keep the dialog flow going: onRename swaps to the rename dialog.
                    onRename()
                }
                FolderActionRow(
                    icon = Icons.Outlined.LayersClear,
                    label = stringResource(MR.strings.action_disband_folder),
                ) {
                    onDismissRequest()
                    onDisband()
                }
            }
        },
    )
}

@Composable
private fun FolderActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Dialog to rename a custom-series folder, pre-filled with its current name. */
@Composable
fun RenameFolderDialog(
    currentName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim())
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(text = stringResource(MR.strings.action_rename_folder)) },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(MR.strings.series_name)) },
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}
