package com.excor.internal.sabr

/**
 * Experimental assembly of UMP parts split across multiple complete HTTP response bodies.
 * This is an offline primitive, not an HTTP client or SABR transport.
 *
 * Every response begins with a complete type-20 header. If the preceding response ended
 * during a part's payload, the next response must repeat that part type, declaring exactly
 * the previously outstanding payload length. This strict rule deliberately rejects
 * inconsistent or ambiguous continuation data.
 */
internal class UmpResponseAssembler(
    private val maxPartBytes: Int = 8 * 1024 * 1024,
    private val maxResponseBytes: Int = 8 * 1024 * 1024,
    private val maxPartsPerResponse: Int = 4096,
) {
    init {
        require(maxPartBytes in 1..64_000_000)
        require(maxResponseBytes in 1..64_000_000)
        require(maxPartsPerResponse in 1..100_000)
    }

    private class Partial(val type: Int, val payload: ByteArray, var written: Int)
    private data class Header(val type: Int, val length: Int, val dataOffset: Int)

    private var partial: Partial? = null
    private var invalid = false

    /**
     * Accept one *complete*, bounded HTTP response body. Never concatenate separate
     * response bodies directly: that would incorrectly treat a continuation's type-20
     * header as media bytes. Returned parts have complete payloads only.
     */
    fun acceptResponse(body: ByteArray): List<UmpFrame> {
        if (invalid) throw UmpFramingException("UMP decoder is invalid after a malformed response")
        try {
            return parseResponse(body)
        } catch (failure: RuntimeException) {
            invalid = true
            partial = null
            throw failure
        }
    }

    fun finish() {
        if (invalid) throw UmpFramingException("UMP decoder is invalid")
        if (partial != null) throw UmpFramingException("truncated UMP response sequence")
    }

    private fun parseResponse(body: ByteArray): List<UmpFrame> {
        if (body.size > maxResponseBytes) throw UmpFramingException("UMP response exceeds byte limit")
        val parts = ArrayList<UmpFrame>()
        val start = readPartHeader(body, 0)
        if (start.type != 20) throw UmpFramingException("UMP response missing type-20 header")
        if (start.length > body.size - start.dataOffset) {
            throw UmpFramingException("truncated UMP response header")
        }
        emit(parts, 20, body.copyOfRange(start.dataOffset, start.dataOffset + start.length))
        var offset = start.dataOffset + start.length
        var mustContinue = partial != null
        while (offset < body.size) {
            val part = readPartHeader(body, offset)
            offset = part.dataOffset
            val active = partial
            val data: ByteArray
            var written: Int
            if (mustContinue) {
                if (active == null || part.type != active.type || part.length != active.payload.size - active.written) {
                    throw UmpFramingException("UMP continuation does not match pending part")
                }
                data = active.payload
                written = active.written
                mustContinue = false
            } else {
                data = ByteArray(part.length)
                written = 0
            }
            val count = minOf(data.size - written, body.size - offset)
            if (count > 0) body.copyInto(data, written, offset, offset + count)
            written += count
            offset += count
            if (written == data.size) {
                partial = null
                emit(parts, part.type, data)
            } else {
                partial = Partial(part.type, data, written)
                break
            }
        }
        if (mustContinue) throw UmpFramingException("missing UMP continuation after response header")
        return parts
    }

    private fun emit(parts: MutableList<UmpFrame>, type: Int, payload: ByteArray) {
        if (parts.size >= maxPartsPerResponse) throw UmpFramingException("too many UMP parts in response")
        parts += UmpFrame(type, payload)
    }

    private fun readPartHeader(body: ByteArray, offset: Int): Header {
        val (type, afterType) = readInteger(body, offset)
        val (size, afterSize) = readInteger(body, afterType)
        if (type > Int.MAX_VALUE) throw UmpFramingException("UMP part type exceeds range")
        if (size > maxPartBytes) throw UmpFramingException("UMP part exceeds byte limit")
        return Header(type.toInt(), size.toInt(), afterSize)
    }

    private fun readInteger(body: ByteArray, offset: Int): Pair<Long, Int> {
        if (offset >= body.size) throw UmpFramingException("truncated UMP field")
        val first = body[offset].toInt() and 0xff
        val width = when {
            first and 0x80 == 0 -> 1
            first and 0xc0 == 0x80 -> 2
            first and 0xe0 == 0xc0 -> 3
            first and 0xf0 == 0xe0 -> 4
            first and 0xf8 == 0xf0 -> 5
            else -> throw UmpFramingException("invalid UMP integer prefix")
        }
        if (width > body.size - offset) throw UmpFramingException("truncated UMP integer")
        return decodeUmpVarint(body.copyOfRange(offset, offset + width)) to offset + width
    }
}
