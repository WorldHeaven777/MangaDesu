package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.Coil
import coil.request.CachePolicy
import coil.request.LoadRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationManager
import java.io.File

/**
 * Class used to show BigPictureStyle notifications
 */
class SaveImageNotifier(private val context: Context) {

    /**
     * Notification builder.
     */
    private val notificationBuilder = NotificationCompat.Builder(context, Notifications.CHANNEL_COMMON)

    /**
     * Id of the notification.
     */
    private val notificationId: Int
        get() = Notifications.ID_DOWNLOAD_IMAGE

    /**
     * Called when image download/copy is complete. This method must be called in a background
     * thread.
     *
     * @param file image file containing downloaded page image.
     */
    fun onComplete(file: File) {

        val request = LoadRequest.Builder(context).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED)
            .data(file)
            .size(720, 1280)
            .target(
                onSuccess = {
                    val bitmap = (it as BitmapDrawable).bitmap
                    if (bitmap != null) {
                        showCompleteNotification(file, bitmap)
                    } else {
                        onError(null)
                    }
                }
            ).build()
        Coil.imageLoader(context).execute(request)
    }

    private fun showCompleteNotification(file: File, image: Bitmap) {
        with(notificationBuilder) {
            setContentTitle(context.getString(R.string.picture_saved))
            setSmallIcon(R.drawable.ic_photo_24dp)
            setStyle(NotificationCompat.BigPictureStyle().bigPicture(image))
            setLargeIcon(image)
            setAutoCancel(true)
            color = ContextCompat.getColor(context, R.color.colorAccent)
            // Clear old actions if they exist
            if (mActions.isNotEmpty())
                mActions.clear()

            setContentIntent(NotificationHandler.openImagePendingActivity(context, file))
            // Share action
            addAction(
                R.drawable.ic_share_24dp,
                context.getString(R.string.share),
                NotificationReceiver.shareImagePendingBroadcast(context, file.absolutePath, notificationId)
            )
            // Delete action
            addAction(
                R.drawable.ic_delete_24dp,
                context.getString(R.string.delete),
                NotificationReceiver.deleteImagePendingBroadcast(context, file.absolutePath, notificationId)
            )

            updateNotification()
        }
    }

    /**
     * Clears the notification message.
     */
    fun onClear() {
        context.notificationManager.cancel(notificationId)
    }

    private fun updateNotification() {
        // Displays the progress bar on notification
        context.notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Called on error while downloading image.
     * @param error string containing error information.
     */
    fun onError(error: String?) {
        // Create notification
        with(notificationBuilder) {
            setContentTitle(context.getString(R.string.download_error))
            setContentText(error ?: context.getString(R.string.unknown_error))
            setSmallIcon(android.R.drawable.ic_menu_report_image)
        }
        updateNotification()
    }
}
