package tachiyomi.domain.updates.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import java.time.Instant

class GetMangaUpdates(
    private val repository: MangaUpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<MangaUpdatesWithRelations> {
        return repository.awaitWithRead(read, after, limit = RECENT_UPDATES_LIMIT)
    }

    fun subscribe(instant: Instant): Flow<List<MangaUpdatesWithRelations>> {
        return repository.subscribeAllMangaUpdates(instant.toEpochMilli(), limit = RECENT_UPDATES_LIMIT)
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<MangaUpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = RECENT_UPDATES_LIMIT)
    }

    companion object {
        // The updates feed is already bounded to the last 3 months by the caller; this is just a safety
        // cap. It's high because a single library update can add hundreds of chapters in one day, and a
        // low cap (was 500) would then push the previous days' updates out of the list entirely. It's
        // also shared by the manga and novel sub-tabs (both filtered from this one query), so it must be
        // large enough that a big manga update doesn't bury the novel updates.
        private const val RECENT_UPDATES_LIMIT = 5000L
    }
}
