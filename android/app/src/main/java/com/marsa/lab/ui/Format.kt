package com.marsa.lab.ui

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun clockText(millis: Long): String = clockFmt.format(Instant.ofEpochMilli(millis))

/** "12 dtk lalu", "3 mnt lalu", ... dari waktu ISO-8601 yang dikirim server. */
fun agoText(iso: String): String {
    val then = try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (ignored: Exception) {
        return "—"
    }
    val s = maxOf(0L, Duration.between(then, Instant.now()).seconds)
    return when {
        s < 60 -> "$s dtk lalu"
        s < 3600 -> "${s / 60} mnt lalu"
        s < 86400 -> "${s / 3600} jam lalu"
        else -> "${s / 86400} hari lalu"
    }
}

fun roleLabel(role: String): String = if (role == "controller") "Pengendali" else "Pemantau"
