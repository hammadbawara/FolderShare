package com.hz_apps.foldershare.core.explorer.model

/**
 * Structured Exception hierarchy for WebDAV client and repository operations.
 * Categorizes errors cleanly and defines whether an error is eligible for auto-retry.
 */
sealed class WebDavException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    abstract val isRetryable: Boolean

    /**
     * HTTP 403 Forbidden or access disabled on the remote folder.
     */
    class PermissionDenied(
        val path: String = "",
        val isReadOperation: Boolean = false,
        val serverReason: String? = null,
        message: String = serverReason ?: if (isReadOperation) {
            "Read access is disabled for this folder"
        } else {
            "Write access is disabled for this folder"
        }
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 401 Unauthorized - Invalid credentials or missing authentication.
     */
    class Unauthorized(
        message: String = "Authentication failed: invalid username or password"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 404 Not Found - Target file or directory does not exist.
     */
    class NotFound(
        val path: String = "",
        message: String = "File or folder not found"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 405 Method Not Allowed - Unsupported WebDAV method or resource already exists.
     */
    class MethodNotAllowed(
        val serverReason: String? = null,
        message: String = serverReason ?: "This action is not allowed here"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 409 Conflict - Directory structure missing or conflict.
     */
    class Conflict(
        message: String = "Parent folder does not exist"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * Request Timeout or Socket Timeout during file transfer or listing.
     */
    class Timeout(
        message: String = "Connection timed out. The device took too long to respond.",
        cause: Throwable? = null
    ) : WebDavException(message, cause) {
        override val isRetryable: Boolean = true
    }

    /**
     * General Network Failure - Host unreachable, connection dropped, socket reset.
     */
    class NetworkError(
        message: String = "Could not connect to the device. Check your Wi-Fi connection.",
        cause: Throwable? = null
    ) : WebDavException(message, cause) {
        override val isRetryable: Boolean = true
    }

    /**
     * Local storage/filesystem error during upload or download (e.g., out of space, unreadable file).
     */
    class StorageError(
        val isOutOfSpace: Boolean = false,
        message: String = if (isOutOfSpace) {
            "Not enough storage space on this device"
        } else {
            "Could not access the local file"
        },
        cause: Throwable? = null
    ) : WebDavException(message, cause) {
        override val isRetryable: Boolean = false
    }

    /**
     * Device endpoint could not be resolved on the local network.
     */
    class DeviceUnavailable(
        val deviceName: String? = null,
        message: String = "Could not find the device on your local network."
    ) : WebDavException(message) {
        override val isRetryable: Boolean = true
    }

    /**
     * HTTP 5xx Server Error.
     */
    class ServerError(
        val statusCode: Int,
        val serverReason: String? = null,
        message: String = serverReason ?: "The remote device encountered an error (HTTP $statusCode)"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = true
    }

    /**
     * Unclassified HTTP response code.
     */
    class Unknown(
        val statusCode: Int,
        val serverReason: String? = null,
        message: String = serverReason ?: "Unexpected response from device (HTTP $statusCode)"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }
}
