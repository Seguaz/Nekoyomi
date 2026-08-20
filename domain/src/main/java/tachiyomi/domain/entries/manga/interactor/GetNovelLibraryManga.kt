package tachiyomi.domain.entries.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import kotlin.time.Duration.Companion.seconds

class GetNovelLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getNovelLibraryManga()
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getNovelLibraryMangaAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetNovelLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }
}
