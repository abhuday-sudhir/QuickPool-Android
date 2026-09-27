package com.quickpool.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
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

/**
 * A small person silhouette carrying a seat number badge — used for every other confirmed
 * passenger on the driver's map (a passenger's own screen never shows this: they only ever
 * see themselves and the vehicle, see PRODUCTION_TASKS.md 5.3).
 */
fun personMarker(number: Int, fill: Color, ring: Color = Color.White): BitmapDescriptor? = runCatching {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = SIZE_PX / 2f
    val bgRadius = centre - RING_PX / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centre, centre, bgRadius, bgPaint)

    val personPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    // Head + shoulders, a simple silhouette rather than a literal figure.
    canvas.drawCircle(centre, SIZE_PX * 0.34f, SIZE_PX * 0.14f, personPaint)
    val shoulders = Path().apply {
        moveTo(SIZE_PX * 0.26f, SIZE_PX * 0.80f)
        quadTo(SIZE_PX * 0.20f, SIZE_PX * 0.48f, centre, SIZE_PX * 0.46f)
        quadTo(SIZE_PX * 0.80f, SIZE_PX * 0.48f, SIZE_PX * 0.74f, SIZE_PX * 0.80f)
        close()
    }
    canvas.drawPath(shoulders, personPaint)

    // Badge behind the number, so it reads clearly over the dark silhouette.
    val badgeRadius = SIZE_PX * 0.20f
    val badgeCentreY = SIZE_PX * 0.68f
    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centre, badgeCentreY, badgeRadius, badgePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        textSize = SIZE_PX * 0.24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val label = number.toString()
    val textY = badgeCentreY - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, centre, textY, textPaint)

    BitmapDescriptorFactory.fromBitmap(bitmap)
}.getOrNull()

/** Simple car silhouette — used for the vehicle marker instead of a plain pin/dot. */
fun carMarker(fill: Color, ring: Color = Color.White): BitmapDescriptor? = runCatching {
    val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = SIZE_PX / 2f
    val bgRadius = centre - RING_PX / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ring.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centre, centre, bgRadius, bgPaint)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    // Cabin + body, roughly car-shaped, facing "up" on the bitmap.
    val bodyRect = RectF(SIZE_PX * 0.20f, SIZE_PX * 0.40f, SIZE_PX * 0.80f, SIZE_PX * 0.72f)
    canvas.drawRoundRect(bodyRect, SIZE_PX * 0.10f, SIZE_PX * 0.10f, bodyPaint)
    val cabinRect = RectF(SIZE_PX * 0.32f, SIZE_PX * 0.26f, SIZE_PX * 0.68f, SIZE_PX * 0.46f)
    canvas.drawRoundRect(cabinRect, SIZE_PX * 0.08f, SIZE_PX * 0.08f, bodyPaint)

    val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Same as the body, not the ring — wheels drawn in the ring color were invisible
        // against the ring-colored background circle.
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    val wheelRadius = SIZE_PX * 0.07f
    canvas.drawCircle(SIZE_PX * 0.28f, SIZE_PX * 0.70f, wheelRadius, wheelPaint)
    canvas.drawCircle(SIZE_PX * 0.72f, SIZE_PX * 0.70f, wheelRadius, wheelPaint)
    canvas.drawCircle(SIZE_PX * 0.28f, SIZE_PX * 0.44f, wheelRadius * 0.7f, wheelPaint)
    canvas.drawCircle(SIZE_PX * 0.72f, SIZE_PX * 0.44f, wheelRadius * 0.7f, wheelPaint)

    BitmapDescriptorFactory.fromBitmap(bitmap)
}.getOrNull()
