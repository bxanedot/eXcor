package com.excor

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlParametersTest {
    @Test
    fun replacingChallengeKeepsOtherPlaybackParameters() {
        val original = "https://rr1---sn.googlevideo.com/videoplayback?c=WEB&n=old&range=0-100"

        val result = Url(setQueryParameter(original, "n", "new"))

        assertEquals("WEB", result.parameters["c"])
        assertEquals("new", result.parameters["n"])
        assertEquals("0-100", result.parameters["range"])
    }
}
