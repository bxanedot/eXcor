package com.excor

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlin.text.CharacterCodingException

/**
 * Read at most [limit] bytes plus one from a scoped streaming response. Use
 * HttpStatement.execute with a response block to avoid buffering before this check.
 *
 * YouTube's player JSON, watch pages, and player scripts are served as UTF-8.
 * Limits are byte counts, not character counts. They bound application reads;
 * the transport may prefetch additional bytes before cancellation closes it.
 */
internal suspend fun readBoundedResponseText(response: HttpResponse, limit: Int): String? {
    require(limit in 1 until Int.MAX_VALUE) { "invalid response limit" }
    val channel = response.bodyAsChannel()
    val bytes = ByteArray(limit + 1)
    var used = 0
    while (used < bytes.size) {
        val count = channel.readAvailable(bytes, used, bytes.size - used)
        if (count == -1) return try {
            bytes.decodeToString(0, used, throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
        if (count > 0) used += count
    }
    // The additional byte proves the limit was exceeded. Stop reading immediately.
    channel.cancel()
    return null
}
