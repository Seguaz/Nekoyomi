package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Dialog to import files into the local manga/anime source. The user names the entry and picks one
 * or more files (or a whole folder); each file becomes a chapter/episode inside a folder with that
 * name. The actual copy happens in the caller; this dialog only collects the title and file list.
 */
@Composable
fun LocalSourceImportDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    fileNames: List<String>,
    importing: Boolean,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    onImport: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!importing) onDismissRequest() },
        confirmButton = {
            TextButton(
                enabled = !importing && title.isNotBlank() && fileNames.isNotEmpty(),
                onClick = onImport,
            ) {
                Text(text = stringResource(MR.strings.action_import))
            }
        },
        dismissButton = {
            TextButton(enabled = !importing, onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(text = stringResource(MR.strings.import_to_local_title)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(text = stringResource(MR.strings.import_field_title)) },
                    singleLine = true,
                    enabled = !importing,
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onAddFiles,
                        enabled = !importing,
                    ) {
                        Icon(imageVector = Icons.Outlined.InsertDriveFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(MR.strings.action_add_files))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onAddFolder,
                        enabled = !importing,
                    ) {
                        Icon(imageVector = Icons.Outlined.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(MR.strings.action_add_folder))
                    }
                }

                if (fileNames.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(MR.strings.import_selected_files, fileNames.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(fileNames) { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!importing) {
                                    IconButton(onClick = { onRemoveAt(index) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(MR.strings.action_remove),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (importing) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(MR.strings.import_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
    )
}
