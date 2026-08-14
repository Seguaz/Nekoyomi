package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppIcon
import eu.kanade.domain.ui.model.HomeTabsMode
import eu.kanade.domain.ui.model.NavBarLabelMode
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.screen.appearance.NavBarOrderScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.AppIconManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import kotlin.math.roundToInt

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
            getCoverBackdropGroup(uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getCoverBackdropGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_cover_backdrop),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.pref_category_cover_backdrop),
                ) {
                    CoverBackdropPreference(uiPreferences = uiPreferences)
                },
            ),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsState()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            onItemClick = { appThemePref.set(it) },
                        )
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        val floatingNavBar by uiPreferences.bottomNavFloating().collectAsState()
        val floatingNavBarAlphaPref = uiPreferences.bottomNavFloatingAlpha()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode(),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.startScreen(),
                    entries = StartScreen.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_start_screen),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.homeTabsMode(),
                    entries = persistentMapOf(
                        HomeTabsMode.ANIME_FIRST to stringResource(AYMR.strings.pref_home_tabs_both_anime),
                        HomeTabsMode.MANGA_FIRST to stringResource(AYMR.strings.pref_home_tabs_both_manga),
                        HomeTabsMode.ANIME_ONLY to stringResource(AYMR.strings.pref_home_tabs_anime_only),
                        HomeTabsMode.MANGA_ONLY to stringResource(AYMR.strings.pref_home_tabs_manga_only),
                    ),
                    title = stringResource(AYMR.strings.pref_home_tabs_mode),
                    subtitle = stringResource(AYMR.strings.pref_home_tabs_mode_summary),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_navigation_style),
                    subtitle = stringResource(AYMR.strings.pref_navigation_style_summary),
                    onClick = { navigator.push(NavBarOrderScreen()) },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.bottomNavFloating(),
                    title = stringResource(AYMR.strings.pref_floating_nav_bar),
                    subtitle = stringResource(AYMR.strings.pref_floating_nav_bar_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.pref_floating_nav_bar_opacity),
                ) {
                    FloatingNavBarPreference(
                        opacityPreference = floatingNavBarAlphaPref,
                        blurPreference = uiPreferences.bottomNavFloatingBlur(),
                        heightPreference = uiPreferences.bottomNavFloatingHeight(),
                        scalePreference = uiPreferences.bottomNavIconScale(),
                        floatingEnabled = floatingNavBar,
                        opacityTitle = stringResource(AYMR.strings.pref_floating_nav_bar_opacity),
                        blurTitle = stringResource(AYMR.strings.pref_floating_nav_bar_blur),
                        heightTitle = stringResource(AYMR.strings.pref_floating_nav_bar_height),
                        sizeTitle = stringResource(AYMR.strings.pref_nav_bar_icon_size),
                    )
                },
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.bottomNavLabelMode(),
                    entries = persistentMapOf(
                        NavBarLabelMode.HIDDEN to stringResource(AYMR.strings.pref_nav_bar_labels_hidden),
                        NavBarLabelMode.BESIDE to stringResource(AYMR.strings.pref_nav_bar_labels_beside),
                        NavBarLabelMode.BELOW to stringResource(AYMR.strings.pref_nav_bar_labels_below),
                    ),
                    title = stringResource(AYMR.strings.pref_nav_bar_labels),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showDownloadSize(),
                    title = stringResource(AYMR.strings.pref_show_download_size),
                    subtitle = stringResource(AYMR.strings.pref_show_download_size_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.pref_app_icon),
                ) {
                    AppIconPreference(
                        preference = uiPreferences.appIcon(),
                        title = stringResource(AYMR.strings.pref_app_icon),
                        onIconSelected = { icon, restartNow ->
                            // Only save the choice here; App.onCreate applies the alias on the next
                            // start. Applying it now would both close the app (the alias the current
                            // task launched from gets disabled) and leave getLaunchIntentForPackage
                            // momentarily null, so the relaunch below wouldn't fire.
                            uiPreferences.appIcon().set(icon)
                            if (restartNow) {
                                // Switch the launcher alias now. This is the original mechanism that
                                // actually changes the icon; on some launchers (Samsung) the app
                                // closes when its alias changes, and App.onCreate re-applies the
                                // saved icon on the next start regardless.
                                AppIconManager.apply(context, icon)
                            } else {
                                context.toast(MR.strings.requires_app_restart)
                            }
                        },
                    )
                },
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat(),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_date_format),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)

