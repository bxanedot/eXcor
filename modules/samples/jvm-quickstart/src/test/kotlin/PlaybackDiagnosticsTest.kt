import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class PlaybackDiagnosticsTest {
    @Test
    fun relayByteBudgetReservesAcrossConcurrentRequestsWithoutExceedingItsLimit() {
        val budget = RelayByteBudget(10)
        val first = budget.reserve(8)!!
        val second = budget.reserve(8)!!

        assertEquals(first.bytes, 8)
        assertEquals(second.bytes, 2)
        assertNull(budget.reserve(1))
        budget.complete(first, 8)
        budget.complete(second, 2)

        assertEquals(budget.consumedBytes(), 10L)
        assertTrue(budget.isExceeded())
    }

    @Test
    fun unusedRelayReservationReturnsCapacityToTheSharedBudget() {
        val budget = RelayByteBudget(10)
        val first = budget.reserve(8)!!
        budget.complete(first, 3)

        val second = budget.reserve(8)!!
        assertEquals(second.bytes, 7)
        budget.complete(second, 7)

        assertEquals(budget.consumedBytes(), 10L)
        assertTrue(budget.isExceeded())
    }

    @Test
    fun processStopsAsSoonAsTheSharedByteBudgetIsExceeded() {
        val process = WaitingProcess()

        assertEquals(waitForProcess(process, 60) { true }, "byte_limit")
        assertFalse(process.isAlive)
    }

    @Test
    fun ffplayUsesAnInteractiveWaveformWindowForSeekKeys() {
        val url = "http://127.0.0.1:49152/media/test"
        val command = ffplayArguments(url)

        assertEquals(command.first(), "ffplay")
        assertEquals(command.drop(1).take(4), listOf("-ss", "0", "-showmode", "1"))
        assertFalse(command.contains("-nodisp"))
        assertEquals(command.last(), url)
    }

    @Test
    fun ffplayReceivesTheRequestedSeekOffsetBeforeTheMediaUrl() {
        val command = ffplayArguments("http://127.0.0.1:49152/media/test", 30)

        assertEquals(command.drop(1).take(2), listOf("-ss", "30"))
    }

    @Test
    fun mediaUrlAllowlistRejectsHostAndPathConfusion() {
        assertTrue(isAllowedMediaUrl("https://r1---sn-ab5l6.googlevideo.com/videoplayback?range=1"))
        assertTrue(isAllowedMediaUrl("https://m.youtube.com/videoplayback?token=private"))
        assertFalse(isAllowedMediaUrl("http://r1---sn-ab5l6.googlevideo.com/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com.attacker.example/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://googlevideo.com.attacker.example/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/other"))
        assertFalse(isAllowedMediaUrl("https://user@youtube.com/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com:444/videoplayback"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/videoplayback/extra"))
        assertFalse(isAllowedMediaUrl("https://youtube.com/videoplayback#fragment"))
    }

    @Test
    fun requestHeadersDropCredentialsVisitorIdsAndControlCharacters() {
        val forwarded = safeRequestHeaders(
            mapOf(
                "User-Agent" to "eXcor playback runner",
                "Accept" to "*/*",
                "Cookie" to "SID=secret-cookie",
                "Authorization" to "Bearer secret-token",
                "X-Goog-Visitor-Id" to "secret-visitor-id",
                "Referer" to "https://youtube.com/watch?v=valid1234567\r\nCookie: injected",
            ),
        )

        assertEquals(forwarded.keys, setOf("User-Agent", "Accept"))
        assertEquals(forwarded["User-Agent"], "eXcor playback runner")
        assertFalse(forwarded.values.any { it.contains("secret") || it.contains("\r") || it.contains("\n") })
    }

    @Test
    fun rangeParserAcceptsSingleByteRangesAndRejectsMalformedOrOverflowingValues() {
        assertTrue(isValidRange("bytes=0-"))
        assertTrue(isValidRange("bytes=15-99"))
        assertTrue(isValidRange("bytes=-1024"))
        assertEquals(parseRangeStart("bytes=15-99"), 15L)
        assertNull(parseRangeStart("bytes=-1024"))

        assertFalse(isValidRange("bytes=1-2,4-5"))
        assertFalse(isValidRange("bytes=99-15"))
        assertFalse(isValidRange("bytes=-0"))
        assertFalse(isValidRange("bytes=9223372036854775808-"))
        assertFalse(isValidRange("bytes=" + "1".repeat(65) + "-"))
    }

    @Test
    fun playabilityClassificationNeverReturnsUnknownRemoteText() {
        assertEquals(classifyPlayabilityStatus("OK"), "OK")
        assertEquals(classifyPlayabilityStatus("LOGIN_REQUIRED"), "LOGIN_REQUIRED")
        assertEquals(classifyPlayabilityStatus("UNRECOGNIZED:https://example.test/?token=secret"), "OTHER")
        assertNull(classifyPlayabilityStatus(null))
    }

    private class WaitingProcess : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = if (isAlive) throw IllegalThreadStateException() else 143
        override fun destroy() { alive = false }
        override fun destroyForcibly(): Process { alive = false; return this }
        override fun isAlive(): Boolean = alive
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !alive

        private var alive = true
    }
}
