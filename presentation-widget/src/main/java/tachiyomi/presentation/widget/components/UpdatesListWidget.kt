package tachiyomi.presentation.widget.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.kanade.tachiyomi.core.common.Constants
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.util.appWidgetInnerRadius

/**
 * A single entry shown in a text list update widget: cover + title + latest chapter/episode name.
 */
data class UpdatesWidgetItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val cover: Bitmap?,
)

val ListCoverWidth = 40.dp
val ListCoverHeight = 56.dp

/**
 * Renders recent updates as a scrollable text list (cover + title + latest item name), as opposed
 * to the cover-only grid widget. [shortcutAction] / [extraKey] decide whether tapping a row opens
 * the manga or anime entry.
 */
@Composable
fun UpdatesListWidget(
    data: ImmutableList<UpdatesWidgetItem>?,
    contentColor: ColorProvider,
    shortcutAction: String,
    extraKey: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    if (data.isNullOrEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            // Use Glance's LocalContext + the Context string extension; the @Composable
            // stringResource relies on Compose-UI's LocalContext, which isn't present in a Glance
            // composition and throws ("can't display content") when the empty state is shown.
            Text(
                text = LocalContext.current.stringResource(MR.strings.information_no_recent),
                style = TextStyle(color = contentColor),
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(data, itemId = { it.id }) { item ->
                val context = LocalContext.current
                val intent = Intent(context, Class.forName(Constants.MAIN_ACTIVITY)).apply {
                    action = shortcutAction
                    putExtra(extraKey, item.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    // https://issuetracker.google.com/issues/238793260
                    addCategory(item.id.toString())
                }
                UpdatesListRow(
                    item = item,
                    contentColor = contentColor,
                    modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                )
            }
        }
    }
}

@Composable
private fun UpdatesListRow(
    item: UpdatesWidgetItem,
    contentColor: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(width = ListCoverWidth, height = ListCoverHeight)
                .appWidgetInnerRadius(),
        ) {
            Image(
                provider = if (item.cover != null) {
                    ImageProvider(item.cover)
                } else {
                    ImageProvider(R.drawable.appwidget_cover_error)
                },
                contentDescription = null,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetInnerRadius(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 8.dp),
        ) {
            Text(
                text = item.title,
                maxLines = 1,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = item.subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}
