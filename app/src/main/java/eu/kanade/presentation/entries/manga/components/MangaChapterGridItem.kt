package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.selectedBackground

/**
 * Card variant of a chapter, used when the chapter list is switched to the 2-column grid view.
 * Chapters have no per-item thumbnail, so the card is a compact text card: title, optional read
 * progress, date and read/bookmark/download state. Swipe actions of the list row are omitted.
 */
@Composable
fun MangaChapterGridItem(
    title: String,
    date: String?,
    readProgress: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> MangaDownload.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (read) DISABLED_ALPHA else 1f
    Column(
        modifier = modifier
            .padding(MaterialTheme.padding.extraSmall)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectedBackground(selected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(MaterialTheme.padding.small),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = LocalContentColor.current.copy(alpha = contentAlpha),
        )

        if (!readProgress.isNullOrBlank()) {
            Text(
                text = readProgress,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
            )
        }

        Spacer(modifier = Modifier.size(MaterialTheme.padding.extraSmall))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!date.isNullOrBlank()) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = LocalContentColor.current.copy(
                        alpha = if (read) DISABLED_ALPHA else SECONDARY_ALPHA,
                    ),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (read) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }
            if (bookmark) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }

            ChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled,
                modifier = Modifier.padding(start = 2.dp),
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadClick?.invoke(it) },
            )
        }
    }
}
