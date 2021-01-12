package eu.kanade.tachiyomi.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class BackupCreatorJob(private val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        val backupManager = BackupManager(context)
        val uri = preferences.backupsDirectory().getOrDefault().toUri()
        val flags = BackupCreateService.BACKUP_ALL
        return try {
            backupManager.createBackup(uri, flags, true)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupCreator"

        fun setupTask(prefInterval: Int? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val interval = prefInterval ?: preferences.backupInterval().getOrDefault()
            if (interval > 0) {
                val request = PeriodicWorkRequestBuilder<BackupCreatorJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES
                )
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance().enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
            } else {
                WorkManager.getInstance().cancelAllWorkByTag(TAG)
            }
        }
    }
}
