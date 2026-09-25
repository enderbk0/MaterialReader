package com.enderbk.materialreader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Human-readable file size, e.g. "2.4 MB". Pure function, unit-tested. */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp.toDouble())
    val text = if (value >= 100) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    return "$text ${units[exp - 1]}"
}

/** Short date for "last opened" labels. Pure function, unit-tested. */
fun formatLastOpened(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Never opened"
    val date = Date(epochMillis)
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
}

/** "Page X of Y" label used by the reader indicator. */
fun formatPageIndicator(currentOneBased: Int, total: Int): String = "$currentOneBased / $total"
