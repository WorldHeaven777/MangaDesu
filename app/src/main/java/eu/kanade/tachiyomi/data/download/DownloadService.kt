package eu.kanade.tachiyomi.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo.State.CONNECTED
import android.net.NetworkInfo.State.DISCONNECTED
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.github.pwittchen.reactivenetwork.library.Connectivity
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.injectLazy

/**
 * This service is used to manage the downloader. The system can decide to stop the service, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 * While the downloader is running, a wake lock will be held.
 */
class DownloadService : Service() {

    companion object {

        /**
         * Relay used to know when the service is running.
         */
        val runningRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

        private val listeners = mutableSetOf<DownloadServiceListener>()

        fun addListener(listener: DownloadServiceListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: DownloadServiceListener) {
            listeners.remove(listener)
        }

        fun callListeners(downloading: Boolean? = null) {
            val downloadManager: DownloadManager by injectLazy()
            listeners.forEach {
                it.downloadStatusChanged(downloading ?: downloadManager.hasQueue())
            }
        }

        /**
         * Starts this service.
         *
         * @param context the application context.
         */
        fun start(context: Context) {
            callListeners()
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                context.startService(intent)
            } else {
                context.startForegroundService(intent)
            }
        }

        /**
         * Stops this service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(DownloadService::class.java)
        }
    }

    /**
     * Download manager.
     */
    private val downloadManager: DownloadManager by injectLazy()

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Wake lock to prevent the device to enter sleep mode.
     */
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DownloadService:WakeLock")
    }

    /**
     * Subscriptions to store while the service is running.
     */
    private lateinit var subscriptions: CompositeSubscription

    /**
     * Called when the service is created.
     */
    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_DOWNLOAD_CHAPTER, getPlaceholderNotification())
        downloadManager.setPlaceholder()
        runningRelay.call(true)
        subscriptions = CompositeSubscription()
        listenDownloaderState()
        listenNetworkChanges()
    }

    /**
     * Called when the service is destroyed.
     */
    override fun onDestroy() {
        runningRelay.call(false)
        subscriptions.unsubscribe()
        downloadManager.stopDownloads()
        callListeners(downloadManager.hasQueue())
        wakeLock.releaseIfNeeded()
        super.onDestroy()
    }

    /**
     * Not used.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    /**
     * Not used.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Listens to network changes.
     *
     * @see onNetworkStateChanged
     */
    private fun listenNetworkChanges() {
        subscriptions += ReactiveNetwork.observeNetworkConnectivity(applicationContext)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { state ->
                    onNetworkStateChanged(state)
                },
                {
                    toast(R.string.could_not_download_chapter_can_try_again)
                    stopSelf()
                }
            )
    }

    /**
     * Called when the network state changes.
     *
     * @param connectivity the new network state.
     */
    private fun onNetworkStateChanged(connectivity: Connectivity) {
        when (connectivity.state) {
            CONNECTED -> {
                if (preferences.downloadOnlyOverWifi() && connectivityManager.activeNetworkInfo?.type != ConnectivityManager.TYPE_WIFI) {
                    downloadManager.stopDownloads(getString(R.string.no_wifi_connection))
                } else {
                    val started = downloadManager.startDownloads()
                    if (!started) stopSelf()
                }
            }
            DISCONNECTED -> {
                downloadManager.stopDownloads(getString(R.string.no_network_connection))
            }
            else -> { /* Do nothing */ }
        }
    }

    /**
     * Listens to downloader status. Enables or disables the wake lock depending on the status.
     */
    private fun listenDownloaderState() {
        subscriptions += downloadManager.runningRelay.subscribe { running ->
            if (running)
                wakeLock.acquireIfNeeded()
            else
                wakeLock.releaseIfNeeded()
        }
    }

    /**
     * Releases the wake lock if it's held.
     */
    fun PowerManager.WakeLock.releaseIfNeeded() {
        if (isHeld) release()
    }

    /**
     * Acquires the wake lock if it's not held.
     */
    fun PowerManager.WakeLock.acquireIfNeeded() {
        if (!isHeld) acquire()
    }

    private fun getPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADER)
            .setContentTitle(getString(R.string.downloading))
            .build()
    }
}

interface DownloadServiceListener {
    fun downloadStatusChanged(downloading: Boolean)
}
