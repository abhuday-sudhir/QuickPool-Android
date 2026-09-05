package com.quickpool.app

import com.quickpool.app.ui.formatDeparture
import com.quickpool.app.ui.formatRelative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimeFormatTest {

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @Test
    fun `today is labelled today`() {
        val at = LocalDate.now().atTime(14, 30)
        assertEquals("Today · 2:30 PM", formatDeparture(at.format(iso)))
    }

    @Test
    fun `tomorrow is labelled tomorrow`() {
        val at = LocalDate.now().plusDays(1).atTime(8, 0)
        assertEquals("Tomorrow · 8:00 AM", formatDeparture(at.format(iso)))
    }

    @Test
    fun `further out shows the date`() {
        val at = LocalDate.now().plusDays(6).atTime(8, 0)
        val out = formatDeparture(at.format(iso))
        assertTrue("expected a weekday and date, got: $out", out.contains("·") && !out.startsWith("Today"))
    }

    @Test
    fun `unparseable input is passed through rather than crashing`() {
        assertEquals("not-a-date", formatDeparture("not-a-date"))
        assertEquals("", formatDeparture(""))
    }

    @Test
    fun `relative timestamps read naturally`() {
        val now = LocalDateTime.now()
        assertEquals("just now", formatRelative(now.format(iso)))
        assertEquals("12m ago", formatRelative(now.minusMinutes(12).format(iso)))
        assertEquals("3h ago", formatRelative(now.minusHours(3).format(iso)))
        assertEquals("5d ago", formatRelative(now.minusDays(5).format(iso)))
    }

    @Test
    fun `relative timestamps tolerate junk`() {
        assertEquals("nonsense", formatRelative("nonsense"))
    }
}
