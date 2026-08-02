package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.ui.model.NavTab
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Nav bar preferences got new install-time defaults. This runs only on upgrades (fresh installs use
 * the initial strategy, which skips non-"always" migrations), so it pins the previous defaults for
 * users who never changed a setting — keeping their layout while new installs get the new defaults.
 * It also converts the old single-choice nav style into the new multi-select set of bottom-bar tabs.
 */
class BottomNavTabsMigration : Migration {
    override val version = 137f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (!prefs.contains("bottom_nav_tabs")) {
            val navTabs = when (prefs.getString("bottom_rail_nav_style", null)) {
                "MOVE_MANGA_TO_MORE" ->
                    setOf(NavTab.Anime.prefKey, NavTab.Updates.prefKey, NavTab.History.prefKey, NavTab.Browse.prefKey)
                "MOVE_UPDATES_TO_MORE" ->
                    setOf(NavTab.Anime.prefKey, NavTab.Manga.prefKey, NavTab.History.prefKey, NavTab.Browse.prefKey)
                "MOVE_BROWSE_TO_MORE" ->
                    setOf(NavTab.Anime.prefKey, NavTab.Manga.prefKey, NavTab.Updates.prefKey, NavTab.History.prefKey)
                // "MOVE_HISTORY_TO_MORE" and users who never changed it: the previous default.
                else ->
                    setOf(NavTab.Anime.prefKey, NavTab.Manga.prefKey, NavTab.Updates.prefKey, NavTab.Browse.prefKey)
            }
            preferenceStore.getStringSet("bottom_nav_tabs", NavTab.DEFAULT).set(navTabs)
        }

        // Pin the previous nav bar defaults (solid bar, full opacity, labels shown).
        if (!prefs.contains("pref_bottom_nav_floating")) {
            preferenceStore.getBoolean("pref_bottom_nav_floating", false).set(false)
        }
        if (!prefs.contains("pref_bottom_nav_floating_alpha")) {
            preferenceStore.getInt("pref_bottom_nav_floating_alpha", 85).set(85)
        }
        if (!prefs.contains("pref_bottom_nav_hide_labels")) {
            preferenceStore.getBoolean("pref_bottom_nav_hide_labels", false).set(false)
        }

        prefs.edit { remove("bottom_rail_nav_style") }
        return true
    }
}
