package com.hz_apps.foldershare.core.explorer.util

/**
 * Parses WebDAV HTTP date string (e.g. RFC 1123, ISO 8601) into epoch millisecond timestamp.
 */
expect fun parseHttpDateToTimestamp(dateStr: String): Long
