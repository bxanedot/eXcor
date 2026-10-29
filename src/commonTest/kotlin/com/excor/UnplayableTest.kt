package com.excor

import com.excor.ExcorUnplayableReason.AGE_RESTRICTED
import com.excor.ExcorUnplayableReason.GEO_RESTRICTED
import com.excor.ExcorUnplayableReason.OTHER
import com.excor.ExcorUnplayableReason.SIGN_IN_REQUIRED
import com.excor.ExcorUnplayableReason.UNAVAILABLE
import com.excor.ExcorUnplayableReason.UPCOMING
import com.excor.internal.unplayableOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UnplayableTest {
    private fun refusal(json: String) = unplayableOf("v", Json.parseToJsonElement(json).jsonObject)

    @Test
    fun capturedRefusalsAreSorted() {
        // What the owner's network got logged out (fixtures/README.md), with YouTube's reasons as they came.
        listOf(
            Triple("age", AGE_RESTRICTED, "LOGIN_REQUIRED: Sign in to confirm your age"),
            Triple("not-found", UNAVAILABLE, "ERROR: This video is unavailable"),
            Triple("upcoming", UPCOMING, "LIVE_STREAM_OFFLINE: This live event will begin in 27 hours."),
            Triple("live", OTHER, "UNPLAYABLE: This live stream recording is not available."),
            Triple("blocked", OTHER, "UNPLAYABLE: It was blocked due to the claimed content by SME."),
        ).forEach { (case, reason, said) ->
            val error = unplayableOf("v", fixtureJson("player-unplayable-$case.json"))!!
            assertEquals(reason, error.reason, case)
            assertEquals(said, "${error.status}: ${error.youtubeReason}", case)
        }
        assertNull(unplayableOf("v", fixtureJson("player-response-visionos.json")))
    }

    @Test
    fun countryBlocksAndSignInsFollowYtDlp() {
        // The shape yt-dlp reads for a country block (errorScreen.playerErrorMessageRenderer.subreason).
        val country = refusal(
            """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Video unavailable","errorScreen":{"playerErrorMessageRenderer":""" +
                """{"subreason":{"runs":[{"text":"The uploader has not made this video available in your country"}]}}}}}""",
        )!!
        assertEquals(GEO_RESTRICTED, country.reason)
        assertEquals("Video unavailable. The uploader has not made this video available in your country", country.youtubeReason)
        assertEquals(SIGN_IN_REQUIRED, refusal("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you’re not a bot"}}""")!!.reason)
        // yt-dlp's age statuses, whatever the reason says.
        assertEquals(AGE_RESTRICTED, refusal("""{"playabilityStatus":{"status":"AGE_VERIFICATION_REQUIRED"}}""")!!.reason)
    }

    @Test
    fun extractThrowsTheFirstRefusal() = runTest {
        val error = assertFailsWith<ExcorUnplayableException> {
            fixtureEngine(FakeHost(), playerFixture = "player-unplayable-age.json").extract("qpgTC9MDx1o", 160, emptySet(), false)
        }
        assertEquals(AGE_RESTRICTED, error.reason)
        assertEquals("Excor: video qpgTC9MDx1o is unplayable (LOGIN_REQUIRED: Sign in to confirm your age)", error.message)
    }
}
