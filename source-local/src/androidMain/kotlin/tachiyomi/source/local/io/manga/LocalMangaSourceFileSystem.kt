package tachiyomi.source.local.io.manga

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

actual class LocalMangaSourceFileSystem(
    private val storageManager: StorageManager,
    // When true this file system points at the local NOVEL directory instead of the manga one, so the
    // same machinery can back a dedicated local novel source (see LocalNovelSource).
    private val novel: Boolean = false,
) {

    actual fun getBaseDirectory(): UniFile? {
        return if (novel) {
            storageManager.getLocalNovelSourceDirectory()
        } else {
            storageManager.getLocalMangaSourceDirectory()
        }
    }

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    actual fun getMangaDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    actual fun getFilesInMangaDirectory(name: String): List<UniFile> {
        return getMangaDirectory(name)?.listFiles().orEmpty().toList()
    }
}
