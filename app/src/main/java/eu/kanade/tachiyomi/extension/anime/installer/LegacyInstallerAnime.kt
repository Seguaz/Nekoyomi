package eu.kanade.tachiyomi.extension.anime.installer

import android.app.Service
import android.content.Intent
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstallActivity
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller

/**
 * Drives the legacy `ACTION_INSTALL_PACKAGE` flow through the shared install queue so the system
 * prompts are shown one at a time. [AnimeExtensionInstallActivity] reports each result back via
 * [InstallerAnime.continueInstallQueue], which advances the queue.
 */
class LegacyInstallerAnime(private val service: Service) : InstallerAnime(service) {

    // Always ready; the actual prompt is driven by the Activity.
    override var ready = true

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        val intent = Intent(service, AnimeExtensionInstallActivity::class.java)
            .setDataAndType(entry.uri, AnimeExtensionInstaller.APK_MIME)
            .putExtra(AnimeExtensionInstaller.EXTRA_DOWNLOAD_ID, entry.downloadId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        service.startActivity(intent)
    }
}
