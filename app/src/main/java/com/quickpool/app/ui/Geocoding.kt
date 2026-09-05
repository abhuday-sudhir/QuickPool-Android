package com.quickpool.app.ui

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Best-effort short address for a point; null-safe and never throws. */
suspend fun reverseGeocode(context: Context, point: LatLng): String =
    withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(point.latitude, point.longitude, 1)
            val address = results?.firstOrNull()
            listOfNotNull(
                address?.featureName?.takeIf { it.isNotBlank() && it != address.subAdminArea },
                address?.thoroughfare,
                address?.subLocality,
                address?.locality
            ).distinct().take(2).joinToString(", ").ifBlank { null }
        }.getOrNull() ?: "Pin at %.4f, %.4f".format(point.latitude, point.longitude)
    }
