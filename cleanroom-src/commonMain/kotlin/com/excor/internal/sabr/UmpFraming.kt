package com.excor.internal.sabr

/** Experimental framing only. No SABR requests, playback, or account access. */
internal class UmpFramingException(message: String) : IllegalArgumentException(message)

internal class UmpFrame(val type: Int, val payload: ByteArray) {
    // Payloads may eventually contain media or session-bearing bytes; never render them in diagnostics.
    override fun toString(): String = "UmpFrame(type=$type, payloadBytes=" + payload.size + ")"
}

/** Decode YouTube UMP's prefix-sized integer, which is NOT a protobuf varint. */
internal fun decodeUmpVarint(encoded: ByteArray): Long {
    if (encoded.isEmpty()) throw UmpFramingException("empty UMP integer")
    val first = encoded[0].toInt() and 0xff
    val width = umpVarintWidth(first)
    if (encoded.size != width) throw UmpFramingException("invalid UMP integer length")
    if (width == 5) {
        // Five-byte form uses exactly four little-endian value bytes; the prefix's low bits are unused.
        var result = 0L
        for (index in 1..4) {
            result = result or ((encoded[index].toInt() and 0xff).toLong() shl ((index - 1) * 8))
        }
        return result
    }
    val lowBits = 8 - width
    var result = (first and ((1 shl lowBits) - 1)).toLong()
    for (index in 1 until width) {
        result = result or ((encoded[index].toInt() and 0xff).toLong() shl (lowBits + (index - 1) * 8))
    }
    return result
}

private fun umpVarintWidth(first: Int): Int = when {
    first and 0x80 == 0 -> 1
    first and 0xc0 == 0x80 -> 2
    first and 0xe0 == 0xc0 -> 3
    first and 0xf0 == 0xe0 -> 4
    first and 0xf8 == 0xf0 -> 5
    else -> throw UmpFramingException("invalid UMP integer prefix")
}

/**
 * Bounded, incremental decoder for raw UMP type/length/payload framing.
 * Does not negotiate SABR, interpret protobuf data, or join parts across HTTP response boundaries.
 * Accepts arbitrary *transport read* chunk boundaries without assuming a frame fits in a read.
 */
internal class UmpFrameDecoder(
    private val maxPayloadBytes: Int = 8 * 1024 * 1024,
    private val maxFramesPerFeed: Int = 4096,
) {
    init {
        require(maxPayloadBytes in 1..64_000_000) { "invalid payload bound" }
        require(maxFramesPerFeed in 1..100_000) { "invalid frame-count bound" }
    }

    private enum class Phase { TYPE, LENGTH, PAYLOAD }

    private var phase = Phase.TYPE
    private val integerBytes = ByteArray(5)
    private var integerUsed = 0
    private var integerWidth = 0
    private var frameType = 0
    private var framePayload = ByteArray(0)
    private var payloadUsed = 0

    fun feed(chunk: ByteArray): List<UmpFrame> {
        val frames = ArrayList<UmpFrame>()
        var at = 0
        while (at < chunk.size) {
            if (phase == Phase.PAYLOAD) {
                val amount = minOf(framePayload.size - payloadUsed, chunk.size - at)
                chunk.copyInto(framePayload, payloadUsed, at, at + amount)
                payloadUsed += amount
                at += amount
                if (payloadUsed == framePayload.size) {
                    emitFrame(frames)
                }
            } else {
                val byte = chunk[at++]
                if (integerUsed == 0) integerWidth = umpVarintWidth(byte.toInt() and 0xff)
                integerBytes[integerUsed++] = byte
                if (integerUsed == integerWidth) {
                    val value = decodeUmpVarint(integerBytes.copyOf(integerUsed))
                    integerUsed = 0
                    integerWidth = 0
                    when (phase) {
                        Phase.TYPE -> {
                            if (value > Int.MAX_VALUE) throw UmpFramingException("UMP part type is too large")
                            frameType = value.toInt()
                            phase = Phase.LENGTH
                        }
                        Phase.LENGTH -> {
                            if (value > maxPayloadBytes.toLong()) throw UmpFramingException("UMP part exceeds size bound")
                            framePayload = ByteArray(value.toInt())
                            payloadUsed = 0
                            phase = Phase.PAYLOAD
                            if (framePayload.isEmpty()) emitFrame(frames)
                        }
                        Phase.PAYLOAD -> error("unreachable")
                    }
                }
            }
        }
        return frames
    }

    fun finish() {
        if (phase != Phase.TYPE || integerUsed != 0) throw UmpFramingException("truncated UMP frame")
    }

    private fun emitFrame(out: MutableList<UmpFrame>) {
        if (out.size >= maxFramesPerFeed) throw UmpFramingException("too many UMP parts in one read")
        out.add(UmpFrame(frameType, framePayload))
        framePayload = ByteArray(0)
        payloadUsed = 0
        phase = Phase.TYPE
    }
}
