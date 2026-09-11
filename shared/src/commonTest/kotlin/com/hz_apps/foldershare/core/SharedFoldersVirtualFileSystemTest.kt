package com.hz_apps.foldershare.core

import com.hz_apps.foldershare.core.models.VirtualFile
import com.hz_apps.foldershare.data.database.FolderConfigEntity
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedFoldersVirtualFileSystemTest {

    private class TestVirtualFile(
        override val name: String,
        override val path: String,
        override val isDirectory: Boolean = false,
        override val isReadAllowed: Boolean = true,
        override val isWriteAllowed: Boolean = true
    ) : VirtualFile {
        override val length: Long = 100L
        override val lastModified: Long = 1000L

        var written: Boolean = false

        override suspend fun listChildren(): List<VirtualFile> = emptyList()
        override suspend fun read(range: LongRange?): ByteReadChannel = ByteReadChannel("test-data".toByteArray())
        override suspend fun write(channel: ByteReadChannel, append: Boolean) { written = true }
        override suspend fun delete(): Boolean = true
        override suspend fun createChildFile(name: String): VirtualFile? = TestVirtualFile(name, "$path/$name")
        override suspend fun createChildDirectory(name: String): VirtualFile? = TestVirtualFile(name, "$path/$name", isDirectory = true)
        override suspend fun renameTo(newName: String): Boolean = true
    }

    private class TestLocalFolderResolver : LocalFolderResolver {
        override suspend fun resolveInFolder(folder: FolderConfigEntity, relativePath: String): VirtualFile? {
            val fileName = if (relativePath.isEmpty()) folder.name else relativePath.split("/").last()
            return TestVirtualFile(
                name = fileName,
                path = if (relativePath.isEmpty()) "/${folder.name}" else "/${folder.name}/$relativePath",
                isDirectory = relativePath.isEmpty()
            )
        }
    }

    @Test
    fun testDynamicFolderAdditionAndPause() = runBlocking {
        val foldersList = mutableListOf<FolderConfigEntity>()
        val resolver = TestLocalFolderResolver()
        val vfs = SharedFoldersVirtualFileSystem(
            foldersProvider = { foldersList },
            resolver = resolver
        )

        // 1. Initial empty state: Root has 0 children, resolve returns root
        val root = vfs.resolve("/")
        assertNotNull(root)
        assertEquals(0, root.listChildren().size)
        assertNull(vfs.resolve("/Movies"))

        // 2. Add Movies folder with isShared = true
        foldersList.add(
            FolderConfigEntity(
                id = 1,
                name = "Movies",
                path = "/storage/movies",
                isShared = true,
                isReadAllowed = true,
                isWriteAllowed = false
            )
        )

        // Dynamically discovered without restarting VFS
        assertEquals(1, root.listChildren().size)
        assertEquals("Movies", root.listChildren().first().name)
        val moviesFile = vfs.resolve("/Movies")
        assertNotNull(moviesFile)
        assertEquals("Movies", moviesFile.name)

        // 3. Pause Movies folder (isShared = false)
        foldersList[0] = foldersList[0].copy(isShared = false)

        // Dynamically hidden from root listing and returns null on path resolution
        assertEquals(0, root.listChildren().size)
        assertNull(vfs.resolve("/Movies"))
    }

    @Test
    fun testDynamicPermissionChanges() = runBlocking {
        val foldersList = mutableListOf(
            FolderConfigEntity(
                id = 1,
                name = "Docs",
                path = "/storage/docs",
                isShared = true,
                isReadAllowed = true,
                isWriteAllowed = false
            )
        )
        val resolver = TestLocalFolderResolver()
        val vfs = SharedFoldersVirtualFileSystem(
            foldersProvider = { foldersList },
            resolver = resolver
        )

        val fileReadOnly = vfs.resolve("/Docs/report.pdf")
        assertNotNull(fileReadOnly)
        // Write operation throws SecurityException
        assertFailsWith<SecurityException> {
            fileReadOnly.write(ByteReadChannel("content".toByteArray()))
        }

        // Dynamically enable write permission
        foldersList[0] = foldersList[0].copy(isWriteAllowed = true)

        val fileReadWrite = vfs.resolve("/Docs/report.pdf")
        assertNotNull(fileReadWrite)
        fileReadWrite.write(ByteReadChannel("content".toByteArray()))
        assertTrue((fileReadWrite as PermissionWrapperVirtualFile).isWriteAllowed)
    }
}
