package tachiyomi.source.local.entries.novel

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.manga.LocalMangaSource
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem

/**
 * A dedicated local source for text novels (epub). It reuses the whole [LocalMangaSource] machinery
 * (folder browsing, reading chapter files, epub metadata) via delegation, but points at its own
 * `localnovel` directory and — crucially — implements [NovelSource], so its entries are classified
 * as novels (they land in the Novel library / updates / history) instead of manga. The [fileSystem]
 * and [coverManager] passed in must be configured for the novel directory.
 */
class LocalNovelSource private constructor(
    context: Context,
    private val delegate: LocalMangaSource,
) : CatalogueSource by delegate, UnmeteredSource, NovelSource {

    constructor(
        context: Context,
        fileSystem: LocalMangaSourceFileSystem,
        coverManager: LocalMangaCoverManager,
    ) : this(context, LocalMangaSource(context, fileSystem, coverManager))

    override val id: Long = ID

    override val name: String = context.stringResource(AYMR.strings.local_novel_source)

    override fun toString(): String = name

    // Local novels are read straight from their epub file (see ChapterLoader -> EpubTextPageLoader),
    // never over the network, so this NovelSource hook is unused here.
    override suspend fun fetchPageText(page: Page): String = ""

    fun getFormat(chapter: SChapter): Format = delegate.getFormat(chapter)

    companion object {
        const val ID = 2L
    }
}

fun Manga.isLocalNovel(): Boolean = source == LocalNovelSource.ID

fun MangaSource.isLocalNovel(): Boolean = id == LocalNovelSource.ID
