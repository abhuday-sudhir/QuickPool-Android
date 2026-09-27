package com.quickpool.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.quickpool.app.MainActivity
import com.quickpool.app.R

class QuickPoolMessagingService : FirebaseMessagingService() {

    /**
     * Fired when FCM issues or rotates this device's token — which happens on install, on
     * app data being cleared, and occasionally on its own. The user may not be signed in at
     * this point, so the token is cached and DeviceRegistrar re-sends it after login.
     */
    override fun onNewToken(token: String) {
        DeviceRegistrar.cacheToken(applicationContext, token)
        DeviceRegistrar.registerIfSignedIn(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureNotificationChannels(applicationContext)

        val title = message.notification?.title ?: message.data["title"] ?: "QuickPool"
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        // Deep-link payload from FcmSender. Carried through to MainActivity so a tap can
        // eventually land on the booking rather than just opening Home.
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notificationType", message.data["type"])
            putExtra("entityId", message.data["entityId"])
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, RIDES_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Posting without the runtime permission throws on API 33+; the push is simply
        // dropped instead, which matches what the system would do anyway.
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext)
                .notify(message.messageId?.hashCode() ?: 0, notification)
        }
    }
}
