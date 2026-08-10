package tachiyomi.presentation.widget.entries.manga

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class MangaUpdatesListGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = MangaUpdatesListGlanceWidget()
}
