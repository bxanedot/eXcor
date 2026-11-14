package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionValidationTest {
    @Test
    fun acceptsBoundedPrintableSessionValuesAndEmptyLocales() {
        val session = ExcorSession("SID=session; SAPISID=value", "visitor-data", "", "")
        val boundary = ExcorSession("c".repeat(8_192), "v".repeat(4_096), "r".repeat(64), "l".repeat(64))

        assertEquals("SID=session; SAPISID=value", session.cookie)
        assertEquals("visitor-data", session.visitorData)
        assertEquals("", session.region)
        assertEquals("", session.language)
        assertEquals(8_192, boundary.cookie?.length)
        assertEquals(4_096, boundary.visitorData?.length)
    }

    @Test
    fun rejectsControlsAndOversizedSessionValues() {
        assertFailsWith<IllegalArgumentException> { ExcorSession("SID=ok\r\nX-Injected: yes", null, "US", "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, "visitor\u007fdata", "US", "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, null, "US\nX-Injected: yes", "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, null, "US", "en\tX") }
        assertFailsWith<IllegalArgumentException> { ExcorSession("c".repeat(8_193), null, "US", "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, "v".repeat(4_097), "US", "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, null, "r".repeat(65), "en") }
        assertFailsWith<IllegalArgumentException> { ExcorSession(null, null, "US", "l".repeat(65)) }
    }
}
