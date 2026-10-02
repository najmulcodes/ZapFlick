package com.najmulcodes.vidgrab.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.najmulcodes.vidgrab.MainActivity
import com.najmulcodes.vidgrab.R
import com.najmulcodes.vidgrab.domain.model.DownloadRow
import com.najmulcodes.vidgrab.domain.model.DownloadStatus
import com.najmulcodes.vidgrab.ui.util.summary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .build()
        manager.createNotificationChannel(channel)
    }

    /** The one ongoing notification the foreground service shows for the whole queue. */
    fun buildActive(rows: List<DownloadRow>): Notification {
        val running = rows.filter { it.item.status == DownloadStatus.RUNNING }
        val activeCount = rows.count { it.item.status.isActive }.coerceAtLeast(1)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.notification_downloading, activeCount, activeCount),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openDownloadsIntent())
            .addAction(
                0,
                context.getString(R.string.notification_action_pause_all),
                serviceIntent(DownloadService.ACTION_PAUSE_ALL, REQUEST_PAUSE_ALL),
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_cancel_all),
                serviceIntent(DownloadService.ACTION_CANCEL_ALL, REQUEST_CANCEL_ALL),
            )

        val lead = running.firstOrNull()
        if (lead == null) {
            builder.setContentText(context.getString(R.string.notification_waiting))
            builder.setProgress(0, 0, true)
        } else {
            val detail = lead.progress?.summary(context)
            builder.setContentText(listOfNotNull(lead.item.title, detail).joinToString(" • "))
            val percents = running.mapNotNull { it.progress?.percent }
            if (percents.isEmpty()) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(100, percents.average().toInt(), false)
            }
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun notifyFinished(id: Long, title: String, success: Boolean) {
        if (!manager.areNotificationsEnabled()) return
        val text = context.getString(
            if (success) R.string.notification_complete else R.string.notification_failed,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent())
            .build()
        manager.notify(FINISHED_ID_BASE + id.toInt(), notification)
    }

    private fun openDownloadsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTIVE_ID = 1
        private const val FINISHED_ID_BASE = 1000
        private const val CHANNEL_ID = "downloads"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_PAUSE_ALL = 1
        private const val REQUEST_CANCEL_ALL = 2
    }
}
