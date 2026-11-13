package com.excor

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable

/**
 * Read at most [limit] bytes of an HTTP response. A body that exceeds the limit is
 * rejected before it can be fully buffered by bodyAsText(), including chunked
 * responses that omit Content-Length.
 *
 * YouTube's player JSON, watch pages, and player scripts are served as UTF-8.
 * Limits are byte counts, not character counts.
 */
internal suspend fun readBoundedResponseText(response: HttpResponse, limit: Int): String? {
    require(limit in 1 until Int.MAX_VALUE) { "invalid response limit" }
    val channel = response.bodyAsChannel()
    val bytes = ByteArray(limit + 1)
    var used = 0
    while (used < bytes.size) {
        val count = channel.readAvailable(bytes, used, bytes.size - used)
        if (count == -1) return bytes.decodeToString(0, used)
        if (count > 0) used += count
    }
    // The additional byte proves the limit was exceeded. Stop reading immediately.
    channel.cancel()
    return null
}
