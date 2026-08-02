package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.ui.model.NavTab
import eu.kanade.domain.ui.model.StartScreen
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NavigationOptionsMigration : Migration {
    override val version = 120f

    // Bring back navigation options
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val bottomNavStyle = preferenceStore.getInt("bottom_nav_style", 0)

        val isDefaultTabManga = preferenceStore.getBoolean("default_home_tab_library", false)
        prefs.edit {
            remove("bottom_nav_style")
            remove("default_home_tab_library")

            val startScreen = if (isDefaultTabManga.get()) StartScreen.MANGA else StartScreen.ANIME
            val navTabs = when (bottomNavStyle.get()) {
                0 -> setOf(NavTab.Anime.prefKey, NavTab.Manga.prefKey, NavTab.Updates.prefKey, NavTab.Browse.prefKey)
                1 -> setOf(NavTab.Anime.prefKey, NavTab.Manga.prefKey, NavTab.History.prefKey, NavTab.Browse.prefKey)
                else -> setOf(
                    NavTab.Anime.prefKey,
                    NavTab.Updates.prefKey,
                    NavTab.History.prefKey,
                    NavTab.Browse.prefKey,
                )
            }

            preferenceStore.getEnum("start_screen", StartScreen.ANIME).set(startScreen)
            preferenceStore.getStringSet("bottom_nav_tabs", NavTab.DEFAULT).set(navTabs)
        }

        return true
    }
}
