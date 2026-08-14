package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.library.service.LibraryPreferences
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Stores a custom cover image per custom-series group. Files live under `series_covers/<anime|manga>`
 * and are named `<hash(seriesName)>_<timestamp>` so re-picking a cover produces a fresh path and
 * bypasses any stale Coil cache. The mapping seriesName -> fileName is kept in a preference set, each
 * element encoded as `"<fileName>|<seriesName>"`; the file name never contains '|', so splitting on
 * the FIRST '|' is unambiguous even when the series name itself contains it.
 */
class SeriesCoverCache(
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
) {

    companion object {
        private const val ANIME_DIR = "series_covers/anime"
        private const val MANGA_DIR = "series_covers/manga"
        private const val SEP = '|'
    }

    private fun dir(isAnime: Boolean): File {
        val path = if (isAnime) ANIME_DIR else MANGA_DIR
        return context.getExternalFilesDir(path)
            ?: File(context.filesDir, path).also { it.mkdirs() }
    }

    private fun pref(isAnime: Boolean) =
        if (isAnime) libraryPreferences.seriesCoversAnime() else libraryPreferences.seriesCoversManga()

    private fun fileNameFor(raw: Set<String>, name: String): String? = raw.firstNotNullOfOrNull { entry ->
        val i = entry.indexOf(SEP)
        if (i <= 0) return@firstNotNullOfOrNull null
        if (entry.substring(i + 1) == name) entry.substring(0, i) else null
    }

    /** The custom cover file for [name], or null if none is set / the file is missing. */
    fun getCoverFile(isAnime: Boolean, name: String): File? {
        val fileName = fileNameFor(pref(isAnime).get(), name) ?: return null
        return File(dir(isAnime), fileName).takeIf { it.exists() }
    }

    fun hasCover(isAnime: Boolean, name: String): Boolean = getCoverFile(isAnime, name) != null

    /** Copies [inputStream] as the custom cover for [name], replacing any previous one. */
    @Throws(IOException::class)
    fun setCover(isAnime: Boolean, name: String, inputStream: InputStream) {
        val p = pref(isAnime)
        val raw = p.get()
        // Delete the previous file for this group, if any.
        fileNameFor(raw, name)?.let { File(dir(isAnime), it).delete() }

        val newFileName = "${DiskUtil.hashKeyForDisk(name)}_${System.currentTimeMillis()}"
        File(dir(isAnime), newFileName).outputStream().use { out -> inputStream.copyTo(out) }

        p.set(raw.dropEntriesFor(name) + "$newFileName$SEP$name")
    }

    /** Moves the custom cover mapping from [oldName] to [newName] (keeps the same file). */
    fun renameSeries(isAnime: Boolean, oldName: String, newName: String) {
        val p = pref(isAnime)
        val raw = p.get()
        val fileName = fileNameFor(raw, oldName) ?: return
        p.set(raw.dropEntriesFor(oldName).dropEntriesFor(newName) + "$fileName$SEP$newName")
    }

    /** Deletes the custom cover for [name], if any. */
    fun deleteCover(isAnime: Boolean, name: String) {
        val p = pref(isAnime)
        val raw = p.get()
        val fileName = fileNameFor(raw, name) ?: return
        File(dir(isAnime), fileName).delete()
        p.set(raw.dropEntriesFor(name))
    }

    private fun Set<String>.dropEntriesFor(name: String): Set<String> = filterNot { entry ->
        val i = entry.indexOf(SEP)
        i > 0 && entry.substring(i + 1) == name
    }.toSet()
}