@Composable
private fun FloatingNavBarPreference(
    opacityPreference: tachiyomi.core.common.preference.Preference<Int>,
    blurPreference: tachiyomi.core.common.preference.Preference<Int>,
    heightPreference: tachiyomi.core.common.preference.Preference<Int>,
    scalePreference: tachiyomi.core.common.preference.Preference<Int>,
    floatingEnabled: Boolean,
    opacityTitle: String,
    blurTitle: String,
    heightTitle: String,
    sizeTitle: String,
) {
    val savedOpacity by opacityPreference.collectAsState()
    val savedBlur by blurPreference.collectAsState()
    val savedHeight by heightPreference.collectAsState()
    val savedScale by scalePreference.collectAsState()
    // Follow the drag live for the preview; persist only when the user lets go.
    var opacity by remember(savedOpacity) { mutableFloatStateOf(savedOpacity.toFloat()) }
    var blur by remember(savedBlur) { mutableFloatStateOf(savedBlur.toFloat()) }
    var height by remember(savedHeight) { mutableFloatStateOf(savedHeight.toFloat()) }
    var scale by remember(savedScale) { mutableFloatStateOf(savedScale.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        NavBarOpacityPreview(
            alpha = opacity / 100f,
            blur = blur.dp,
            iconScale = scale / 100f,
            heightScale = height / 100f,
        )

        Spacer(Modifier.height(16.dp))

        BackdropSliderRow(
            title = opacityTitle,
            value = opacity,
            valueRange = 0f..100f,
            valueLabel = "${opacity.roundToInt()}%",
            enabled = floatingEnabled,
            onValueChange = { opacity = it },
            onValueChangeFinished = { opacityPreference.set(opacity.roundToInt()) },
        )
        BackdropSliderRow(
            title = blurTitle,
            value = blur,
            valueRange = 0f..30f,
            valueLabel = if (blur.roundToInt() == 0) {
                stringResource(MR.strings.off)
            } else {
                "${blur.roundToInt()} dp"
            },
            enabled = floatingEnabled,
            onValueChange = { blur = it },
            onValueChangeFinished = { blurPreference.set(blur.roundToInt()) },
        )
        BackdropSliderRow(
            title = heightTitle,
            value = height,
            valueRange = 70f..130f,
            valueLabel = "${height.roundToInt()}%",
            enabled = floatingEnabled,
            onValueChange = { height = it },
            onValueChangeFinished = { heightPreference.set(height.roundToInt()) },
        )
        BackdropSliderRow(
            title = sizeTitle,
            value = scale,
            valueRange = 60f..140f,
            valueLabel = "${scale.roundToInt()}%",
            onValueChange = { scale = it },
            onValueChangeFinished = { scalePreference.set(scale.roundToInt()) },
        )
    }
}

/** Cover backdrop settings: a live preview plus opacity/blur/darkening sliders. */
@Composable
private fun CoverBackdropPreference(uiPreferences: UiPreferences) {
    val opacityPref = uiPreferences.entryBackdropOpacity()
    val blurPref = uiPreferences.entryBackdropBlur()
    val dimPref = uiPreferences.entryBackdropDim()

    val savedOpacity by opacityPref.collectAsState()
    val savedBlur by blurPref.collectAsState()
    val savedDim by dimPref.collectAsState()

    // Follow the drag live for the preview; persist only when the user lets go.
    var opacity by remember(savedOpacity) { mutableFloatStateOf(savedOpacity.toFloat()) }
    var blur by remember(savedBlur) { mutableFloatStateOf(savedBlur.toFloat()) }
    var dim by remember(savedDim) { mutableFloatStateOf(savedDim.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        CoverBackdropPreview(
            opacity = opacity / 100f,
            blur = blur.roundToInt().dp,
            dim = dim / 100f,
        )

        Spacer(Modifier.height(16.dp))

        BackdropSliderRow(
            title = stringResource(AYMR.strings.pref_entry_backdrop_opacity),
            value = opacity,
            valueRange = 0f..100f,
            valueLabel = "${opacity.roundToInt()}%",
            onValueChange = { opacity = it },
            onValueChangeFinished = { opacityPref.set(opacity.roundToInt()) },
        )
        BackdropSliderRow(
            title = stringResource(AYMR.strings.pref_entry_backdrop_blur),
            value = blur,
            valueRange = 0f..40f,
            valueLabel = "${blur.roundToInt()} dp",
            onValueChange = { blur = it },
            onValueChangeFinished = { blurPref.set(blur.roundToInt()) },
        )
        BackdropSliderRow(
            title = stringResource(AYMR.strings.pref_entry_backdrop_dim),
            value = dim,
            valueRange = 0f..100f,
            valueLabel = "${dim.roundToInt()}%",
            onValueChange = { dim = it },
            onValueChangeFinished = { dimPref.set(dim.roundToInt()) },
        )
    }
}

@Composable
private fun CoverBackdropPreview(opacity: Float, blur: Dp, dim: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Faux blurred cover backdrop (the app icon stands in for a cover).
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(blur)
                .alpha(opacity),
        )
        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }
        // Faux header content so the effect reads in context.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .fillMaxWidth(0.6f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .fillMaxWidth(0.4f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun BackdropSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true,
) {
    val accent = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 20.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        },
    )
}

