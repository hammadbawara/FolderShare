package com.hz_apps.foldershare.core.explorer.model

import java.io.InputStream

/**
 * Interface defining a local file source to be uploaded.
 * Abstracted to decouple OS/Platform specific file access (e.g. Android ContentResolver Uri vs JVM File)
 * from remote repository and ViewModel logic.
 */
interface LocalFileSource {
    val name: String
    val size: Long
    fun openStream(): InputStream
}
