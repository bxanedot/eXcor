package com.excor

internal data class PlayerCacheEntry(
    val signatureTimestamp: Int?,
    val preparedPlayer: String?,
)

internal interface PlayerDataStore {
    suspend fun read(playerUrl: String): PlayerCacheEntry?
    suspend fun write(playerUrl: String, value: PlayerCacheEntry)
    suspend fun clear()
}