/** A small live preview of the floating nav bar over faux content, so its opacity, blur, size + height read. */
@Composable
private fun NavBarOpacityPreview(alpha: Float, blur: Dp, iconScale: Float, heightScale: Float) {
    val pillShape = RoundedCornerShape(24.dp)
    val hazeState = remember { HazeState() }
    val blurActive = blur > 0.dp
    // Colors captured here; the Haze effect block below is not composable.
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val solidTint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha * 0.5f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
    val tileColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        // Opaque backdrop + faux library tiles, marked as the blur source so the frosted pill always
        // has solid content to sample (a transparent source would make the pill vanish).
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (blurActive) Modifier.hazeSource(hazeState) else Modifier)
                .background(surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(tileColor),
                    )
                }
            }
        }

        // The floating pill: frosted when blur is on (samples the backdrop above), else translucent.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .height(48.dp * heightScale)
                .clip(pillShape)
                .then(
                    if (blurActive) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurRadius = blur
                            backgroundColor = surfaceVariant
                            tints = listOf(HazeTint(glassTint))
                            fallbackTint = HazeTint(solidTint)
                            noiseFactor = 0f
                        }
                    } else {
                        Modifier.background(color = solidTint)
                    },
                )
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = pillShape,
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Icons.Outlined.Movie,
                Icons.Outlined.CollectionsBookmark,
                Icons.Outlined.NewReleases,
                Icons.Outlined.Explore,
                Icons.Outlined.MoreHoriz,
            ).forEach { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp * iconScale),
                )
            }
        }
    }
}

@Composable
private fun AppIconPreference(
    preference: tachiyomi.core.common.preference.Preference<AppIcon>,
    title: String,
    onIconSelected: (AppIcon, restartNow: Boolean) -> Unit,
) {
    val selected by preference.collectAsState()
    var pendingIcon by remember { mutableStateOf<AppIcon?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                AppIconItem(
                    icon = icon,
                    selected = icon == selected,
                    onClick = { pendingIcon = icon },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(AYMR.strings.app_icon_credits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(AYMR.strings.app_icon_ai_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SECONDARY_ALPHA),
        )
    }

    pendingIcon?.let { icon ->
        AlertDialog(
            onDismissRequest = { pendingIcon = null },
            title = { Text(text = stringResource(icon.titleRes)) },
            text = { Text(text = stringResource(AYMR.strings.app_icon_restart_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIconSelected(icon, true)
                        pendingIcon = null
                    },
                ) {
                    Text(text = stringResource(AYMR.strings.action_restart_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onIconSelected(icon, false)
                        pendingIcon = null
                    },
                ) {
                    Text(text = stringResource(AYMR.strings.action_restart_later))
                }
            },
        )
    }
}

@Composable
private fun AppIconItem(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = icon.iconRes,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(icon.titleRes),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
