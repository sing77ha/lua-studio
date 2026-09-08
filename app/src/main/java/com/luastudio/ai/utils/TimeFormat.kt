package com.luastudio.ai.utils

import java.util.concurrent.TimeUnit

/** Formats an epoch-millis timestamp as a short relative string, e.g. "5m ago", "3d ago". */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return "—"
    val diff = (nowMillis - epochMillis).coerceAtLeast(0L)
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 30}mo ago"
    }
}
