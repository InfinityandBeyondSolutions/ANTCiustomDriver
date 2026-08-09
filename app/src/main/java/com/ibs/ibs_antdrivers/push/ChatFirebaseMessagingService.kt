package com.ibs.ibs_antdrivers.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ibs.ibs_antdrivers.MainActivity
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.data.TopicSubscriber

class ChatFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let(TopicSubscriber::subscribeToMyGroups)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("notifications", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val eventType = msg.data["type"] ?: "message"
        val title = msg.notification?.title ?: msg.data["title"] ?: defaultTitle(eventType)
        val body = msg.notification?.body ?: msg.data["body"] ?: "Open the app to view it"
        val deeplink = msg.data["deeplink"] ?: "/"
        val groupId = msg.data["groupId"]
        val destination = msg.data["destination"] ?: eventType
        val channelId = if (eventType == "message" || !groupId.isNullOrBlank()) {
            CHAT_CHANNEL_ID
        } else {
            UPDATES_CHANNEL_ID
        }

        ensureChannel(channelId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("deeplink", deeplink)
            putExtra("groupId", groupId)
            putExtra("destination", destination)
        }
        val pi = PendingIntent.getActivity(
            this, (msg.messageId ?: "$eventType:$groupId:$deeplink").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun defaultTitle(eventType: String): String = when (eventType) {
        "announcements" -> "New announcement"
        "catalogues" -> "New catalogue"
        "price_lists" -> "New price list"
        else -> "New message"
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) return

        val isChat = channelId == CHAT_CHANNEL_ID
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                if (isChat) "Chat Messages" else "Content Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (isChat) {
                    "Group chat notifications"
                } else {
                    "Announcements, catalogues and price list notifications"
                }
            }
        )
    }

    companion object {
        private const val CHAT_CHANNEL_ID = "chat_messages"
        private const val UPDATES_CHANNEL_ID = "app_updates"
    }
}
