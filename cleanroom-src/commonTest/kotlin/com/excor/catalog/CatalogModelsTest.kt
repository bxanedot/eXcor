package com.excor.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest

class CatalogModelsTest {
    @Test
    fun catalogStringsDoNotExposeSignedUrlsOrPageTokens() {
        val secret = "session-secret-value"
        val item = ExcorCatalogItem(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            kind = ExcorCatalogKind.TRACK,
            id = "740001",
            title = "North Loop",
            url = "https://music.apple.com/track?id=$secret",
            artwork = listOf(ExcorArtwork("https://images.example/image?token=$secret", 100, 100)),
        )
        val page = ExcorCatalogPage(listOf(item), nextPageToken = secret)

        assertNotEquals(secret, item.toString())
        assertNotEquals(secret, page.toString())
        assertEquals(false, secret in item.toString())
        assertEquals(false, secret in page.toString())
    }

    @Test
    fun appleCapabilitiesAreSupportedAndUnapprovedMusicCatalogIsNot() {
        val client = HttpClient(MockEngine { error("Capability lookup must not request the network") })
        try {
            val catalog = ExcorMusicCatalog(client)
            assertEquals(
                ExcorCatalogCapabilityStatus.SUPPORTED,
                catalog.capabilities(ExcorCatalogProvider.APPLE_MUSIC)
                    .single { it.operation == ExcorCatalogOperation.SEARCH }.status,
            )
            assertEquals(
                ExcorCatalogCapabilityStatus.RESTRICTED,
                catalog.capabilities(ExcorCatalogProvider.YOUTUBE_MUSIC)
                    .single { it.operation == ExcorCatalogOperation.SEARCH }.status,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun providerAndKindArePartOfResourceIdentity() {
        val apple = ExcorCatalogItem(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            kind = ExcorCatalogKind.TRACK,
            id = "740001",
            title = "North Loop",
        )
        val deezer = ExcorCatalogItem(
            provider = ExcorCatalogProvider.DEEZER,
            kind = ExcorCatalogKind.TRACK,
            id = "740001",
            title = "North Loop",
        )
        val album = ExcorCatalogItem(
            provider = ExcorCatalogProvider.APPLE_MUSIC,
            kind = ExcorCatalogKind.ALBUM,
            id = "740001",
            title = "North Loop",
        )

        assertNotEquals(apple, deezer)
        assertNotEquals(apple, album)
        assertEquals("740001", apple.id)
    }

    @Test
    fun restrictedYouTubeMusicCatalogRequestsAreNotSent() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            error("Restricted provider must not make requests")
        })
        try {
            val error = try {
                ExcorMusicCatalog(client).search(
                    ExcorCatalogProvider.YOUTUBE_MUSIC,
                    "North Loop",
                    ExcorCatalogKind.TRACK,
                )
                error("The restricted provider should fail")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.RESTRICTED, error.error)
            assertEquals(0, calls)
        } finally {
            client.close()
        }
    }
}
