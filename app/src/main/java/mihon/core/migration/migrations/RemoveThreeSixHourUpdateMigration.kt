package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

class RemoveThreeSixHourUpdateMigration : Migration {
    override val version = 152f

    // Handle removed every 3 or 6 hour library updates: bump them to the new minimum (12 hours).
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false

        val updateInterval = libraryPreferences.autoUpdateInterval().get()
        if (updateInterval == 3 || updateInterval == 6) {
            libraryPreferences.autoUpdateInterval().set(12)
            MangaLibraryUpdateJob.setupTask(context, 12)
            AnimeLibraryUpdateJob.setupTask(context, 12)
        }

        return true
    }
}
