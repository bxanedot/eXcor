package com.excor.internal.sabr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Hand-built UMP integer-width and HTTP continuation boundaries.
 * No captured responses, network access, or credential-bearing fixtures.
 */
class UmpMultibyteContinuationTest {
    @Test
    fun reassemblesPayloadWhoseDeclaredSizeUsesTwoBytePrefix() {
        val payload = ByteArray(128) { (it and 0x7f).toByte() }
        val assembler = UmpResponseAssembler(maxPartBytes = 128)

        // Two-byte prefix: 128 = (0x80 & 0x3f) + (0x02 << 6).
        val first = byteArrayOf(20, 0, 21, 0x80.toByte(), 0x02) +
            payload.copyOfRange(0, 60)
        val emittedFirst = assembler.acceptResponse(first)
        assertEquals(listOf(20), emittedFirst.map { it.type })

        // The second complete HTTP response declares exactly 68 remaining bytes.
        val second = byteArrayOf(20, 0, 21, 0x84.toByte(), 0x01) +
            payload.copyOfRange(60, 128)
        val emittedSecond = assembler.acceptResponse(second)
        assertEquals(listOf(20, 21), emittedSecond.map { it.type })
        assertContentEquals(payload, emittedSecond[1].payload)
        assembler.finish()
    }

    @Test
    fun acceptsFiveBytePrefixForBoundedPayloadLength() {
        val payload = ByteArray(128) { (it xor 0x55).toByte() }
        // Five-byte form uses four little-endian value bytes.
        val body = byteArrayOf(20, 0, 21, 0xf0.toByte(), 0x80.toByte(), 0, 0, 0) + payload
        val frames = UmpResponseAssembler(maxPartBytes = 128).acceptResponse(body)
        assertEquals(listOf(20, 21), frames.map { it.type })
        assertContentEquals(payload, frames[1].payload)
    }

    @Test
    fun rejectsTwoByteContinuationLengthThatDiffersByOne() {
        val assembler = UmpResponseAssembler(maxPartBytes = 256)
        // A declared 130-byte payload leaves 70 bytes after the first 60.
        assembler.acceptResponse(
            byteArrayOf(20, 0, 21, 0x82.toByte(), 0x02) + ByteArray(60) { 1 }
        )
        // 69 bytes is inconsistent with the outstanding 70.
        assertFailsWith<UmpFramingException> {
            assembler.acceptResponse(
                byteArrayOf(20, 0, 21, 0x85.toByte(), 0x01) + ByteArray(69) { 2 }
            )
        }
        assertFailsWith<UmpFramingException> { assembler.finish() }
    }
}
