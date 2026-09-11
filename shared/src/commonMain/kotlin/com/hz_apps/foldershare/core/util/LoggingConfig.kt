package com.hz_apps.foldershare.core.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import okio.FileSystem
import okio.buffer
import okio.use

object LoggingConfig {

    private class FileLogWriter(private val fileSystem: FileSystem, private val logPath: okio.Path) : LogWriter() {
        // Basic thread-safety for JS/Native would require atomic, but for JVM/Android this is fine as appending
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            try {
                val logLine = buildString {
                    append("[${severity.name}] ")
                    append("$tag: ")
                    append(message)
                    if (throwable != null) {
                        append("\n")
                        append(throwable.stackTraceToString())
                    }
                    append("\n")
                }
                fileSystem.appendingSink(logPath).use { sink ->
                    sink.buffer().use { it.writeUtf8(logLine) }
                }
            } catch (e: Exception) {
                // Ignore failures to write to log file to avoid recursive crashing
            }
        }
    }

    fun initLogging(enableFileLogging: Boolean = true) {
        val writers = mutableListOf<LogWriter>(platformLogWriter())
        
        if (enableFileLogging) {
            val logFile = PlatformDirs.logDir / "app.log"
            writers.add(FileLogWriter(FileSystem.SYSTEM, logFile))
        }

        Logger.setLogWriters(writers)
        Logger.v { "Logging initialized. File logging enabled: $enableFileLogging" }
    }
}

inline fun Logger.info(crossinline message: () -> String) = this.i { message() }
inline fun Logger.info(throwable: Throwable, crossinline message: () -> String) = this.i(throwable) { message() }
inline fun Logger.warn(crossinline message: () -> String) = this.w { message() }
inline fun Logger.warn(throwable: Throwable, crossinline message: () -> String) = this.w(throwable) { message() }
inline fun Logger.debug(crossinline message: () -> String) = this.d { message() }
inline fun Logger.debug(throwable: Throwable, crossinline message: () -> String) = this.d(throwable) { message() }
inline fun Logger.error(crossinline message: () -> String) = this.e { message() }
inline fun Logger.error(throwable: Throwable, crossinline message: () -> String) = this.e(throwable) { message() }
