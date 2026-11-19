package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaHeadersTest {
    @Test
    fun mediaRequestsDoNotCarrySessionCredentials() {
        val headers = mediaHeaders(
            userAgent = "eXcor test",
            session = ExcorSession(
                cookie = "SAPISID=synthetic-secret",
                visitorData = "synthetic-visitor",
                region = "US",
                language = "en",
            ),
        )

        assertEquals("eXcor test", headers["User-Agent"])
        assertFalse("Cookie" in headers)
        assertFalse("X-Goog-Visitor-Id" in headers)
    }
}
