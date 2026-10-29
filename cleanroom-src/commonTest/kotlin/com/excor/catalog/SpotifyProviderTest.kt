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
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class SpotifyProviderTest {
    @Test
    fun searchUsesHostTokenAndNormalizesTrackMetadata() = runTest {
        var requestType: String? = null
        var requestLimit: String? = null
        var requestOffset: String? = null
        var authorization: String? = null
        val client = HttpClient(MockEngine { request ->
            requestType = request.url.parameters["type"]
            requestLimit = request.url.parameters["limit"]
            requestOffset = request.url.parameters["offset"]
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                fixture("spotify/search-track.json"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val tokenProvider = ExcorCatalogTokenProvider { provider ->
            assertEquals(ExcorCatalogProvider.SPOTIFY, provider)
            "host-owned-test-token"
        }

        try {
            val page = ExcorMusicCatalog(client, tokenProvider).search(
                provider = ExcorCatalogProvider.SPOTIFY,
                query = "North Loop",
                kind = ExcorCatalogKind.TRACK,
                limit = 1,
            )
            val item = page.items.single()
            assertEquals("track", requestType)
            assertEquals("1", requestLimit)
            assertEquals("0", requestOffset)
            assertEquals("Bearer host-owned-test-token", authorization)
            assertEquals("4iV5W9uYEdYUVa79Axb7Rh", item.id)
            assertEquals("North Loop", item.title)
            assertEquals(ExcorCatalogProvider.SPOTIFY, item.provider)
            assertEquals("2222222222222222222222", item.artists.single().id)
            assertEquals("Night Signals", item.albumTitle)
            assertEquals(187_000L, item.durationMs)
            assertEquals(2, item.artwork.size)
            assertEquals(false, item.isExplicit)
            assertFalse(item.toString().contains("preview"))
            assertTrue(page.nextPageToken != null)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyPageTokenIsBoundToProviderAndQuery() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(fixture("spotify/search-track.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "test-token" })

        try {
            val error = try {
                catalog.search(
                    provider = ExcorCatalogProvider.SPOTIFY,
                    query = "Different query",
                    kind = ExcorCatalogKind.TRACK,
                    limit = 1,
                    pageToken = "SPOTIFY|TRACK|North Loop|1",
                )
                fail("A token from another query should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_PAGE_TOKEN, error.error)
            assertEquals(0, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifySearchPaginationAdvancesByRequestedPageSize() = runTest {
        val offsets = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            val offset = request.url.parameters["offset"]
            offsets += offset
            val fixtureName = if (offset == "0") "spotify/search-track.json" else "spotify/search-track-page-2.json"
            respond(fixture(fixtureName), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token" })

        try {
            val first = catalog.search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK, limit = 1)
            val second = catalog.search(
                ExcorCatalogProvider.SPOTIFY,
                "North Loop",
                ExcorCatalogKind.TRACK,
                limit = 1,
                pageToken = first.nextPageToken,
            )
            assertEquals(listOf("0", "1"), offsets.map { it ?: "missing" })
            assertEquals("5555555555555555555555", second.items.single().id)
            assertEquals(null, second.nextPageToken)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyLookupResolvesTrackLinkThroughApi() = runTest {
        var requestHost: String? = null
        var requestPath: String? = null
        val client = HttpClient(MockEngine { request ->
            requestHost = request.url.host
            requestPath = request.url.encodedPath
            respond(fixture("spotify/lookup-track.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        try {
            val item = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "host-token" })
                .lookup("https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh?si=share-id")
            assertEquals("api.spotify.com", requestHost)
            assertEquals("/v1/tracks/4iV5W9uYEdYUVa79Axb7Rh", requestPath)
            assertEquals("North Loop", item?.title)
            assertEquals("Night Signals", item?.albumTitle)
        } finally {
            client.close()
        }
    }

    @Test
    fun missingSpotifyTokenIsAuthenticationRequiredWithoutNetworkRequest() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("{}", HttpStatusCode.OK)
        })

        try {
            val error = try {
                ExcorMusicCatalog(client).search(
                    ExcorCatalogProvider.SPOTIFY,
                    "North Loop",
                    ExcorCatalogKind.TRACK,
                )
                fail("Spotify requests without a host token should fail")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, error.error)
            assertEquals(0, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyCapabilitiesReflectWhetherHostSuppliesTokens() = runTest {
        val client = HttpClient(MockEngine { error("Capability lookup must not request the network") })
        try {
            assertEquals(
                ExcorCatalogCapabilityStatus.AUTHENTICATION_REQUIRED,
                ExcorMusicCatalog(client).capabilities(ExcorCatalogProvider.SPOTIFY)
                    .single { it.operation == ExcorCatalogOperation.SEARCH }.status,
            )
            assertEquals(
                ExcorCatalogCapabilityStatus.SUPPORTED,
                ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token" })
                    .capabilities(ExcorCatalogProvider.SPOTIFY)
                    .single { it.operation == ExcorCatalogOperation.SEARCH }.status,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifySearchAndCatalogCollectionsPreserveResourceKinds() = runTest {
        val client = HttpClient(MockEngine { request ->
            val fixtureName = when {
                request.url.parameters["type"] == "album" -> "spotify/search-album.json"
                request.url.parameters["type"] == "artist" -> "spotify/search-artist.json"
                request.url.encodedPath.endsWith("/tracks") -> "spotify/album-tracks.json"
                request.url.encodedPath.endsWith("/albums") -> "spotify/artist-albums.json"
                else -> error("unexpected Spotify request")
            }
            respond(fixture(fixtureName), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "test-token" })

        try {
            val album = catalog.search(ExcorCatalogProvider.SPOTIFY, "Night Signals", ExcorCatalogKind.ALBUM).items.single()
            val artist = catalog.search(ExcorCatalogProvider.SPOTIFY, "June Harbor", ExcorCatalogKind.ARTIST).items.single()
            val tracks = catalog.albumTracks(ExcorCatalogProvider.SPOTIFY, "3333333333333333333333")
            val albums = catalog.artistAlbums(ExcorCatalogProvider.SPOTIFY, "2222222222222222222222")
            assertEquals(ExcorCatalogKind.ALBUM, album.kind)
            assertEquals(9, album.trackCount)
            assertEquals("June Harbor", album.artists.single().name)
            assertEquals(ExcorCatalogKind.ARTIST, artist.kind)
            assertEquals(listOf("4iV5W9uYEdYUVa79Axb7Rh"), tracks.items.map { it.id })
            assertEquals("3333333333333333333333", tracks.items.single().albumId)
            assertEquals(listOf("3333333333333333333333"), albums.items.map { it.id })
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyHttpErrorsAreTypedAndDoNotLeakResponseBodiesOrTokens() = runTest {
        val client = HttpClient(MockEngine {
            respond("private provider payload", HttpStatusCode.Unauthorized)
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "private-token" })

        try {
            val error = try {
                catalog.search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
                fail("401 should require authentication")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.AUTHENTICATION_REQUIRED, error.error)
            assertEquals(401, error.statusCode)
            assertFalse("private provider payload" in error.toString())
            assertFalse("private-token" in error.toString())
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyForbiddenAndRateLimitedResponsesAreTyped() = runTest {
        val cases = listOf(
            HttpStatusCode.Forbidden to ExcorCatalogError.ACCESS_DENIED,
            HttpStatusCode.TooManyRequests to ExcorCatalogError.RATE_LIMITED,
        )
        for ((status, expected) in cases) {
            val client = HttpClient(MockEngine { respond("private provider payload", status) })
            try {
                val error = try {
                    ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token" })
                        .search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
                    fail("Provider status $status should fail")
                } catch (error: ExcorCatalogException) {
                    error
                }
                assertEquals(expected, error.error)
                assertEquals(status.value, error.statusCode)
                assertFalse("private provider payload" in error.toString())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun malformedSpotifySearchResponseFailsSafely() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                "{\"unexpected\": {}}",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        try {
            val error = try {
                ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token" })
                    .search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
                fail("A missing resource page should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, error.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyInvalidIdsAreRejectedBeforeRequest() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("{}", HttpStatusCode.OK)
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token" })

        try {
            val error = try {
                catalog.lookup("https://open.spotify.com/track/not-an-id")
                fail("Invalid Spotify ID should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_URL, error.error)
            assertEquals(0, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyInvalidSearchLimitsDoNotRequestTokenOrNetwork() = runTest {
        var tokenCalls = 0
        var networkCalls = 0
        val client = HttpClient(MockEngine {
            networkCalls++
            respond("{}", HttpStatusCode.OK)
        })
        val catalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider {
            tokenCalls++
            "token"
        })

        try {
            val error = try {
                catalog.search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK, limit = 51)
                fail("Spotify limit above 50 should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_QUERY, error.error)
            assertEquals(0, tokenCalls)
            assertEquals(0, networkCalls)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyTokenProviderFailureIsSafeAndCancellationIsPreserved() = runTest {
        var networkCalls = 0
        val client = HttpClient(MockEngine {
            networkCalls++
            respond("{}", HttpStatusCode.OK)
        })

        try {
            val failedTokenCatalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider {
                error("private credential source detail")
            })
            val error = try {
                failedTokenCatalog.search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
                fail("Credential provider failure should be typed")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.CREDENTIAL_PROVIDER_FAILED, error.error)
            assertFalse("private credential source detail" in error.toString())
            assertEquals(0, networkCalls)

            val cancelledCatalog = ExcorMusicCatalog(client, ExcorCatalogTokenProvider {
                throw CancellationException("cancel token lookup")
            })
            assertFailsWith<CancellationException> {
                cancelledCatalog.search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
            }
            assertEquals(0, networkCalls)
        } finally {
            client.close()
        }
    }

    @Test
    fun spotifyCredentialRejectsHeaderInjectionBeforeRequest() = runTest {
        var networkCalls = 0
        val client = HttpClient(MockEngine {
            networkCalls++
            respond("{}", HttpStatusCode.OK)
        })

        try {
            val error = try {
                ExcorMusicCatalog(client, ExcorCatalogTokenProvider { "token\r\nX-Injected: true" })
                    .search(ExcorCatalogProvider.SPOTIFY, "North Loop", ExcorCatalogKind.TRACK)
                fail("A token with header controls should be rejected")
            } catch (error: ExcorCatalogException) {
                error
            }
            assertEquals(ExcorCatalogError.INVALID_CREDENTIAL, error.error)
            assertEquals(0, networkCalls)
        } finally {
            client.close()
        }
    }
}
