package com.quickpool.app.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

private fun parse(iso: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(iso) }.getOrNull()

/**
 * 12-hour clock built by hand rather than via the `a` pattern: depending on JDK and
 * CLDR version that yields "pm" in some locales and "PM" in others, which made the
 * same screen render differently on different devices.
 */
private fun timeLabel(dt: LocalDateTime): String {
    val hour12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
    val meridiem = if (dt.hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(Locale.ENGLISH, hour12, dt.minute, meridiem)
}

/** "Today · 2:30 PM" / "Tomorrow · 8:00 AM" / "Sat, Sep 6 · 8:00 AM", falling back to the raw value. */
fun formatDeparture(iso: String): String {
    val dt = parse(iso) ?: return iso
    val today = LocalDate.now()
    val day = when (dt.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> dt.format(DAY_MONTH)
    }
    return "$day · ${timeLabel(dt)}"
}

/** "just now" / "12m ago" / "3h ago" / "5d ago", for notification timestamps. */
fun formatRelative(iso: String): String {
    val dt = parse(iso) ?: return iso
    val minutes = Duration.between(dt, LocalDateTime.now()).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
