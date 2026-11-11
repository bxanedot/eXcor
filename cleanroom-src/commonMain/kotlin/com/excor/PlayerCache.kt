package com.excor

internal data class PlayerCacheEntry(
    val signatureTimestamp: Int?,
    val preparedPlayer: String?,
) {
    override fun toString(): String =
        "PlayerCacheEntry(signatureTimestamp=$signatureTimestamp, preparedPlayer=${if (preparedPlayer == null) "missing" else "present"})"
}

internal interface PlayerDataStore {
    suspend fun read(playerUrl: String): PlayerCacheEntry?
    suspend fun write(playerUrl: String, value: PlayerCacheEntry)
    suspend fun clear()
}
