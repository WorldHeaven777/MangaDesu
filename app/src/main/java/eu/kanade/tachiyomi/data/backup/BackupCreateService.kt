package eu.kanade.tachiyomi.data.backup

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isServiceRunning

/**
 * Service for backing up library information to a JSON file.
 */
class BackupCreateService : Service() {

    companion object {
        // Filter options
        internal const val BACKUP_CATEGORY = 0x1
        internal const val BACKUP_CATEGORY_MASK = 0x1
        internal const val BACKUP_CHAPTER = 0x2
        internal const val BACKUP_CHAPTER_MASK = 0x2
        internal const val BACKUP_HISTORY = 0x4
        internal const val BACKUP_HISTORY_MASK = 0x4
        internal const val BACKUP_TRACK = 0x8
        internal const val BACKUP_TRACK_MASK = 0x8
        internal const val BACKUP_ALL = 0xF

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean =
            context.isServiceRunning(BackupCreateService::class.java)

        /**
         * Make a backup from library
         *
         * @param context context of application
         * @param uri path of Uri
         * @param flags determines what to backup
         */
        fun start(context: Context, uri: Uri, flags: Int) {
            if (!isRunning(context)) {
                val intent = Intent(context, BackupCreateService::class.java).apply {
                    putExtra(BackupConst.EXTRA_URI, uri)
                    putExtra(BackupConst.EXTRA_FLAGS, flags)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
            }
        }
    }

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var backupManager: BackupManager
    private lateinit var notifier: BackupNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = BackupNotifier(this)

        startForeground(Notifications.ID_BACKUP_PROGRESS, notifier.showBackupProgress().build())

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${javaClass.name}:WakeLock"
        )
        wakeLock.acquire()
    }

    override fun stopService(name: Intent?): Boolean {
        destroyJob()
        return super.stopService(name)
    }

    override fun onDestroy() {
        destroyJob()
        super.onDestroy()
    }

    private fun destroyJob() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        try {
            val uri = intent.getParcelableExtra<Uri>(BackupConst.EXTRA_URI)
            val backupFlags = intent.getIntExtra(BackupConst.EXTRA_FLAGS, 0)
            backupManager = BackupManager(this)

            val backupFileUri = backupManager.createBackup(uri, backupFlags, false)?.toUri()
            val unifile = UniFile.fromUri(this, backupFileUri)
            notifier.showBackupComplete(unifile)
        } catch (e: Exception) {
            notifier.showBackupError(e.message)
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }
}
