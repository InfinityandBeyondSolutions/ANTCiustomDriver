package com.ibs.ibs_antdrivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID_GENERAL = "general"
    private const val CHANNEL_NAME = "General"
    private const val CHANNEL_DESC = "General notifications"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID_GENERAL) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_GENERAL,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    lightColor = Color.YELLOW
                    enableVibration(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendTest(context: Context, title: String = "Notifications enabled",
                 body: String = "You’ll receive alerts from now on.") {
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notifications) // TODO: your icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1001, notif)
    }
}
