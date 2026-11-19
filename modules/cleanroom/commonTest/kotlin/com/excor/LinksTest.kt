package com.excor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinksTest {
    @Test
    fun readsVideoIdsFromYouTubeLinks() {
        val id = "dQw4w9WgXcQ"
        listOf(
            id,
            "  $id ",
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?feature=share&v=$id&t=42",
            "youtube.com/watch?v=$id",
            "http://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://WWW.YOUTUBE.COM/watch?v=$id#t=10",
        ).forEach { assertEquals(id, excorVideoIdOf(it), it) }
    }

    @Test
    fun rejectsOtherHostsAndShapes() {
        listOf(
            "",
            "rick astley",
            "dQw4w9WgXc",
            "https://evil.example/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
            "https://user@youtube.com/watch?v=dQw4w9WgXcQ",
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/playlist?list=PL0123456789",
        ).forEach { assertNull(excorVideoIdOf(it), it) }
    }
}
