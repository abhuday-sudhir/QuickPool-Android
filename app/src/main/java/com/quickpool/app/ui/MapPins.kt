package com.quickpool.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Uber-style pinpoint markers: compact geometric marks anchored at their centre,
 * rather than teardrop pins. A square marks a fixed endpoint (the destination);
 * a dot marks a live position (you).
 *
 * All builders return null if the Maps SDK has not initialised yet — callers hand
 * that straight to Marker(icon = …), which falls back to the default marker instead
 * of throwing "IBitmapDescriptorFactory is not initialized".
 */

private const val SIZE_PX = 56
private const val RING_PX = 6f

/** Rounded square — used for the destination. */
fun squareMarker(fill: Color, ring: Color = Color.White): BitmapDescriptor? = runCatching {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = RING_PX
    val rect = RectF(inset, inset, SIZE_PX - inset, SIZE_PX - inset)
    val radius = (SIZE_PX - 2 * inset) * 0.28f

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = RING_PX
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, radius, radius, fillPaint)
    canvas.drawRoundRect(rect, radius, radius, ringPaint)
    BitmapDescriptorFactory.fromBitmap(bitmap)
}.getOrNull()

/** Filled dot with a ring — used for the user's own position. */
fun dotMarker(fill: Color, ring: Color = Color.White): BitmapDescriptor? = runCatching {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = SIZE_PX / 2f
    val radius = centre - RING_PX

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = RING_PX
    }
    canvas.drawCircle(centre, centre, radius, fillPaint)
    canvas.drawCircle(centre, centre, radius, ringPaint)
    BitmapDescriptorFactory.fromBitmap(bitmap)
}.getOrNull()
