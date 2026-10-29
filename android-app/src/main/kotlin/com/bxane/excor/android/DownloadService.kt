package com.bxane.excor.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the download queue while the app is in the background, with a notification showing progress. Stops itself when
 * nothing is left to download.
 */
class DownloadService : Service() {
    private val scope = MainScope()
    private var running: Job? = null
    private var lastStart = 0

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStart = startId
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val starting = notification("Starting downloads", null, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, starting, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, starting)
        }
        // Everything here runs on the main thread, so a job added while the queue is finishing is either seen by the
        // running loop or starts a new one.
        if (running?.isActive != true) {
            running = scope.launch {
                (application as ExcorApp).downloads.runQueued { job, position, count ->
                    val done = (job.state as? Downloads.State.Fetching)?.done
                    notifications.notify(ID, notification("Downloading $position of $count", job.track.title ?: job.track.id, done))
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(lastStart)
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 and later end data-sync work after six hours a day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        running?.cancel()
        (application as ExcorApp).downloads.stopAll("Android paused downloads for today. Try again later.")
        stopSelf()
    }

    override fun onDestroy() {
        // Stopped by Android while downloading: what was left is not coming.
        if (running?.isActive == true) (application as ExcorApp).downloads.stopAll("Android stopped the download.")
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, text: String?, done: Float?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setProgress(100, ((done ?: 0f) * 100).toInt(), done == null)
            .build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val ID = 2
    }
}
