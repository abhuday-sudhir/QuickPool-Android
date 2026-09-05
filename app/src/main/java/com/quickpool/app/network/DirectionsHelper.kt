package com.quickpool.app.network

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DirectionsResult(val points: List<LatLng>, val distanceText: String, val durationText: String)

/**
 * Routes come from our own backend, not from Google directly.
 *
 * Directions is a Web Service API, so its key cannot be restricted by Android package name
 * and signing certificate the way the Maps SDK key can — shipped in the APK it is extractable
 * and billable by anyone who pulls it. The backend holds that key instead (IP-restricted), and
 * this call travels over the authenticated Retrofit client like every other request.
 *
 * The polyline is still decoded here: it arrives in Google's encoded form, which is far smaller
 * on the wire than an expanded coordinate list.
 */
object DirectionsHelper {

    suspend fun getRoute(origin: LatLng, destination: LatLng): DirectionsResult? = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.directionsApi.route(
                originLat = origin.latitude,
                originLng = origin.longitude,
                destLat = destination.latitude,
                destLng = destination.longitude
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) return@withContext null
            DirectionsResult(decodePolyline(body.polyline), body.distanceText, body.durationText)
        } catch (e: Exception) {
            // No route is a survivable state for every caller — the map simply draws no line.
            null
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0; var lat = 0; var lng = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0; result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
