package com.excor.app

import com.excor.MusicCatalogImage
import com.excor.MusicCatalogItem
import com.excor.MusicCatalogKind
import com.excor.MusicCatalogProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    private fun item(vararg urls: String) = MusicCatalogItem(
        MusicCatalogProvider.DEEZER, MusicCatalogKind.TRACK, "3135556", "Harder, Better, Faster, Stronger", "https://www.deezer.com/track/3135556",
        images = urls.map { MusicCatalogImage(it, 1000, 1000) },
    )

    @Test
    fun onlyTheCatalogsOwnImageHostsOverHttps() {
        assertEquals("https://i.scdn.co/image/ab67616d0000b273", catalogArtworkUrl(item("https://i.scdn.co/image/ab67616d0000b273")))
        assertEquals("https://cdn-images.dzcdn.net/images/cover/x/1000x1000.jpg", catalogArtworkUrl(item("https://cdn-images.dzcdn.net/images/cover/x/1000x1000.jpg")))
        // The first image that passes is taken.
        assertEquals("https://i.scdn.co/a", catalogArtworkUrl(item("https://evil.example/a.jpg", "https://i.scdn.co/a")))
        listOf(
            "http://i.scdn.co/a", "https://scdn.co.evil.example/a", "https://evil.example/i.scdn.co/a",
            "https://user@i.scdn.co/a", "https://i.scdn.co:8443/a", "ftp://i.scdn.co/a", "",
        ).forEach { assertNull(catalogArtworkUrl(item(it)), it) }
        assertNull(catalogArtworkUrl(item()))
    }
}
