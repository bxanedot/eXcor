package com.excor

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class PlayerDataDiskStore(
    filesDir: File?,
    private val enabled: () -> Boolean,
) : PlayerDataStore {
    private val directory = filesDir?.resolve("eXcor-player-cache")

    override suspend fun read(playerUrl: String): PlayerCacheEntry? = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext null
        val file = directory?.resolve(fileName(playerUrl)) ?: return@withContext null
        if (!file.isFile || file.length() > MAX_ENTRY_BYTES) return@withContext null
        runCatching {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            PlayerCacheEntry(
                signatureTimestamp = json["signatureTimestamp"]?.jsonPrimitive?.content?.toIntOrNull(),
                preparedPlayer = json["preparedPlayer"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
    }

    override suspend fun write(playerUrl: String, value: PlayerCacheEntry): Unit = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext
        val dir = directory ?: return@withContext
        synchronized(this@PlayerDataDiskStore) {
            Files.createDirectories(dir.toPath())
            val target = dir.resolve(fileName(playerUrl))
            val temp = Files.createTempFile(dir.toPath(), "eXcor-", ".tmp")
            try {
                temp.toFile().writeText(buildJsonObject {
                    value.signatureTimestamp?.let { put("signatureTimestamp", it) }
                    value.preparedPlayer?.let { put("preparedPlayer", it) }
                }.toString())
                try {
                    Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val newest = dir.listFiles()?.maxOfOrNull(File::lastModified) ?: 0L
                target.setLastModified(maxOf(System.currentTimeMillis(), newest + 1))
                dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                    ?.sortedByDescending(File::lastModified)
                    ?.drop(MAX_ENTRIES)
                    ?.forEach(File::delete)
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        synchronized(this@PlayerDataDiskStore) {
            directory?.listFiles()?.forEach(File::delete)
        }
    }

    private fun fileName(playerUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(playerUrl.encodeToByteArray())
        val name = digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "$name.json"
    }

    private companion object {
        const val MAX_ENTRY_BYTES = 2_000_000L
        const val MAX_ENTRIES = 3
    }
}
