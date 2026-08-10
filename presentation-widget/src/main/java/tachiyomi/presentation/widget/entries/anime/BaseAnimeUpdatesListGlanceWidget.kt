package tachiyomi.presentation.widget.entries.anime

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.components.ListCoverHeight
import tachiyomi.presentation.widget.components.ListCoverWidth
import tachiyomi.presentation.widget.components.UpdatesListWidget
import tachiyomi.presentation.widget.components.UpdatesWidgetItem
import tachiyomi.presentation.widget.components.anime.LockedAnimeWidget
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseAnimeUpdatesListGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetAnimeUpdates = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    // A scrollable list doesn't need a RemoteViews per exact size; a single one avoids the
    // "content too large" translation failure that Exact can hit with lazy lists of covers.
    override val sizeMode = SizeMode.Single

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        // Load once here (in the suspend context) instead of collecting a flow inside the
        // composition: the flow's first emission can land after Glance has already snapshotted the
        // RemoteViews, leaving the widget stuck with no data. The widget manager re-runs this on
        // updates, so live refresh still works.
        val items = if (locked) {
            persistentListOf()
        } else {
            runCatching {
                getUpdates.await(seen = false, after = DateLimit.toEpochMilli()).prepareData()
            }.getOrElse {
                logcat(LogPriority.ERROR, it) { "Failed to load anime updates for widget" }
                persistentListOf()
            }
        }

        provideContent {
            if (locked) {
                LockedAnimeWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }
            UpdatesListWidget(
                data = items,
                contentColor = foreground,
                shortcutAction = Constants.SHORTCUT_ANIME,
                extraKey = Constants.ANIME_EXTRA,
                modifier = containerModifier,
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun List<AnimeUpdatesWithRelations>.prepareData(): ImmutableList<UpdatesWidgetItem> {
        val widthPx = ListCoverWidth.value.toInt().dpToPx
        val heightPx = ListCoverHeight.value.toInt().dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        return withIOContext {
            this@prepareData
                .distinctBy { it.animeId }
                .take(MAX_ITEMS)
                .map { updatesView ->
                    val request = ImageRequest.Builder(context)
                        .data(
                            AnimeCover(
                                animeId = updatesView.animeId,
                                sourceId = updatesView.sourceId,
                                isAnimeFavorite = true,
                                url = updatesView.coverData.url,
                                lastModified = updatesView.coverData.lastModified,
                            ),
                        )
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .precision(Precision.EXACT)
                        .size(widthPx, heightPx)
                        .scale(Scale.FILL)
                        .let {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                it.transformations(RoundedCornersTransformation(roundPx))
                            } else {
                                it // Handled by system
                            }
                        }
                        .build()
                    val bitmap = context.imageLoader.executeBlocking(request)
                        .image
                        ?.asDrawable(context.resources)
                        ?.toBitmap()
                    UpdatesWidgetItem(
                        id = updatesView.animeId,
                        title = updatesView.animeTitle,
                        subtitle = updatesView.episodeName,
                        cover = bitmap,
                    )
                }
                .toImmutableList()
        }
    }

    companion object {
        private const val MAX_ITEMS = 20

        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
