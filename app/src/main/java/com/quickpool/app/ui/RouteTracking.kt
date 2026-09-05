package com.quickpool.app.ui

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Route progress computed locally against a polyline that was fetched **once**.
 *
 * Directions is billed per request, so re-routing on every GPS fix is the single
 * most expensive thing this app can do. The route between two fixed points does
 * not change; only the position on it moves. Everything here is therefore pure
 * maths over an already-fetched polyline — no network, no key, no cost.
 */
data class RouteProgress(
    /** [fix] projected onto the nearest point of the polyline. */
    val snapped: LatLng,
    /** The polyline from its start up to [snapped] — drawn as already-driven. */
    val traveled: List<LatLng>,
    /** The polyline from [snapped] to the destination. */
    val remaining: List<LatLng>,
    val remainingMeters: Double,
    /** Perpendicular distance from the raw fix to the route, in metres. */
    val offRouteMeters: Double
)

class SnappedRoute(val points: List<LatLng>) {

    /** cumulative[i] = metres travelled along the polyline to reach points[i]. */
    private val cumulative = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) {
            c[i] = c[i - 1] + SphericalUtil.computeDistanceBetween(points[i - 1], points[i])
        }
    }

    val totalMeters: Double get() = if (points.isEmpty()) 0.0 else cumulative.last()

    fun snap(fix: LatLng): RouteProgress? {
        if (points.size < 2) return null
        var bestIndex = 0
        var bestT = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestPoint = points[0]
        for (i in 0 until points.size - 1) {
            val (point, distance, t) = projectOnSegment(fix, points[i], points[i + 1])
            if (distance < bestDistance) {
                bestDistance = distance; bestIndex = i; bestT = t; bestPoint = point
            }
        }
        val segmentMeters = cumulative[bestIndex + 1] - cumulative[bestIndex]
        val alongMeters = cumulative[bestIndex] + bestT * segmentMeters
        return RouteProgress(
            snapped = bestPoint,
            traveled = points.subList(0, bestIndex + 1) + bestPoint,
            remaining = listOf(bestPoint) + points.subList(bestIndex + 1, points.size),
            remainingMeters = (totalMeters - alongMeters).coerceAtLeast(0.0),
            offRouteMeters = bestDistance
        )
    }
}

/**
 * Nearest point on segment a→b to p, as (point, metres from p, fraction along ab).
 *
 * Equirectangular projection about a's latitude. Segments of an intra-city route
 * are a few hundred metres, where the error from ignoring curvature is far below
 * GPS noise.
 */
private fun projectOnSegment(p: LatLng, a: LatLng, b: LatLng): Triple<LatLng, Double, Double> {
    val metresPerDegreeLat = 111_320.0
    val metresPerDegreeLng = metresPerDegreeLat * cos(Math.toRadians(a.latitude))
    val ax = a.longitude * metresPerDegreeLng; val ay = a.latitude * metresPerDegreeLat
    val bx = b.longitude * metresPerDegreeLng; val by = b.latitude * metresPerDegreeLat
    val px = p.longitude * metresPerDegreeLng; val py = p.latitude * metresPerDegreeLat
    val dx = bx - ax; val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0
            else min(1.0, max(0.0, ((px - ax) * dx + (py - ay) * dy) / lengthSquared))
    val sx = ax + t * dx; val sy = ay + t * dy
    return Triple(
        LatLng(sy / metresPerDegreeLat, sx / metresPerDegreeLng),
        hypot(px - sx, py - sy),
        t
    )
}

/**
 * Rolling average speed over the recent fixes, in metres per second.
 *
 * Deliberately not the per-fix instantaneous speed: at a 4s interval GPS jitter
 * alone reads as several km/h and the ETA would flicker.
 */
class SpeedTracker(private val window: Int = 5) {
    private val fixes = ArrayDeque<Pair<Long, LatLng>>()

    fun record(timestampMs: Long, position: LatLng) {
        fixes.addLast(timestampMs to position)
        while (fixes.size > window) fixes.removeFirst()
    }

    /** null until there are two fixes far enough apart to mean anything. */
    fun metresPerSecond(): Double? {
        if (fixes.size < 2) return null
        val (firstTime, firstPoint) = fixes.first()
        val (lastTime, lastPoint) = fixes.last()
        val seconds = (lastTime - firstTime) / 1000.0
        if (seconds <= 0.0) return null
        val metres = SphericalUtil.computeDistanceBetween(firstPoint, lastPoint)
        val speed = metres / seconds
        return if (speed < 0.5) null else speed   // stopped: an ETA would be infinite
    }
}

/** "3.2 km • 11 min", or distance alone while the speed is still unknown. */
fun routeStatusText(remainingMeters: Double, metresPerSecond: Double?): String {
    val distance = if (remainingMeters >= 1000) {
        String.format("%.1f km", remainingMeters / 1000)
    } else {
        "${remainingMeters.roundToInt()} m"
    }
    val speed = metresPerSecond ?: return distance
    val minutes = ((remainingMeters / speed) / 60).roundToInt()
    return when {
        minutes < 1 -> "$distance • under a minute"
        minutes < 60 -> "$distance • $minutes min"
        else -> "$distance • ${minutes / 60} h ${minutes % 60} min"
    }
}
