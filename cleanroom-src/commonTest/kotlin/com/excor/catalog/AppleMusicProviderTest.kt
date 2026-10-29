package com.excor.catalog

import com.excor.fixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AppleMusicProviderTest {
    @Test
    fun searchNormalizesTrackWithoutExposingPreviewAudio() = runTest {
        var requestHost: String? = null
        var requestPath: String? = null
        var requestTerm: String? = null
        var requestEntity: String? = null
        var requestLimit: String? = null
        val client = HttpClient(MockEngine { request ->
            requestHost = request.url.host
            requestPath = request.url.encodedPath
            requestTerm = request.url.parameters["term"]
            requestEntity = request.url.parameters["entity"]
            requestLimit = request.url.parameters["limit"]
            respond(
                fixture("apple-music/search-track.json"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val page = ExcorMusicCatalog(client).search(
                provider = ExcorCatalogProvider.APPLE_MUSIC,
                query = "North Loop",
                kind = ExcorCatalogKind.TRACK,
                limit = 2,
            )
            val item = page.items.single()

            assertEquals("itunes.apple.com", requestHost)
            assertEquals("/search", requestPath)
            assertEquals("North Loop", requestTerm)
            assertEquals("musicTrack", requestEntity)
            assertEquals("2", requestLimit)
            assertEquals(ExcorCatalogProvider.APPLE_MUSIC, item.provider)
            assertEquals(ExcorCatalogKind.TRACK, item.kind)
            assertEquals("740001", item.id)
            assertEquals("North Loop", item.title)
            assertEquals("740003", item.albumId)
            assertEquals("Night Signals", item.albumTitle)
            assertEquals(187_000L, item.durationMs)
            assertEquals(3, item.trackNumber)
            assertEquals(1, item.discNumber)
            assertEquals(1, item.artwork.size)
            assertFalse(item.toString().contains("preview"))
            assertTrue(page.nextPageToken == null)
        } finally {
            client.close()
        }
    }

    @Test
    fun lookupRejectsUntrustedUrlBeforeMakingARequest() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("{}", HttpStatusCode.OK)
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).lookup("https://example.org/track/740001")
                fail("Untrusted URL should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_URL, error.error)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun appleInvalidSearchBoundsAndMalformedIdsFailBeforeNetwork() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("{}", HttpStatusCode.OK)
        })
        val catalog = ExcorMusicCatalog(client)

        try {
            for (query in listOf("   ", "x".repeat(201))) {
                val error = try {
                    catalog.search(ExcorCatalogProvider.APPLE_MUSIC, query, ExcorCatalogKind.TRACK)
                    fail("Invalid Apple query should be rejected")
                } catch (error: ExcorCatalogException) {
                    error
                }
                assertEquals(ExcorCatalogError.INVALID_QUERY, error.error)
            }
            for (limit in listOf(0, 201)) {
                val error = try {
                    catalog.search(ExcorCatalogProvider.APPLE_MUSIC, "North Loop", ExcorCatalogKind.TRACK, limit)
                    fail("Out-of-range Apple limit should be rejected")
                } catch (error: ExcorCatalogException) {
                    error
                }
                assertEquals(ExcorCatalogError.INVALID_QUERY, error.error)
            }
            val urlError = try {
                catalog.lookup("https://music.apple.com/us/album/night-signals/not-a-numeric-id")
                fail("Malformed Apple catalog ID should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_URL, urlError.error)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedAppleJsonFailsSafely() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                "{\"resultCount\":0}",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).search(ExcorCatalogProvider.APPLE_MUSIC, "North Loop", ExcorCatalogKind.TRACK)
                fail("Missing results array should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, error.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun albumTracksAreSortedByDiscAndTrackAndExcludeOtherAlbums() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/lookup", request.url.encodedPath)
            assertEquals("740003", request.url.parameters["id"])
            assertEquals("song", request.url.parameters["entity"])
            respond(
                fixture("apple-music/album-tracks.json"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val page = ExcorMusicCatalog(client).albumTracks(ExcorCatalogProvider.APPLE_MUSIC, "740003")
            assertEquals(listOf("740001", "740002", "740004"), page.items.map { it.id })
            assertEquals(listOf(1, 2, 1), page.items.map { it.trackNumber })
            assertEquals(listOf(1, 1, 2), page.items.map { it.discNumber })
        } finally {
            client.close()
        }
    }

    @Test
    fun artistAlbumsKeepOnlyAlbumsCreditedToThatArtist() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/lookup", request.url.encodedPath)
            assertEquals("740002", request.url.parameters["id"])
            assertEquals("album", request.url.parameters["entity"])
            respond(
                fixture("apple-music/artist-albums.json"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val page = ExcorMusicCatalog(client).artistAlbums(ExcorCatalogProvider.APPLE_MUSIC, "740002")
            assertEquals(listOf("740003", "740005"), page.items.map { it.id })
            assertTrue(page.items.all { it.kind == ExcorCatalogKind.ALBUM })
        } finally {
            client.close()
        }
    }

    @Test
    fun lookupResolvesTrackIdFromAppleAlbumShareUrl() = runTest {
        var requestedId: String? = null
        var requestedEntity: String? = null
        val client = HttpClient(MockEngine { request ->
            assertEquals("itunes.apple.com", request.url.host)
            assertEquals("/lookup", request.url.encodedPath)
            requestedId = request.url.parameters["id"]
            requestedEntity = request.url.parameters["entity"]
            respond(
                fixture("apple-music/search-track.json"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val item = ExcorMusicCatalog(client)
                .lookup("https://music.apple.com/us/album/night-signals/740003?i=740001")
            assertEquals("740001", requestedId)
            assertEquals("song", requestedEntity)
            assertEquals("740001", item?.id)
            assertEquals(ExcorCatalogKind.TRACK, item?.kind)
        } finally {
            client.close()
        }
    }

    @Test
    fun searchMapsAlbumAndArtistResources() = runTest {
        val client = HttpClient(MockEngine { request ->
            val fixtureName = when (request.url.parameters["entity"]) {
                "album" -> "apple-music/search-album.json"
                "musicArtist" -> "apple-music/search-artist.json"
                else -> error("unexpected entity")
            }
            respond(
                fixture(fixtureName),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val catalog = ExcorMusicCatalog(client)
            val album = catalog.search(ExcorCatalogProvider.APPLE_MUSIC, "Night Signals", ExcorCatalogKind.ALBUM).items.single()
            val artist = catalog.search(ExcorCatalogProvider.APPLE_MUSIC, "June Harbor", ExcorCatalogKind.ARTIST).items.single()
            assertEquals(ExcorCatalogKind.ALBUM, album.kind)
            assertEquals("9", album.trackCount?.toString())
            assertEquals("740002", album.artists.single().id)
            assertEquals(ExcorCatalogKind.ARTIST, artist.kind)
            assertEquals("Electronic", artist.genres.single())
            assertEquals("https://music.apple.com/us/artist/june-harbor/740002", artist.url)
        } finally {
            client.close()
        }
    }

    @Test
    fun pageTokenCannotBeReusedForAnotherProvider() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("{}", HttpStatusCode.OK)
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).search(
                    ExcorCatalogProvider.APPLE_MUSIC,
                    "North Loop",
                    ExcorCatalogKind.TRACK,
                    pageToken = "SPOTIFY|search:TRACK:North Loop|25",
                )
                fail("A page token from another provider should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_PAGE_TOKEN, error.error)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun appleRateLimitReturnsTypedStatusWithoutResponseBody() = runTest {
        val client = HttpClient(MockEngine {
            respond("private response body", HttpStatusCode.TooManyRequests)
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).search(ExcorCatalogProvider.APPLE_MUSIC, "North Loop", ExcorCatalogKind.TRACK)
                fail("HTTP 429 should be surfaced as a rate limit")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.RATE_LIMITED, error.error)
            assertEquals(429, error.statusCode)
            assertFalse("private response body" in error.toString())
        } finally {
            client.close()
        }
    }

    @Test
    fun declaredOversizedResponseIsRejectedBeforeJsonParsing() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                " ".repeat(2 * 1024 * 1024 + 1),
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ContentLength to listOf((2 * 1024 * 1024 + 1).toString()),
                ),
            )
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).search(ExcorCatalogProvider.APPLE_MUSIC, "North Loop", ExcorCatalogKind.TRACK)
                fail("Oversized response should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.RESPONSE_TOO_LARGE, error.error)
        } finally {
            client.close()
        }
    }
}
