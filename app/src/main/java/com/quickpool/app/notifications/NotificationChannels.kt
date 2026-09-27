package com.quickpool.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Channel id, duplicated in AndroidManifest.xml as the FCM default channel. */
const val RIDES_CHANNEL_ID = "quickpool_rides"

/**
 * Creating a channel twice is a no-op, so this is safe to call on every cold start —
 * which it must be, because a push can arrive before any screen has been shown.
 */
fun ensureNotificationChannels(context: Context) {
    val channel = NotificationChannel(
        RIDES_CHANNEL_ID,
        "Rides and bookings",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Booking requests, approvals, and ride updates"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
