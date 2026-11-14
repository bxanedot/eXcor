package com.excor.internal.primary.extraction

import com.excor.internal.primary.extraction.strategy.PoTokenProviderKind

data class TokenProviderCapabilities(
    val providers: Set<PoTokenProviderKind> = emptySet(),
    val usesWebView: Boolean = false,
)

data class PoTokenResult(
    val playerRequestToken: String,
    val streamingDataToken: String,
    val visitorData: String,
) {
    override fun toString(): String =
        "PoTokenResult(playerRequestToken=${presence(playerRequestToken)}, " +
            "streamingDataToken=${presence(streamingDataToken)}, visitorData=${presence(visitorData)})"

    private fun presence(value: String): String = if (value.isEmpty()) "missing" else "present"
}

interface TokenProvider {
    val capabilities: TokenProviderCapabilities
        get() = TokenProviderCapabilities()

    suspend fun getPoToken(videoId: String, visitorData: String, cookie: String? = null): PoTokenResult?
    suspend fun prewarm(cookie: String? = null) = Unit
    suspend fun invalidateAttestation() = Unit
    suspend fun close() = Unit
}
