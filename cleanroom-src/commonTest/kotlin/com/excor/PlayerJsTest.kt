package com.excor

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerJsTest {
    @Test
    fun executesSignatureAndThrottleChallenges() = runTest {
        val output = solvePlayerJs(
            playerScript = null,
            preparedPlayer = "_result.sig = sig => sig.split('').reverse().join(''); _result.n = n => n + 'x';",
            signatures = listOf("abcdef"),
            throttling = listOf("123"),
        )

        assertEquals("fedcba", output?.signatures?.get("abcdef"))
        assertEquals("123x", output?.throttling?.get("123"))
    }

    @Test
    fun playerFunctionsCanUseUrlAndSearchParams() = runTest {
        val output = solvePlayerJs(
            playerScript = null,
            preparedPlayer = "_result.n = n => new URL('https://rr.googlevideo.com/videoplayback?n=' + n).searchParams.get('n');",
            signatures = emptyList(),
            throttling = listOf("abc"),
        )

        assertEquals("abc", output?.throttling?.get("abc"))
    }

    @Test
    fun cancellationInterruptsChallengeExecution() = runTest {
        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
            withTimeout(100) {
                solvePlayerJs(
                    playerScript = null,
                    preparedPlayer = "_result.sig = sig => { while (true) {} };",
                    signatures = listOf("x"),
                    throttling = emptyList(),
                )
            }
        }
    }
}
