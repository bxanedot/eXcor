package com.excor.internal.sabr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Additional synthetic boundary cases for response continuation.
 * No network traffic, captures, credentials, or signed URLs.
 */
class UmpResponseBoundaryTest {
    @Test
    fun oneByteContinuationsPreserveOrderAcrossManyResponses() {
        val assembler = UmpResponseAssembler(maxPartBytes = 16, maxResponseBytes = 16)
        val initial = assembler.acceptResponse(byteArrayOf(20, 0, 21, 5, 10))
        assertEquals(listOf(20), initial.map { it.type })
        for (remaining in 4 downTo 2) {
            val next = assembler.acceptResponse(
                byteArrayOf(20, 0, 21, remaining.toByte(), (15 - remaining).toByte())
            )
            assertEquals(listOf(20), next.map { it.type })
        }
        val last = assembler.acceptResponse(byteArrayOf(20, 0, 21, 1, 14, 22, 0))
        assertEquals(listOf(20, 21, 22), last.map { it.type })
        assertContentEquals(byteArrayOf(10, 11, 12, 13, 14), last[1].payload)
        assembler.finish()
    }

    @Test
    fun malformedContinuationPoisonsInstanceEvenAfterValidHeader() {
        val assembler = UmpResponseAssembler()
        assembler.acceptResponse(byteArrayOf(20, 0, 21, 4, 1))
        assertFailsWith<UmpFramingException> {
            assembler.acceptResponse(byteArrayOf(20, 0, 21, 2, 2, 3))
        }
        assertFailsWith<UmpFramingException> {
            assembler.acceptResponse(byteArrayOf(20, 0, 21, 3, 2, 3, 4))
        }
        assertFailsWith<UmpFramingException> { assembler.finish() }
    }

    @Test
    fun exactResponseLimitIsAcceptedAndOneByteMoreFailsClosed() {
        val atLimit = UmpResponseAssembler(maxResponseBytes = 4)
        assertEquals(2, atLimit.acceptResponse(byteArrayOf(20, 0, 21, 0)).size)
        atLimit.finish()

        val tooLong = UmpResponseAssembler(maxResponseBytes = 4)
        assertFailsWith<UmpFramingException> {
            tooLong.acceptResponse(byteArrayOf(20, 0, 21, 1, 7))
        }
        assertFailsWith<UmpFramingException> {
            tooLong.acceptResponse(byteArrayOf(20, 0))
        }
    }

    @Test
    fun truncatedContinuationIntegerIsRejectedWithoutReadingPastBody() {
        val assembler = UmpResponseAssembler()
        assembler.acceptResponse(byteArrayOf(20, 0, 21, 2, 9))
        assertFailsWith<UmpFramingException> {
            assembler.acceptResponse(byteArrayOf(20, 0, 0x81.toByte()))
        }
        assertFailsWith<UmpFramingException> { assembler.finish() }
    }

    @Test
    fun partLimitCountsRepeatedResponseHeaderAndCompletedParts() {
        val assembler = UmpResponseAssembler(maxPartsPerResponse = 2)
        val result = assembler.acceptResponse(byteArrayOf(20, 0, 21, 0))
        assertEquals(listOf(20, 21), result.map { it.type })
        assertFailsWith<UmpFramingException> {
            assembler.acceptResponse(byteArrayOf(20, 0, 21, 0, 22, 0))
        }
    }
}
