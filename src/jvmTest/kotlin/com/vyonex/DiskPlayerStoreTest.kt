package com.vyonex

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiskPlayerStoreTest {
    @Test
    fun keepsTheThreeNewestEntries() = runBlocking {
        val dir = Files.createTempDirectory("vyonex").toFile()
        val store = DiskPlayerStore(dir)
        for (i in 1..5) {
            store.write("p$i", "v$i")
            File(dir, "p$i.js").setLastModified(i * 10_000L)
        }
        assertEquals(listOf("p3.js", "p4.js", "p5.js"), dir.list()!!.sorted())
        assertEquals("v4", store.read("p4"))
        assertNull(store.read("p1"))
        store.delete("p4")
        assertNull(store.read("p4"))
        dir.deleteRecursively()
        Unit
    }
}
