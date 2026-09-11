package com.hz_apps.foldershare.core.transfer

enum class TransferDirection {
    DOWNLOAD,
    UPLOAD
}

enum class TransferStatus {
    IN_PROGRESS,
    RECONNECTING,
    PAUSED_ERROR,
    COMPLETED
}

data class FileTransferProgress(
    val fileName: String,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long? = null,
    val progress: Float? = null,
    val direction: TransferDirection = TransferDirection.DOWNLOAD,
    val status: TransferStatus = TransferStatus.IN_PROGRESS,
    val errorMessage: String? = null,
    val isAutoRetrying: Boolean = false,
    val currentFileIndex: Int = 1,
    val totalFilesCount: Int = 1
)

typealias FileSaveProgress = FileTransferProgress

