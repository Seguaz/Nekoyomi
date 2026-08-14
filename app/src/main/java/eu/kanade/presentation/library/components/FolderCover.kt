package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File

private val FolderPlaceholderColor = Color(0x1F888888)

/**
 * Cover for a custom-series folder cell. Shows the user's custom cover when [coverPath] is set,
 * otherwise a 2x2 collage built from the first members' covers ([previewCovers]). Fills [modifier].
 */
@Composable
fun FolderCover(
    coverPath: String?,
    previewCovers: List<Any>,
    modifier: Modifier = Modifier,
) {
    // Match the rounded corners of a normal cover (ItemCover uses shapes.extraSmall).
    val shape = MaterialTheme.shapes.extraSmall
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            placeholder = ColorPainter(FolderPlaceholderColor),
            error = ColorPainter(FolderPlaceholderColor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Column(modifier = modifier.clip(shape).background(FolderPlaceholderColor)) {
            for (rowIndex in 0 until 2) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (colIndex in 0 until 2) {
                        val cover = previewCovers.getOrNull(rowIndex * 2 + colIndex)
                        if (cover != null) {
                            AsyncImage(
                                model = cover,
                                placeholder = ColorPainter(FolderPlaceholderColor),
                                error = ColorPainter(FolderPlaceholderColor),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/** Convenience overload that fills the available space. */
@Composable
fun FolderCover(coverPath: String?, previewCovers: List<Any>) {
    FolderCover(coverPath, previewCovers, Modifier.fillMaxSize())
}
