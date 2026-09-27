package com.quickpool.app.network

import com.google.gson.Gson
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import io.reactivex.disposables.CompositeDisposable

data class LocationUpdatePayload(val lat: Double, val lng: Double)
data class LocationBroadcast(val userId: String, val role: String, val lat: Double, val lng: Double, val timestamp: Long)

class StompManager(private val rideId: String, private val token: String) {

    private var stompClient: StompClient? = null
    // A CompositeDisposable that has been disposed once is dead forever — RxJava disposes any
    // later .add() immediately, silently, with no error. LiveLocationScreen calls connect() a
    // second time as soon as myUserId resolves (DisposableEffect(myUserId) re-keying from null),
    // which used to call disconnect() → connect() on the *same* composite: the topic subscription
    // added on that second call was disposed the instant it was added, so no broadcast — driver's
    // or any passenger's — was ever actually delivered, even though outgoing sendLocation() kept
    // working (it doesn't go through this composite). A fresh instance per connect() fixes it.
    private var disposables = CompositeDisposable()
    private val gson = Gson()

    fun connect(onLocationReceived: (LocationBroadcast) -> Unit) {
        disposables = CompositeDisposable()
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