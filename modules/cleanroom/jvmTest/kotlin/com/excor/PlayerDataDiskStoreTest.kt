package com.excor

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class PlayerDataDiskStoreTest {
    @Test
    fun atomicallyRetainsOnlyTheThreeNewestEntries() = runTest {
        val root = Files.createTempDirectory("excor-cache-test").toFile()
        try {
            val store = PlayerDataDiskStore(root) { true }
            (1..4).forEach { index ->
                store.write("https://www.youtube.com/s/player/$index/base.js", PlayerCacheEntry(index, "prepared-$index"))
            }

            assertNull(store.read("https://www.youtube.com/s/player/1/base.js"))
            assertEquals(PlayerCacheEntry(4, "prepared-4"), store.read("https://www.youtube.com/s/player/4/base.js"))
            assertEquals(3, root.resolve("eXcor-player-cache").listFiles()?.count { it.extension == "json" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun disabledConfigSkipsPersistentReadsAndWrites() = runTest {
        val root = Files.createTempDirectory("excor-cache-disabled").toFile()
        try {
            val store = PlayerDataDiskStore(root) { false }
            store.write("https://www.youtube.com/s/player/base.js", PlayerCacheEntry(12345, "prepared"))
            assertNull(store.read("https://www.youtube.com/s/player/base.js"))
            assertNull(root.resolve("eXcor-player-cache").listFiles())
        } finally {
            root.deleteRecursively()
        }
    }
}
