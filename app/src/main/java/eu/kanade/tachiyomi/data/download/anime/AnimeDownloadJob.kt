package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 */
class AnimeDownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val downloadManager: AnimeDownloadManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_EPISODE_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        var networkCheck = isNetworkOk(
            applicationContext.activeNetworkState(),
            downloadPreferences.downloadOnlyOverWifi().get(),
        )
        var active = networkCheck && downloadManager.downloaderStart()

        if (!active) {
            return Result.failure()
        }

        setForegroundSafely()

        // Watch the network. On loss, PAUSE (keeping this worker alive) instead of stopping, so
        // downloads resume automatically once the connection returns — e.g. losing signal between
        // cell towers on a train no longer errors the download and forces a manual resume.
        coroutineScope {
            combineTransform(
                applicationContext.networkStateFlow(),
                downloadPreferences.downloadOnlyOverWifi().changes(),
                transform = { state, onlyWifi -> emit(networkStatusFor(state, onlyWifi)) },
            )
                .distinctUntilChanged()
                .onEach { status ->
                    networkCheck = status.ok
                    if (status.ok) {
                        // Resume any downloads paused while the network was unavailable.
                        downloadManager.downloaderStart()
                    } else {
                        downloadManager.pauseDownloadsForNetwork(status.reason)
                    }
                }
                .launchIn(this)
        }

        // Keep the worker running when needed
        while (active) {
            active = !isStopped && downloadManager.isRunning && networkCheck
        }

        return Result.success()
    }

    private data class NetworkStatus(val ok: Boolean, val reason: String)

    private fun networkStatusFor(state: NetworkState, requireWifi: Boolean): NetworkStatus {
        return when {
            !state.isOnline -> NetworkStatus(
                ok = false,
                reason = applicationContext.getString(R.string.download_notifier_no_network),
            )
            requireWifi && !state.isWifi -> NetworkStatus(
                ok = false,
                reason = applicationContext.getString(R.string.download_notifier_text_only_wifi),
            )
            else -> NetworkStatus(ok = true, reason = "")
        }
    }

    private fun isNetworkOk(state: NetworkState, requireWifi: Boolean): Boolean {
        return state.isOnline && !(requireWifi && !state.isWifi)
    }

    companion object {
        private const val TAG = "AnimeDownloader"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
