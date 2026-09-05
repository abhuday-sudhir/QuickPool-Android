package com.quickpool.app

import com.google.android.gms.maps.model.LatLng
import com.quickpool.app.ui.SnappedRoute
import com.quickpool.app.ui.SpeedTracker
import com.quickpool.app.ui.routeStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackingTest {

    // A due-east leg near Agra: three points about 1 km apart.
    private val route = SnappedRoute(
        listOf(
            LatLng(27.1767, 78.0000),
            LatLng(27.1767, 78.0101),
            LatLng(27.1767, 78.0202)
        )
    )

    @Test
    fun `total length is the sum of the segments`() {
        assertEquals(2000.0, route.totalMeters, 40.0)
    }

    @Test
    fun `a fix on the route snaps to itself and reports no deviation`() {
        val p = route.snap(LatLng(27.1767, 78.0101))!!
        assertEquals(0.0, p.offRouteMeters, 1.0)
        assertEquals(1000.0, p.remainingMeters, 40.0)
    }

    @Test
    fun `a fix beside the route snaps onto it and reports the perpendicular distance`() {
        // ~0.0009 degrees of latitude is roughly 100 m north of the line.
        val p = route.snap(LatLng(27.1776, 78.0101))!!
        assertEquals(100.0, p.offRouteMeters, 10.0)
        assertEquals(27.1767, p.snapped.latitude, 1e-4)
        assertEquals(78.0101, p.snapped.longitude, 1e-4)
    }

    @Test
    fun `progress splits the polyline into driven and remaining halves`() {
        val p = route.snap(LatLng(27.1767, 78.0101))!!
        assertTrue("traveled should reach the snap point", p.traveled.size >= 2)
        assertTrue("remaining should reach the destination", p.remaining.size >= 2)
        assertEquals(78.0202, p.remaining.last().longitude, 1e-4)
        assertEquals(78.0000, p.traveled.first().longitude, 1e-4)
    }

    @Test
    fun `remaining distance falls as the vehicle advances`() {
        val start = route.snap(LatLng(27.1767, 78.0000))!!.remainingMeters
        val middle = route.snap(LatLng(27.1767, 78.0101))!!.remainingMeters
        val end = route.snap(LatLng(27.1767, 78.0202))!!.remainingMeters
        assertTrue(start > middle && middle > end)
        assertEquals(0.0, end, 5.0)
    }

    @Test
    fun `a route of fewer than two points cannot be snapped to`() {
        assertNull(SnappedRoute(emptyList()).snap(LatLng(27.0, 78.0)))
        assertNull(SnappedRoute(listOf(LatLng(27.0, 78.0))).snap(LatLng(27.0, 78.0)))
    }

    @Test
    fun `speed needs two fixes and ignores a stationary vehicle`() {
        val tracker = SpeedTracker()
        tracker.record(0L, LatLng(27.1767, 78.0000))
        assertNull(tracker.metresPerSecond())
        // Same spot 4s later: parked, not a zero-length ETA.
        tracker.record(4_000L, LatLng(27.1767, 78.0000))
        assertNull(tracker.metresPerSecond())
    }

    @Test
    fun `speed is averaged across the window`() {
        val tracker = SpeedTracker()
        // ~1 km east over 100 s is about 10 m per second.
        tracker.record(0L, LatLng(27.1767, 78.0000))
        tracker.record(100_000L, LatLng(27.1767, 78.0101))
        val speed = tracker.metresPerSecond()
        assertNotNull(speed)
        assertEquals(10.0, speed!!, 1.0)
    }

    @Test
    fun `status text falls back to distance alone without a speed`() {
        assertEquals("1.5 km", routeStatusText(1500.0, null))
        assertEquals("400 m", routeStatusText(400.0, null))
    }

    @Test
    fun `status text adds an eta once a speed is known`() {
        // 1200 m at 10 m per second is 120 s, which rounds to 2 min.
        assertEquals("1.2 km • 2 min", routeStatusText(1200.0, 10.0))
        assertEquals("100 m • under a minute", routeStatusText(100.0, 10.0))
        // 40 km at 10 m per second is 4000 s, or 1 h 7 min.
        assertEquals("40.0 km • 1 h 7 min", routeStatusText(40_000.0, 10.0))
    }
}
