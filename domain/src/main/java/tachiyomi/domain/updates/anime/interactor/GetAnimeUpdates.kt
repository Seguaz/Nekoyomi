package tachiyomi.domain.updates.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import java.time.Instant

class GetAnimeUpdates(
    private val repository: AnimeUpdatesRepository,
) {

    suspend fun await(seen: Boolean, after: Long): List<AnimeUpdatesWithRelations> {
        return repository.awaitWithSeen(seen, after, limit = RECENT_UPDATES_LIMIT)
    }

    fun subscribe(instant: Instant): Flow<List<AnimeUpdatesWithRelations>> {
        return repository.subscribeAllAnimeUpdates(instant.toEpochMilli(), limit = RECENT_UPDATES_LIMIT)
    }

    fun subscribe(seen: Boolean, after: Long): Flow<List<AnimeUpdatesWithRelations>> {
        return repository.subscribeWithSeen(seen, after, limit = RECENT_UPDATES_LIMIT)
    }

    companion object {
        // The updates feed is already bounded to the last 3 months by the caller; this is just a safety
        // cap. It's high because a single library update can add hundreds of episodes in one day, and a
        // low cap (was 500) would then push the previous days' updates out of the list entirely.
        private const val RECENT_UPDATES_LIMIT = 5000L
    }
}
