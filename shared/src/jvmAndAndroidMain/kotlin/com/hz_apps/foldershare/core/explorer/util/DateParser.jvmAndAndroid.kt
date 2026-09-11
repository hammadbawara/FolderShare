package com.hz_apps.foldershare.core.explorer.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

actual fun parseHttpDateToTimestamp(dateStr: String): Long {
    if (dateStr.isBlank()) return 0L

    val patterns = arrayOf(
        "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )

    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            val date = sdf.parse(dateStr)
            if (date != null) {
                return date.time
            }
        } catch (_: Exception) {
            // Try next format
        }
    }
    return 0L
}
