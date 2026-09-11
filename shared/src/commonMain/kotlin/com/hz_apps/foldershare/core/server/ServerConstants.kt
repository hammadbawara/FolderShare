package com.hz_apps.foldershare.core.server

/**
 * Single source of truth for WebDAV / HTTP server configuration defaults.
 */
object ServerConstants {
    const val DEFAULT_PORT: Int = 45678
    val FALLBACK_PORTS: List<Int> = listOf(
        45679,
        45680,
        45681,
        45682,
        56789,
        34568,
        34579
    )
    val ALL_APP_PORTS: List<Int> = (listOf(DEFAULT_PORT) + FALLBACK_PORTS).distinct()
    const val DEFAULT_USERNAME: String = "admin"
    const val DEFAULT_PASSWORD: String = "1234"
}
