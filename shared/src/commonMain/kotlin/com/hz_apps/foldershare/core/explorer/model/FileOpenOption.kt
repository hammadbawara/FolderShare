package com.hz_apps.foldershare.core.explorer.model

/**
 * Strategy for opening a remote file from the explorer.
 */
enum class FileOpenOption {
    /**
     * Stream or open file URL directly in external app without saving locally.
     */
    DIRECT_OPEN,

    /**
     * Download the file to a temporary directory first and then open the local file with default app.
     */
    TEMPORARY_SAVE_AND_OPEN
}
