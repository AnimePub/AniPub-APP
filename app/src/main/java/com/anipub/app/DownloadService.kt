package com.animepub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that keeps downloads running when the app is in the background.
 **/
class DownloadService : Service() {

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42

        /** episode name, percent for every download that is still running */
        private val running = ConcurrentHashMap<String, Pair<String, Int>>()

        @Volatile private var instance: DownloadService? = null

        fun start(ctx: Context) {
            createChannel(ctx)
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }

        /** Called with every progress JSON the Downloader emits. */
        fun report(ctx: Context, json: String) {
            val o = try { JSONObject(json) } catch (e: Exception) { return }
            val key = o.optString("key")
            val name = o.optString("name")
            when (o.optString("status")) {
                "running" -> running[key] = Pair(name, o.optInt("pct"))
                "done" -> { running.remove(key); postResult(ctx, key, true, name) }
                "error" -> { running.remove(key); postResult(ctx, key, false, name) }
                else -> running.remove(key)            // cancelled
            }
            if (running.isEmpty()) {
                // Remove the progress notification explicitly, then stop the service.
                try { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) } catch (e: SecurityException) { }
                instance?.endForeground()
                ctx.stopService(Intent(ctx, DownloadService::class.java))
            } else {
                try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, buildProgress(ctx)) } catch (e: SecurityException) { }
            }
        }

        private fun openIntent(ctx: Context): PendingIntent =
            PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        /** Final notification for one episode: complete or failed*/
        private fun postResult(ctx: Context, key: String, ok: Boolean, name: String) {
            createChannel(ctx)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(if (ok) "Download complete" else "Download failed")
                .setContentText(name)
                .setContentIntent(openIntent(ctx))
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(ctx).notify(0x40000000 + (key.hashCode() and 0xFFFF), n) } catch (e: SecurityException) { }
        }

        private fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }

        private fun buildProgress(ctx: Context): Notification {
            val first = running.values.firstOrNull()
            val count = running.size
            val title = if (count == 1) "Downloading episode" else "Downloading $count episodes"
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText(first?.first ?: "")
                .setProgress(100, first?.second ?: 0, false)
                .setContentIntent(openIntent(ctx))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val n = buildProgress(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /** Notification Changer */
    fun endForeground() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
