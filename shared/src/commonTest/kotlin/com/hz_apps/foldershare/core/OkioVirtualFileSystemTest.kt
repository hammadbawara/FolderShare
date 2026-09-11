package com.hz_apps.foldershare.core

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkioVirtualFileSystemTest {

    @Test
    fun testResolveAndFileOperations() = runBlocking {
        val fileSystem = FileSystem.SYSTEM
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "foldershare_test_${System.currentTimeMillis()}"
        fileSystem.createDirectories(tempDir)

        try {
            val vfs = OkioVirtualFileSystem(tempDir, fileSystem)

            // Resolve root
            val rootVirtualFile = vfs.resolve("/")
            assertNotNull(rootVirtualFile)
            assertTrue(rootVirtualFile.isDirectory)

            // Create child file
            val createdChild = rootVirtualFile.createChildFile("test.txt")
            assertNotNull(createdChild)
            assertEquals("test.txt", createdChild.name)

            // Write content to child file
            val content = "Hello Folder Share Okio KMP!"
            val channel = ByteReadChannel(content.toByteArray())
            createdChild.write(channel)

            // Read content from child file
            val readChannel = createdChild.read()
            val readBytes = readChannel.toInputStream().readBytes()
            assertEquals(content, readBytes.decodeToString())

            // List children
            val children = rootVirtualFile.listChildren()
            assertEquals(1, children.size)
            assertEquals("test.txt", children.first().name)

            // Prevent traversal attack outside root
            val illegalVirtualFile = vfs.resolve("../secret.txt")
            assertNull(illegalVirtualFile)

            // Delete child
            assertTrue(createdChild.delete())
            assertEquals(0, rootVirtualFile.listChildren().size)
        } finally {
            fileSystem.deleteRecursively(tempDir)
        }
    }
}
