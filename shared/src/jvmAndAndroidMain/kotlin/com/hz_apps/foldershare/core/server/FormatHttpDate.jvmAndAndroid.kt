package com.hz_apps.foldershare.core.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual fun formatHttpDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("GMT")
    return sdf.format(Date(timestamp))
}
