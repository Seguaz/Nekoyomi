package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.entries.manga.model.readerOrientation
import eu.kanade.domain.entries.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.text.TextViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(screenModel: ReaderSettingsScreenModel) {
    val viewer by screenModel.viewerFlow.collectAsState()
    // Novels use a dedicated text viewer; the image reading-mode/orientation options don't apply.
    if (viewer is TextViewer) {
        TextViewerSettings(screenModel)
        return
    }

    HeadingItem(MR.strings.pref_category_for_this_series)
    val manga by screenModel.mangaFlow.collectAsState()

    val readingMode = remember(manga) {
        ReadingMode.fromPreference(
            manga?.readingMode?.toInt(),
        )
    }
    SettingsChipRow(MR.strings.pref_category_reading_mode) {
        ReadingMode.entries.map {
            FilterChip(
                selected = it == readingMode,
                onClick = { screenModel.onChangeReadingMode(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    val orientation = remember(manga) {
        ReaderOrientation.fromPreference(
            manga?.readerOrientation?.toInt(),
        )
    }
    SettingsChipRow(MR.strings.rotation_type) {
        ReaderOrientation.entries.map {
            FilterChip(
                selected = it == orientation,
                onClick = { screenModel.onChangeOrientation(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(screenModel)
    } else {
        PagerViewerSettings(screenModel)
    }
}

@Composable
private fun ColumnScope.TextViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(AYMR.strings.novel_viewer)

    val textScale by screenModel.preferences.novelTextScale().collectAsState()
    SliderItem(
        value = textScale,
        valueRange = 50..300,
        label = stringResource(AYMR.strings.pref_novel_text_size),
        valueText = numberFormat.format(textScale / 100f),
        onChange = { screenModel.preferences.novelTextScale().set(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun ColumnScope.PagerViewerSettings(screenModel: ReaderSettingsScreenModel) {
    HeadingItem(MR.strings.pager_viewer)

    val navigationModePager by screenModel.preferences.navigationModePager().collectAsState()
    val pagerNavInverted by screenModel.preferences.pagerNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = screenModel.preferences.navigationModePager()::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = screenModel.preferences.pagerNavInverted()::set,
    )

    val imageScaleType by screenModel.preferences.imageScaleType().collectAsState()
    SettingsChipRow(MR.strings.pref_image_scale_type) {
        ReaderPreferences.ImageScaleType.mapIndexed { index, it ->
            FilterChip(
                selected = imageScaleType == index + 1,
                onClick = { screenModel.preferences.imageScaleType().set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    val zoomStart by screenModel.preferences.zoomStart().collectAsState()
    SettingsChipRow(MR.strings.pref_zoom_start) {
        ReaderPreferences.ZoomStart.mapIndexed { index, it ->
            FilterChip(
                selected = zoomStart == index + 1,
                onClick = { screenModel.preferences.zoomStart().set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = screenModel.preferences.cropBorders(),
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_landscape_zoom),
        pref = screenModel.preferences.landscapeZoom(),
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_navigate_pan),
        pref = screenModel.preferences.navigateToPan(),
    )

    val dualPageSplitPaged by screenModel.preferences.dualPageSplitPaged().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = screenModel.preferences.dualPageInvertPaged(),
    )

    if (dualPageSplitPaged) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = screenModel.preferences.dualPageRotateToFitInvert(),
        )
    }

    val dualPageRotateToFit by screenModel.preferences.dualPageRotateToFit().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = screenModel.preferences.dualPageRotateToFit(),
    )

    if (dualPageRotateToFit) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = screenModel.preferences.dualPageRotateToFitInvert(),
        )
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(MR.strings.webtoon_viewer)

    val navigationModeWebtoon by screenModel.preferences.navigationModeWebtoon().collectAsState()
    val webtoonNavInverted by screenModel.preferences.webtoonNavInverted().collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = screenModel.preferences.navigationModeWebtoon()::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = screenModel.preferences.webtoonNavInverted()::set,
    )

    val webtoonSidePadding by screenModel.preferences.webtoonSidePadding().collectAsState()
    SliderItem(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        valueText = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            screenModel.preferences.webtoonSidePadding().set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val autoScrollSpeed by screenModel.preferences.webtoonAutoScrollSpeed().collectAsState()
    SliderItem(
        value = autoScrollSpeed,
        valueRange = 1..10,
        label = stringResource(AYMR.strings.pref_auto_scroll_speed),
        valueText = "$autoScrollSpeed",
        onChange = {
            screenModel.preferences.webtoonAutoScrollSpeed().set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = screenModel.preferences.cropBordersWebtoon(),
    )

    val dualPageSplitWebtoon by screenModel.preferences.dualPageSplitWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = screenModel.preferences.dualPageSplitWebtoon(),
    )

    if (dualPageSplitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = screenModel.preferences.dualPageInvertWebtoon(),
        )
    }

    val dualPageRotateToFitWebtoon by screenModel.preferences.dualPageRotateToFitWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = screenModel.preferences.dualPageRotateToFitWebtoon(),
    )

    if (dualPageRotateToFitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = screenModel.preferences.dualPageRotateToFitInvertWebtoon(),
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_double_tap_zoom),
        pref = screenModel.preferences.webtoonDoubleTapZoomEnabled(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
        pref = screenModel.preferences.webtoonDisableZoomOut(),
    )
}

@Composable
private fun ColumnScope.TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    SettingsChipRow(MR.strings.pref_viewer_nav) {
        ReaderPreferences.TapZones.mapIndexed { index, it ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    if (selected != 5) {
        SettingsChipRow(MR.strings.pref_read_with_tapping_inverted) {
            ReaderPreferences.TappingInvertMode.entries.map {
                FilterChip(
                    selected = it == invertMode,
                    onClick = { onSelectInvertMode(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }
}
