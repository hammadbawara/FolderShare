package com.hz_apps.foldershare.core.transfer

/**
 * JVM actual implementation of FileTransferController.
 * On JVM / Desktop Linux, transfers run within application coroutine scope.
 */
class JvmFileTransferController : FileTransferController {
    override fun startTransferService() {
        println("[JvmFileTransferController] Background transfer started")
    }

    override fun stopTransferService() {
        println("[JvmFileTransferController] Background transfer stopped")
    }
}

actual fun createFileTransferController(): FileTransferController = JvmFileTransferController()
