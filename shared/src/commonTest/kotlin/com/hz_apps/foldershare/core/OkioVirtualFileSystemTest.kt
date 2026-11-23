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

    @Test
    fun testResolveOkioPathInFolderAndTraversalPrevention() = runBlocking {
        val fileSystem = FileSystem.SYSTEM
        val baseDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "foldershare_resolver_test_${System.currentTimeMillis()}"
        val sharedRoot = baseDir / "music"
        val nestedDir = sharedRoot / "nested"
        val siblingPrefixDir = baseDir / "music2"
        fileSystem.createDirectories(nestedDir)
        fileSystem.createDirectories(siblingPrefixDir)

        try {
            val nestedFile = nestedDir / "hello.txt"
            fileSystem.sink(nestedFile).close()

            val outsideSecret = baseDir / "secret.txt"
            fileSystem.sink(outsideSecret).close()

            val siblingSecret = siblingPrefixDir / "secret.txt"
            fileSystem.sink(siblingSecret).close()

            // Resolves root
            val resolvedRoot = resolveOkioPathInFolder(sharedRoot.toString(), "Music", "", fileSystem)
            assertNotNull(resolvedRoot)
            assertTrue(resolvedRoot.isDirectory)
            assertEquals("/Music", resolvedRoot.path)

            // Resolves nested file
            val resolvedNested = resolveOkioPathInFolder(sharedRoot.toString(), "Music", "nested/hello.txt", fileSystem)
            assertNotNull(resolvedNested)
            assertEquals("hello.txt", resolvedNested.name)
            assertEquals("/Music/nested/hello.txt", resolvedNested.path)

            // Rejects parent traversal
            assertNull(resolveOkioPathInFolder(sharedRoot.toString(), "Music", "../secret.txt", fileSystem))

            // Rejects sibling directory with shared path prefix (e.g. /music vs /music2)
            assertNull(resolveOkioPathInFolder(sharedRoot.toString(), "Music", "../music2/secret.txt", fileSystem))
        } finally {
            fileSystem.deleteRecursively(baseDir)
        }
    }
}
