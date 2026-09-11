package com.hz_apps.foldershare.core.explorer

import com.hz_apps.foldershare.core.explorer.util.PathUtils
import okio.FileSystem
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class PathUtilsTest {

    @Test
    fun testGetMimeTypeForKnownExtensions() {
        assertEquals("video/x-matroska", PathUtils.getMimeType("mkv"))
        assertEquals("video/x-matroska", PathUtils.getMimeType(".MKV"))
        assertEquals("video/mp4", PathUtils.getMimeType("mp4"))
        assertEquals("audio/mpeg", PathUtils.getMimeType("mp3"))
        assertEquals("image/png", PathUtils.getMimeType("png"))
        assertEquals("application/pdf", PathUtils.getMimeType("pdf"))
        assertEquals("*/*", PathUtils.getMimeType("unknown_ext"))
    }

    @Test
    fun testGenerateUniqueDestinationPathWhenFileDoesNotExist() {
        val fs = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "path_test_1_${System.currentTimeMillis()}"
        fs.createDirectories(tempDir)

        try {
            val uniquePath = PathUtils.generateUniqueDestinationPath(tempDir, "document.pdf", fs)
            assertEquals((tempDir / "document.pdf").toString(), uniquePath.toString())
        } finally {
            fs.deleteRecursively(tempDir)
        }
    }

    @Test
    fun testGenerateUniqueDestinationPathWhenFileExists() {
        val fs = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "path_test_2_${System.currentTimeMillis()}"
        fs.createDirectories(tempDir)

        try {
            fs.sink(tempDir / "document.pdf").buffer().use { it.writeUtf8("content") }

            val uniquePath = PathUtils.generateUniqueDestinationPath(tempDir, "document.pdf", fs)
            assertEquals((tempDir / "document (1).pdf").toString(), uniquePath.toString())

            fs.sink(tempDir / "document (1).pdf").buffer().use { it.writeUtf8("content2") }
            val uniquePath2 = PathUtils.generateUniqueDestinationPath(tempDir, "document.pdf", fs)
            assertEquals((tempDir / "document (2).pdf").toString(), uniquePath2.toString())
        } finally {
            fs.deleteRecursively(tempDir)
        }
    }

    @Test
    fun testGenerateUniqueDestinationPathWithoutExtension() {
        val fs = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "path_test_3_${System.currentTimeMillis()}"
        fs.createDirectories(tempDir)

        try {
            fs.sink(tempDir / "LICENSE").buffer().use { it.writeUtf8("MIT") }

            val uniquePath = PathUtils.generateUniqueDestinationPath(tempDir, "LICENSE", fs)
            assertEquals((tempDir / "LICENSE (1)").toString(), uniquePath.toString())
        } finally {
            fs.deleteRecursively(tempDir)
        }
    }
}
