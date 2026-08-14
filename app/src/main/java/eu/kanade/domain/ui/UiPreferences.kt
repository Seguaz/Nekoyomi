package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppIcon
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.HomeTabsMode
import eu.kanade.domain.ui.model.NavTab
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.ANIME)

    fun bottomNavTabs() = preferenceStore.getStringSet("bottom_nav_tabs", NavTab.DEFAULT)

    // Which Manga/Anime sub-tabs are shown (and the default) in Updates/History/Browse.
    fun homeTabsMode() = preferenceStore.getEnum("home_tabs_mode", HomeTabsMode.ANIME_FIRST)

    // Order of the bottom nav tabs, as a comma-separated list of NavTab prefKeys. Empty = enum order.
    fun bottomNavOrder() = preferenceStore.getString("bottom_nav_order", "")

    fun bottomNavFloating() = preferenceStore.getBoolean("pref_bottom_nav_floating", true)

    fun bottomNavHideLabels() = preferenceStore.getBoolean("pref_bottom_nav_hide_labels", true)

    // Opacity of the floating nav bar, as a percentage (0 = fully translucent, 100 = solid).
    fun bottomNavFloatingAlpha() = preferenceStore.getInt("pref_bottom_nav_floating_alpha", 81)

    // Backdrop blur radius of the floating nav bar, in dp (0 = off / no blur).
    fun bottomNavFloatingBlur() = preferenceStore.getInt("pref_bottom_nav_floating_blur", 0)

    // Height of the floating nav bar, as a percentage of the default (100 = default).
    fun bottomNavFloatingHeight() = preferenceStore.getInt("pref_bottom_nav_floating_height", 100)

    // Size of the navigation bar icons, as a percentage (100 = default).
    fun bottomNavIconScale() = preferenceStore.getInt("pref_bottom_nav_icon_scale", 100)

    fun showDownloadSize() = preferenceStore.getBoolean("pref_show_download_size", false)

    fun appIcon() = preferenceStore.getEnum("app_icon", AppIcon.Default)

    // Blurred cover backdrop on the manga/anime entry screen (values as percentages / dp).
    fun entryBackdropOpacity() = preferenceStore.getInt("pref_entry_backdrop_opacity", 20)

    fun entryBackdropBlur() = preferenceStore.getInt("pref_entry_backdrop_blur", 4)

    fun entryBackdropDim() = preferenceStore.getInt("pref_entry_backdrop_dim", 0)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
