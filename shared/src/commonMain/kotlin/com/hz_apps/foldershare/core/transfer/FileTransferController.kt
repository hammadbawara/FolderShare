package com.hz_apps.foldershare.core.transfer

/**
 * Controller interface for managing background file transfer service lifecycle across Android & Linux desktop.
 * Follows the Dependency Inversion Principle so view models and managers depend on abstractions.
 */
interface FileTransferController {
    fun startTransferService()
    fun stopTransferService()
}

expect fun createFileTransferController(): FileTransferController
