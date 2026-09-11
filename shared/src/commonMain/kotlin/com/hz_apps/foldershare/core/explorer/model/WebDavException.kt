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
     * HTTP 403 Forbidden or write access disabled on the remote folder.
     */
    class PermissionDenied(
        val path: String = "",
        message: String = "Write access is disabled for this folder"
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
     * HTTP 404 Not Found - Target resource or parent directory does not exist.
     */
    class NotFound(
        val path: String = "",
        message: String = "Resource or directory not found"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 405 Method Not Allowed - Unsupported WebDAV method on target resource.
     */
    class MethodNotAllowed(
        message: String = "Method not allowed for this resource"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * HTTP 409 Conflict - Directory structure missing or conflict.
     */
    class Conflict(
        message: String = "Conflict: Parent directory does not exist"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }

    /**
     * Request Timeout or Socket Timeout during file transfer.
     */
    class Timeout(
        message: String = "Network connection or request timed out",
        cause: Throwable? = null
    ) : WebDavException(message, cause) {
        override val isRetryable: Boolean = true
    }

    /**
     * General Network Failure - Host unreachable, connection dropped, socket reset.
     */
    class NetworkError(
        message: String = "Network connection failed",
        cause: Throwable? = null
    ) : WebDavException(message, cause) {
        override val isRetryable: Boolean = true
    }

    /**
     * HTTP 5xx Server Error.
     */
    class ServerError(
        val statusCode: Int,
        message: String = "Server internal error (HTTP $statusCode)"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = true
    }

    /**
     * Unclassified HTTP response code.
     */
    class Unknown(
        val statusCode: Int,
        message: String = "Server returned HTTP $statusCode"
    ) : WebDavException(message) {
        override val isRetryable: Boolean = false
    }
}
