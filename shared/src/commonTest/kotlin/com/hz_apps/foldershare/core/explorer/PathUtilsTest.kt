package com.hz_apps.foldershare.core.explorer

import com.hz_apps.foldershare.core.explorer.util.PathUtils
import com.hz_apps.foldershare.core.explorer.util.PlatformFileHandler
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun testClearTemporaryDirectoryRemovesCachedFiles() {
        val fs = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "path_test_clear_${System.currentTimeMillis()}"
        fs.createDirectories(tempDir)

        val handler = object : PlatformFileHandler {
            override fun getDefaultDownloadDirectory(): Path = tempDir
            override fun getTemporaryDirectory(): Path = tempDir
            override fun getDownloadDestinationPath(fileName: String, customDirectory: Path?): Path =
                (customDirectory ?: tempDir) / fileName
            override fun openFile(
                downloadUrl: String,
                mimeType: String?,
                title: String?,
                fileSize: Long?,
                rawUrl: String?
            ) {}
            override fun openLocalFile(filePath: Path, mimeType: String?, title: String?) {}
            override fun shareFile(downloadUrl: String, title: String) {}
            override fun copyToClipboard(text: String) {}
        }

        try {
            val file1 = tempDir / "video.mp4"
            val file2 = tempDir / "report.pdf"
            fs.sink(file1).buffer().use { it.writeUtf8("video-data") }
            fs.sink(file2).buffer().use { it.writeUtf8("pdf-data") }
            assertTrue(fs.exists(file1))
            assertTrue(fs.exists(file2))

            handler.clearTemporaryDirectory(fs)

            assertTrue(fs.exists(tempDir))
            assertFalse(fs.exists(file1))
            assertFalse(fs.exists(file2))
        } finally {
            fs.deleteRecursively(tempDir)
        }
    }
}
