package com.quickpool.app.network

import com.google.gson.Gson
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import io.reactivex.disposables.CompositeDisposable

data class LocationUpdatePayload(val lat: Double, val lng: Double)
data class LocationBroadcast(val userId: String, val role: String, val lat: Double, val lng: Double, val timestamp: Long)

class StompManager(private val rideId: String, private val token: String) {

    private var stompClient: StompClient? = null
    private val disposables = CompositeDisposable()
    private val gson = Gson()

    fun connect(onLocationReceived: (LocationBroadcast) -> Unit) {
        // localhost works via `adb reverse tcp:8080 tcp:8080`; swap to LAN IP for Wi-Fi testing.
        val url = "ws://localhost:8080/ws?token=$token"
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url)

        disposables.add(
            stompClient!!.lifecycle().subscribe { _ ->
                // Optional: log connection state changes here for debugging
            }
        )

        stompClient!!.connect()

        disposables.add(
            stompClient!!.topic("/topic/ride/$rideId/location").subscribe { message ->
                val broadcast = gson.fromJson(message.payload, LocationBroadcast::class.java)
                onLocationReceived(broadcast)
            }
        )
    }

    fun sendLocation(lat: Double, lng: Double) {
        val payload = gson.toJson(LocationUpdatePayload(lat, lng))
        stompClient?.send("/app/ride/$rideId/location", payload)?.subscribe()
    }

    fun disconnect() {
        disposables.dispose()
        stompClient?.disconnect()
    }
}