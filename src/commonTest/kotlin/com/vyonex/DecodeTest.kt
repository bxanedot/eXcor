package com.vyonex

import com.vyonex.internal.PlayerJs
import com.vyonex.internal.audioFormatsOf
import com.vyonex.internal.isAllowedUrl
import com.vyonex.internal.playableUrl
import com.vyonex.internal.preparePlayer
import com.vyonex.internal.signatureTimestamp
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DecodeTest {
    // Expected answers were checked live on 2026-10-26: the decoded URLs answered 206, undecoded ones 403.
    @Test
    fun decodesSignatureAndNAgainstCapturedPlayer() = runTest(timeout = 5.minutes) {
        val code = fixture("player-dce23b30.js")
        assertEquals(20746, signatureTimestamp(code))
        val js = PlayerJs.load(preparePlayer(code))
        val expected = fixtureJson("challenges-dce23b30.json")
        val n = expected.getValue("n").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val sig = expected.getValue("sig").jsonObject.mapValues { it.value.jsonPrimitive.content }
        n.forEach { (challenge, answer) -> assertEquals(answer, js.solve("n", challenge)) }
        sig.forEach { (challenge, answer) -> assertEquals(answer, js.solve("sig", challenge)) }

        val urls = audioFormatsOf(fixtureJson("player-response-tv_samsung.json"))
            .map { playableUrl(it) { type, challenge -> js.solve(type, challenge) }!! }
        assertEquals(4, urls.size)
        urls.forEach { url ->
            assertTrue(isAllowedUrl(url), url)
            assertTrue("&n=${n.values.single()}&" in url, url)
            assertTrue(sig.values.any { "&sig=${it.encodeURLParameter()}" in url }, url)
        }
        js.close()
    }
}
