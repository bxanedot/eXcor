package com.excor.internal.sabr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hand-constructed frames, no network captures or third-party implementation data. */
class UmpFramingTest {
    @Test
    fun decodesEveryIntegerWidth() {
        assertEquals(127L, decodeUmpVarint(byteArrayOf(0x7f)))
        assertEquals(129L, decodeUmpVarint(byteArrayOf(0x81.toByte(), 0x02)))
        assertEquals(9_313L, decodeUmpVarint(byteArrayOf(0xc1.toByte(), 0x23, 0x01)))
        assertEquals(1_060_897L, decodeUmpVarint(byteArrayOf(0xe1.toByte(), 0x02, 0x03, 0x01)))
        // Five-byte form ignores the prefix's low three bits.
        assertEquals(0x12345678L, decodeUmpVarint(byteArrayOf(0xf7.toByte(), 0x78, 0x56, 0x34, 0x12)))
        assertEquals(4_294_967_295L, decodeUmpVarint(byteArrayOf(0xf0.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())))
    }

    @Test
    fun handlesZeroLengthPartsAndFragmentedPayloads() {
        val decoder = UmpFrameDecoder()
        val first = decoder.feed(byteArrayOf(20, 0, 21, 3, 0))
        assertEquals(1, first.size)
        assertEquals(20, first.single().type)
        assertTrue(first.single().payload.isEmpty())
        assertTrue(decoder.feed(byteArrayOf()).isEmpty())

        val rest = decoder.feed(byteArrayOf(17, 34))
        assertEquals(21, rest.single().type)
        assertContentEquals(byteArrayOf(0, 17, 34), rest.single().payload)
        decoder.finish()
    }

    @Test
    fun parsesSplitMultiByteTypeWithoutConfusingItWithPayload() {
        val decoder = UmpFrameDecoder()
        assertTrue(decoder.feed(byteArrayOf(0x81.toByte())).isEmpty())
        val parts = decoder.feed(byteArrayOf(0x02, 1, 0x7f))
        assertEquals(129, parts.single().type)
        assertContentEquals(byteArrayOf(0x7f), parts.single().payload)
        decoder.finish()
    }

    @Test
    fun refusesMalformedIntegerPrefixesAndTruncation() {
        assertFailsWith<UmpFramingException> { decodeUmpVarint(byteArrayOf()) }
        assertFailsWith<UmpFramingException> { decodeUmpVarint(byteArrayOf(0xf8.toByte())) }
        assertFailsWith<UmpFramingException> { decodeUmpVarint(byteArrayOf(0x81.toByte())) }

        val incomplete = UmpFrameDecoder()
        incomplete.feed(byteArrayOf(21, 3, 0))
        assertFailsWith<UmpFramingException> { incomplete.finish() }
        assertEquals(1, incomplete.feed(byteArrayOf(1, 2)).size)
        incomplete.finish()

        val incompleteLength = UmpFrameDecoder()
        incompleteLength.feed(byteArrayOf(21, 0x81.toByte()))
        assertFailsWith<UmpFramingException> { incompleteLength.finish() }
    }

    @Test
    fun rejectsOversizeBeforeAllocatingAndBoundsFrameCount() {
        assertFailsWith<UmpFramingException> {
            UmpFrameDecoder(maxPayloadBytes = 4).feed(byteArrayOf(21, 5))
        }
        assertFailsWith<UmpFramingException> {
            UmpFrameDecoder(maxPayloadBytes = 4).feed(byteArrayOf(21, 0xf0.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        }
        assertFailsWith<UmpFramingException> {
            UmpFrameDecoder(maxFramesPerFeed = 1).feed(byteArrayOf(20, 0, 21, 0))
        }
        assertFailsWith<IllegalArgumentException> { UmpFrameDecoder(maxPayloadBytes = 0) }
    }

    @Test
    fun diagnosticsNeverRenderPayloadContent() {
        val part = UmpFrame(21, "synthetic-secret".encodeToByteArray())
        assertFalse("synthetic-secret" in part.toString())
        assertTrue("payloadBytes=16" in part.toString())
    }
}
