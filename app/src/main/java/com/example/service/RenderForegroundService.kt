package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class RenderForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resolution = intent?.getStringExtra("resolution") ?: "1080p"
        RenderNotificationHelper.createNotificationChannel(this)

        val resLabel = when {
            resolution.contains("4K") -> "4K"
            resolution.contains("720p") -> "720p"
            resolution.contains("1080p") -> "1080p"
            else -> "1080p"
        }

        val intentLaunch = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intentLaunch,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, "studio_render_channel")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Studio Minimal")
            .setContentText("Studio Minimal: Rendering $resLabel Video on Colab...")
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
            startForeground(RenderNotificationHelper.NOTIFICATION_ID, builder.build(), serviceType)
        } else {
            startForeground(RenderNotificationHelper.NOTIFICATION_ID, builder.build())
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
