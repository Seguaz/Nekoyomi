package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.selectedBackground

/**
 * Card variant of an episode, used when the episode list is switched to the 2-column grid view.
 * Shows the episode preview on top (only when the source provides one) with the title, date and
 * seen/bookmark/download state below. Swipe actions and the extra metadata of the list row are
 * intentionally omitted to keep the card compact.
 */
@Composable
fun AnimeEpisodeGridItem(
    title: String,
    date: String?,
    previewUrl: String?,
    seen: Boolean,
    bookmark: Boolean,
    fillermark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> AnimeDownload.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (seen) DISABLED_ALPHA else 1f
    val hasPreview = !previewUrl.isNullOrBlank()
    Column(
        modifier = modifier
            .padding(MaterialTheme.padding.extraSmall)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectedBackground(selected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (hasPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(previewUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(contentAlpha),
                )
                if (seen) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.small,
                vertical = MaterialTheme.padding.small,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = contentAlpha),
            )

            Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!date.isNullOrBlank()) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = LocalContentColor.current.copy(
                            alpha = if (seen) DISABLED_ALPHA else SECONDARY_ALPHA,
                        ),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!hasPreview && seen) {
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
                if (fillermark) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(18.dp),
                    )
                }

                EpisodeDownloadIndicator(
                    enabled = downloadIndicatorEnabled,
                    modifier = Modifier.padding(start = 2.dp),
                    downloadStateProvider = downloadStateProvider,
                    downloadProgressProvider = downloadProgressProvider,
                    onClick = { onDownloadClick?.invoke(it) },
                )
            }
        }
    }
}
