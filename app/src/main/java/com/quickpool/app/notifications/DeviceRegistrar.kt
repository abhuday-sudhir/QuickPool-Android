package com.quickpool.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.quickpool.app.data.TokenHolder
import com.quickpool.app.network.ApiClient
import com.quickpool.app.network.RegisterDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the backend's copy of this device's FCM token in step with who is signed in.
 *
 * The two events are independent — FCM issues a token whenever it feels like it, and the user
 * signs in whenever they feel like it — so the token is cached locally and pushed to the
 * backend whenever both are true. Registration is idempotent on the server, so calling this
 * more often than strictly necessary is cheap and much safer than calling it too rarely.
 */
object DeviceRegistrar {

    private const val PREFS = "quickpool_push"
    private const val KEY_TOKEN = "fcm_token"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cacheToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun cachedToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    /**
     * Call after login and on every cold start. Fetches the current token if none is cached
     * yet — on a fresh install onNewToken may already have fired before the user signed in,
     * or may not have fired at all.
     */
    fun registerIfSignedIn(context: Context) {
        if (TokenHolder.accessToken == null) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val token = cachedToken(context)
                    ?: FirebaseMessaging.getInstance().token.await().also { cacheToken(context, it) }
                ApiClient.deviceApi.register(RegisterDeviceRequest(token))
            }.onFailure {
                // Never fatal: push is an enhancement, the Alerts inbox still works without it.
                Log.w("DeviceRegistrar", "Could not register device token: ${it.message}")
            }
        }
    }

    /**
     * Call on logout, before the auth token is cleared — the request needs it. Otherwise the
     * next person to sign in on this device keeps receiving the previous user's pushes.
     */
    suspend fun unregister(context: Context) {
        val token = cachedToken(context) ?: return
        runCatching { ApiClient.deviceApi.unregister(RegisterDeviceRequest(token)) }
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }
}
