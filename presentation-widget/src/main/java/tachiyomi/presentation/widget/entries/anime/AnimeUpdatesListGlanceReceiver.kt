package tachiyomi.presentation.widget.entries.anime

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AnimeUpdatesListGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = AnimeUpdatesListGlanceWidget()
}
