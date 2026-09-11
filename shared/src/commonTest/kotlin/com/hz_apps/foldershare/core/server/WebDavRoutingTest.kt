package com.hz_apps.foldershare.core.server

import com.hz_apps.foldershare.core.PermissionWrapperVirtualFile
import com.hz_apps.foldershare.core.models.VirtualFile
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeVirtualFile(
    override val name: String,
    override val path: String,
    override val isDirectory: Boolean,
    override var isReadAllowed: Boolean = true,
    override var isWriteAllowed: Boolean = true
) : VirtualFile {
    override val length: Long = 0L
    override val lastModified: Long = 0L

    val children = mutableListOf<FakeVirtualFile>()
    var writtenData: String = ""

    override suspend fun listChildren(): List<VirtualFile> = children

    override suspend fun read(range: LongRange?): ByteReadChannel {
        return ByteReadChannel(writtenData.toByteArray())
    }

    override suspend fun write(channel: ByteReadChannel, append: Boolean) {
        writtenData = "written_content"
    }

    override suspend fun delete(): Boolean = true

    override suspend fun createChildFile(name: String): VirtualFile? {
        if (!isDirectory) return null
        val child = FakeVirtualFile(name, "$path/$name", isDirectory = false, isReadAllowed, isWriteAllowed)
        children.add(child)
        return child
    }

    override suspend fun createChildDirectory(name: String): VirtualFile? {
        if (!isDirectory) return null
        val child = FakeVirtualFile(name, "$path/$name", isDirectory = true, isReadAllowed, isWriteAllowed)
        children.add(child)
        return child
    }

    override suspend fun renameTo(newName: String): Boolean {
        return true
    }
}

class WebDavRoutingTest {

    @Test
    fun testPermissionWrapperGuardsWriteOperationsWhenWriteDisabled() = runBlocking {
        val delegate = FakeVirtualFile("TestFolder", "/TestFolder", isDirectory = true)
        val wrapper = PermissionWrapperVirtualFile(
            delegate = delegate,
            isReadAllowed = true,
            isWriteAllowed = false
        )

        assertFalse(wrapper.isWriteAllowed)
        assertTrue(wrapper.isReadAllowed)

        try {
            wrapper.createChildFile("new.txt")
            kotlin.test.fail("Should throw SecurityException when isWriteAllowed is false")
        } catch (e: SecurityException) {
            assertEquals("Write access disabled for this folder", e.message)
        }

        try {
            wrapper.renameTo("RenamedFolder")
            kotlin.test.fail("Should throw SecurityException on renameTo when isWriteAllowed is false")
        } catch (e: SecurityException) {
            assertEquals("Write access disabled for this folder", e.message)
        }
    }

    @Test
    fun testPermissionWrapperAllowsWriteOperationsWhenWriteEnabled() = runBlocking {
        val delegate = FakeVirtualFile("TestFolder", "/TestFolder", isDirectory = true)
        val wrapper = PermissionWrapperVirtualFile(
            delegate = delegate,
            isReadAllowed = true,
            isWriteAllowed = true
        )

        assertTrue(wrapper.isWriteAllowed)
        val child = wrapper.createChildFile("new.txt")
        assertNotNull(child)
        assertEquals("new.txt", child.name)
        assertTrue(child.isWriteAllowed)
        assertTrue(wrapper.renameTo("RenamedFolder"))
    }

    @Test
    fun testResolveMimeTypeReturnsCorrectMimeTypes() {
        assertEquals("video/mp4", resolveMimeType("sample.mp4").toString())
        assertEquals("video/x-matroska", resolveMimeType("movie.mkv").toString())
        assertEquals("audio/mpeg", resolveMimeType("song.mp3").toString())
        assertEquals("image/jpeg", resolveMimeType("photo.jpg").toString())
        assertEquals("application/pdf", resolveMimeType("doc.pdf").toString())
        assertEquals("application/octet-stream", resolveMimeType("data.unknown").toString())
    }

    @Test
    fun testParseByteRangeHandlesAllFormats() {
        val fileLength = 100_000L

        // Single range: bytes=0-499
        val r1 = parseByteRange("bytes=0-499", fileLength)
        assertNotNull(r1)
        assertEquals(0L, r1.start)
        assertEquals(499L, r1.end)
        assertEquals(500L, r1.length)

        // Open-ended range: bytes=1000-
        val r2 = parseByteRange("bytes=1000-", fileLength)
        assertNotNull(r2)
        assertEquals(1000L, r2.start)
        assertEquals(99999L, r2.end)
        assertEquals(99000L, r2.length)

        // Suffix range (MKV/MP4 cue/moov atom index lookup): bytes=-500
        val r3 = parseByteRange("bytes=-500", fileLength)
        assertNotNull(r3)
        assertEquals(99500L, r3.start)
        assertEquals(99999L, r3.end)
        assertEquals(500L, r3.length)
    }

    @Test
    fun testParseByteRangeUnsatisfiableRanges() {
        val fileLength = 100_000L

        // Requesting starting at or past file length returns null (triggers HTTP 416)
        kotlin.test.assertNull(parseByteRange("bytes=100000-", fileLength))
        kotlin.test.assertNull(parseByteRange("bytes=150000-", fileLength))
        kotlin.test.assertNull(parseByteRange("bytes=500-200", fileLength))
        kotlin.test.assertNull(parseByteRange("bytes=-0", fileLength))
        kotlin.test.assertNull(parseByteRange("bytes=0-10", 0L))
    }
}

