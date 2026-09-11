package com.hz_apps.foldershare.core.explorer.model

import com.hz_apps.foldershare.core.server.formatHttpDate
import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Domain model representing a remote target device that can be explored.
 */
@Serializable
data class RemoteTargetDevice(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val isHttps: Boolean = false
)

/**
 * Domain model representing a remote file or folder retrieved via WebDAV/HTTP.
 */
data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModifiedTimestamp: Long = 0L,
    val isWriteAllowed: Boolean = true
) {
    val lastModified: String
        get() = if (lastModifiedTimestamp <= 0L) "" else formatHttpDate(lastModifiedTimestamp)
    val extension: String
        get() = if (isDirectory || !name.contains('.')) "" else name.substringAfterLast('.').lowercase()

    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
            val coercedGroup = digitGroups.coerceIn(0, units.lastIndex)
            val value = size / 1024.0.pow(coercedGroup.toDouble())
            return if (coercedGroup == 0) "$size B" else "${(value * 10).roundToInt() / 10.0} ${units[coercedGroup]}"
        }
}
