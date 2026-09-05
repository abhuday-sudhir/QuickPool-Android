package com.quickpool.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class CurrentLocationProvider(private val context: Context) {

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLatLng(): LatLng? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)

        // Request a fresh fix first: lastLocation is often null on a device/emulator
        // that has no recent cached location (e.g. fresh install, no other app polling GPS).
        val fresh = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        }.getOrNull()
        if (fresh != null) return LatLng(fresh.latitude, fresh.longitude)

        val cached = runCatching { client.lastLocation.await() }.getOrNull()
        return cached?.let { LatLng(it.latitude, it.longitude) }
    }
}
