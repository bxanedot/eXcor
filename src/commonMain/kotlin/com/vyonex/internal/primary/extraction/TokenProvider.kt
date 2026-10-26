package com.vyonex.internal.primary.extraction

import com.vyonex.internal.primary.extraction.strategy.PoTokenProviderKind
import com.vyonex.presence

interface TokenProvider {
    val capabilities: TokenProviderCapabilities get() = TokenProviderCapabilities()
    suspend fun getPoToken(videoId: String, visitorData: String, cookie: String? = null): PoTokenResult?
    suspend fun prewarm(cookie: String? = null) {}
    suspend fun invalidateAttestation() {}
    suspend fun close() {}
}

data class TokenProviderCapabilities(
    val providers: Set<PoTokenProviderKind> = emptySet(),
    val usesWebView: Boolean = false,
)

data class PoTokenResult(
    val playerRequestToken: String,
    val streamingDataToken: String,
    val visitorData: String,
) {
    override fun toString() =
        "PoTokenResult(playerRequestToken=${presence(playerRequestToken)}, " +
            "streamingDataToken=${presence(streamingDataToken)}, visitorData=${presence(visitorData)})"
}
