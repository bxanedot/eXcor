package com.excor.internal.sabr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Synthetic, clean-room response bodies; no captured streams or session data. */
class UmpResponseAssemblyTest {
    @Test
    fun assemblesPartialMediaAcrossTwoHttpResponsesWithoutAppendingTheNewHeader() {
        val decoder = UmpResponseAssembler()
        val first = decoder.acceptResponse(byteArrayOf(20, 1, 7, 21, 5, 1, 2))
        assertEquals(listOf(20), first.map { it.type })
        assertContentEquals(byteArrayOf(7), first.single().payload)

        val second = decoder.acceptResponse(byteArrayOf(20, 0, 21, 3, 3, 4, 5, 22, 0))
        assertEquals(listOf(20, 21, 22), second.map { it.type })
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), second[1].payload)
        decoder.finish()
    }

    @Test
    fun canReassembleOneMediaPartOverThreeResponses() {
        val decoder = UmpResponseAssembler()
        assertEquals(1, decoder.acceptResponse(byteArrayOf(20, 0, 21, 6, 1, 2)).size)
        assertEquals(1, decoder.acceptResponse(byteArrayOf(20, 0, 21, 4, 3, 4)).size)
        val last = decoder.acceptResponse(byteArrayOf(20, 0, 21, 2, 5, 6, 22, 0))
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), last[1].payload)
        assertEquals(22, last[2].type)
        decoder.finish()
    }

    @Test
    fun completeResponsesAndEmptyPayloadsWorkIndependently() {
        val decoder = UmpResponseAssembler()
        val first = decoder.acceptResponse(byteArrayOf(20, 0, 21, 2, 42, 43, 22, 0))
        assertEquals(listOf(20, 21, 22), first.map { it.type })
        assertContentEquals(byteArrayOf(42, 43), first[1].payload)
        assertTrue(first[2].payload.isEmpty())
        val second = decoder.acceptResponse(byteArrayOf(20, 0, 22, 0))
        assertEquals(listOf(20, 22), second.map { it.type })
        decoder.finish()
    }

    @Test
    fun missingOrMismatchedContinuationsFailClosed() {
        fun pending() = UmpResponseAssembler().apply {
            acceptResponse(byteArrayOf(20, 0, 21, 5, 1, 2))
        }
        assertFailsWith<UmpFramingException> {
            pending().acceptResponse(byteArrayOf(20, 0))
        }
        assertFailsWith<UmpFramingException> {
            pending().acceptResponse(byteArrayOf(20, 0, 22, 3, 3, 4, 5))
        }
        assertFailsWith<UmpFramingException> {
            pending().acceptResponse(byteArrayOf(20, 0, 21, 2, 3, 4))
        }
        assertFailsWith<UmpFramingException> {
            pending().finish()
        }
    }

    @Test
    fun rejectsMissingAndTruncatedResponseHeaders() {
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf())
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf(21, 0))
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf(20, 2, 1))
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf(0xf8.toByte(), 0))
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf(0x81.toByte()))
        }
    }

    @Test
    fun rejectsNonEmptyPartsThatMakeNoPayloadProgress() {
        val decoder = UmpResponseAssembler()
        decoder.acceptResponse(byteArrayOf(20, 0, 21, 5, 1, 2))

        assertFailsWith<UmpFramingException> {
            decoder.acceptResponse(byteArrayOf(20, 0, 21, 3))
        }
        assertFailsWith<UmpFramingException> { decoder.finish() }

        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler().acceptResponse(byteArrayOf(20, 0, 21, 3))
        }
    }

    @Test
    fun rejectsResponseAndPartSizeLimitsBeforeAllocating() {
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler(maxResponseBytes = 4)
                .acceptResponse(byteArrayOf(20, 0, 21, 1, 99))
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler(maxPartBytes = 2)
                .acceptResponse(byteArrayOf(20, 0, 21, 3))
        }
        assertFailsWith<UmpFramingException> {
            UmpResponseAssembler(maxPartBytes = 2)
                .acceptResponse(byteArrayOf(20, 0, 21, 0xf0.toByte(),
                    0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        }
        assertFailsWith<IllegalArgumentException> { UmpResponseAssembler(maxPartBytes = 0) }
    }

    @Test
    fun boundsPartsPerResponseAndPoisonsDecoderAfterMalformedData() {
        val decoder = UmpResponseAssembler(maxPartsPerResponse = 2)
        assertFailsWith<UmpFramingException> {
            decoder.acceptResponse(byteArrayOf(20, 0, 21, 0, 22, 0))
        }
        assertFailsWith<UmpFramingException> {
            decoder.acceptResponse(byteArrayOf(20, 0))
        }
        assertFailsWith<UmpFramingException> { decoder.finish() }
    }
}
