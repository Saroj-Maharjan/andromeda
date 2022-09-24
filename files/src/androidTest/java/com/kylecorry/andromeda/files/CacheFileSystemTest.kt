package com.kylecorry.andromeda.files

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

class CacheFileSystemTest {

    private lateinit var ctx: Context
    private lateinit var fileSystem: CacheFileSystem

    @Before
    fun setup() {
        ctx = InstrumentationRegistry.getInstrumentation().context
        fileSystem = CacheFileSystem(ctx)
    }

    @Test
    fun createFile() {
        fileSystem.createFile("something/other.txt")
        val file = fileSystem.getFile("something/other.txt", false)
        assertTrue(file.exists())
        assertFalse(file.isDirectory)
        fileSystem.delete("something", true)
    }

    @Test
    fun createDirectory() {
        fileSystem.createDirectory("something/other")
        val file = fileSystem.getDirectory("something/other", false)
        assertTrue(file.exists())
        assertTrue(file.isDirectory)
        fileSystem.delete("something", true)
    }

    @Test
    fun delete() {
        val file = fileSystem.getFile("test.txt", true)
        fileSystem.delete("test.txt")
        assertFalse(file.exists())
    }

    @Test
    fun deleteRecursive() {
        val file = fileSystem.getFile("something/other", true)
        val parent = fileSystem.getFile("something", false)
        fileSystem.delete("something", true)
        assertFalse(parent.exists())
        assertFalse(file.exists())
    }

    @Test
    fun getDirectoryNoCreate() {
        val file = fileSystem.getDirectory("test", false)
        assertEquals("${getBasePath()}/test", file.path)
        assertFalse(file.exists())
    }

    @Test
    fun getDirectoryCreate() {
        val file = fileSystem.getDirectory("test", true)
        assertEquals("${getBasePath()}/test", file.path)
        assertTrue(file.isDirectory)
        assertTrue(file.exists())
        fileSystem.delete("test")
    }

    @Test
    fun getDirectoryCreatePaths() {
        val file = fileSystem.getDirectory("test/other", true)
        assertEquals("${getBasePath()}/test/other", file.path)
        assertTrue(file.isDirectory)
        assertTrue(file.exists())
        fileSystem.delete("test", true)
    }

    @Test
    fun getFileNoCreate() {
        val file = fileSystem.getFile("test.txt", false)
        assertEquals("${getBasePath()}/test.txt", file.path)
        assertFalse(file.exists())
    }

    @Test
    fun getFileCreate() {
        val file = fileSystem.getFile("test.txt", true)
        assertEquals("${getBasePath()}/test.txt", file.path)
        assertTrue(file.exists())

        fileSystem.delete("test.txt")
    }

    @Test
    fun getFileCreatePath() {
        val file = fileSystem.getFile("exports/test.txt", true)
        assertEquals("${getBasePath()}/exports/test.txt", file.path)
        assertTrue(file.exists())

        fileSystem.delete("exports/test.txt", true)
    }

    @Test
    fun readNonExistentFile() {
        val text = fileSystem.read("test.txt", false)
        assertEquals("", text)
        assertFalse(fileSystem.getFile("test.txt", false).exists())
    }

    @Test
    fun readCreateNonExistentFile() {
        val text = fileSystem.read("test.txt", true)
        assertEquals("", text)
        assertTrue(fileSystem.getFile("test.txt", false).exists())
        fileSystem.delete("test.txt")
    }

    @Test
    fun writeThenReadFile() {
        fileSystem.write("test.txt", "testing")
        val text = fileSystem.read("test.txt", false)
        assertEquals("testing", text)

        fileSystem.delete("test.txt")
    }

    @Test
    fun appendToFile() {
        fileSystem.write("test.txt", "testing")
        fileSystem.write("test.txt", "123", true)
        val text = fileSystem.read("test.txt", false)
        assertEquals("testing123", text)

        fileSystem.delete("test.txt")
    }

    @Test
    fun overwriteFile() {
        fileSystem.write("test.txt", "testing")
        fileSystem.write("test.txt", "123")
        val text = fileSystem.read("test.txt", false)
        assertEquals("123", text)

        fileSystem.delete("test.txt")
    }

    @Test
    fun canGetRelativeFilePath() {
        val relative = fileSystem.getRelativePath(fileSystem.getFile("test.txt", true))

        assertEquals("test.txt", relative)

        fileSystem.delete("test.txt")
    }

    private fun getBasePath(): String {
        return InstrumentationRegistry.getInstrumentation().context.cacheDir.path
    }

}