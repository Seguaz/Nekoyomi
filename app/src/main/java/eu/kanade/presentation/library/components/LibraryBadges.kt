package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.presentation.core.components.Badge

@Composable
internal fun DownloadsBadge(count: Long) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnviewedBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun PinnedBadge(pinned: Boolean) {
    if (pinned) {
        Badge(
            imageVector = Icons.Filled.PushPin,
            color = MaterialTheme.colorScheme.primary,
            iconColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun SeriesBadge(
    seriesName: String?,
    count: Int = 0,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
) {
    if (seriesName == null) return
    if (count > 1) {
        // Series head: member count + a tappable expand/collapse chevron. Tapping the cover opens (or
        // expands) the entry; the chevron is the way to collapse an expanded series.
        Badge(
            imageVector = Icons.Outlined.Layers,
            text = "$count",
            color = MaterialTheme.colorScheme.secondary,
            iconColor = MaterialTheme.colorScheme.onSecondary,
        )
        Badge(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            modifier = if (onToggleExpanded != null) {
                Modifier.clickable(onClick = onToggleExpanded)
            } else {
                Modifier
            },
            color = MaterialTheme.colorScheme.secondary,
            iconColor = MaterialTheme.colorScheme.onSecondary,
        )
    } else {
        // Lone grouped entry or an expanded member: plain marker.
        Badge(
            imageVector = Icons.Outlined.Layers,
            color = MaterialTheme.colorScheme.secondary,
            iconColor = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

@Composable
internal fun FolderBadge(count: Int) {
    Badge(
        imageVector = Icons.Outlined.Folder,
        text = "$count",
        color = MaterialTheme.colorScheme.secondary,
        iconColor = MaterialTheme.colorScheme.onSecondary,
    )
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnviewedBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
        }
    }
}
