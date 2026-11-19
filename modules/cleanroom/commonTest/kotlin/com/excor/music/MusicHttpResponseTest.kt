package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MusicHttpResponseTest {
    @Test
    fun emptyCatalogResponseIsMalformedAndCannotPoisonTheCache() = runTest {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        for ((body, headers) in listOf("" to jsonHeaders, " \t\r\n" to jsonHeaders,
            "" to headersOf(HttpHeaders.ContentLength, "0"))) {
            var requests = 0
            val client = HttpClient(MockEngine {
                requests++
                if (requests == 1) respond(body, HttpStatusCode.OK, headers)
                else respond("{\"items\":[]}", HttpStatusCode.OK, jsonHeaders)
            })
            try {
                val http = MusicHttp(client, { 0L })
                val url = "https://api.spotify.com/v1/search?q=synthetic"
                val failure = assertFailsWith<ExcorCatalogException> {
                    http.getJson(ExcorCatalogProvider.SPOTIFY, url)
                }
                assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, failure.error)
                val expected = Json.parseToJsonElement("{\"items\":[]}")
                assertEquals(expected, http.getJson(ExcorCatalogProvider.SPOTIFY, url))
                assertEquals(expected, http.getJson(ExcorCatalogProvider.SPOTIFY, url))
                assertEquals(2, requests, "Only the subsequent valid JSON response may be cached")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun malformedUtf8IsRejectedWithoutCachingOrExposingResponseText() = runTest {
        val invalidSequences = listOf(
            byteArrayOf(0x80.toByte()), // lone continuation
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()), // overlong encoding
            byteArrayOf(0xe2.toByte(), 0x82.toByte()), // truncated sequence
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()), // surrogate
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), // above U+10FFFF
        )
        val valid = "{\"name\":\"音楽 🎵 �\"}"
        val expected = Json.parseToJsonElement(valid)
        for (invalid in invalidSequences) {
            var requests = 0
            val client = HttpClient(MockEngine {
                requests++
                val body = if (requests == 1) {
                    "{\"name\":\"synthetic-private-".encodeToByteArray() + invalid + "\"}".encodeToByteArray()
                } else valid.encodeToByteArray()
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                val http = MusicHttp(client, { 0L })
                val url = "https://api.spotify.com/v1/search?q=synthetic"
                val failure = assertFailsWith<ExcorCatalogException> {
                    http.getJson(ExcorCatalogProvider.SPOTIFY, url)
                }
                assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, failure.error)
                assertFalse(failure.toString().contains("synthetic-private"))
                assertEquals(null, failure.cause)
                assertEquals(expected, http.getJson(ExcorCatalogProvider.SPOTIFY, url))
                assertEquals(expected, http.getJson(ExcorCatalogProvider.SPOTIFY, url))
                assertEquals(2, requests, "Only valid UTF-8 JSON may be cached")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun nonemptyFormsAndCommandsAlsoRequireValidUtf8() = runTest {
        val body = "{\"value\":\"".encodeToByteArray() + byteArrayOf(0xff.toByte()) + "\"}".encodeToByteArray()
        val client = HttpClient(MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val http = MusicHttp(client, { 0L })
            val formFailure = assertFailsWith<ExcorCatalogException> {
                http.postForm(ExcorCatalogProvider.SPOTIFY, "https://accounts.spotify.com/api/token",
                    mapOf("grant_type" to "synthetic"))
            }
            assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, formFailure.error)
            val commandFailure = assertFailsWith<ExcorCatalogException> {
                http.command(ExcorCatalogProvider.SPOTIFY, HttpMethod.Put,
                    "https://api.spotify.com/v1/me/player", "synthetic")
            }
            assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, commandFailure.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun tokenFormDoesNotAcceptAnEmptySuccess() = runTest {
        val client = HttpClient(MockEngine {
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val http = MusicHttp(client, { 0L })
            val failure = assertFailsWith<ExcorCatalogException> {
                http.postForm(ExcorCatalogProvider.SPOTIFY, "https://accounts.spotify.com/api/token",
                    mapOf("grant_type" to "synthetic"))
            }
            assertEquals(ExcorCatalogError.MALFORMED_RESPONSE, failure.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun emptyCommandsAndExplicitNoContentReadsRemainSupported() = runTest {
        val empty = JsonObject(emptyMap())
        for (headers in listOf(headersOf(HttpHeaders.ContentType, "application/json"),
            headersOf(HttpHeaders.ContentLength, "0"))) {
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) respond("", HttpStatusCode.NoContent)
                else respond("", HttpStatusCode.OK, headers)
            })
            try {
                val http = MusicHttp(client, { 0L })
                val url = "https://api.spotify.com/v1/me/player"
                assertEquals(empty, http.command(ExcorCatalogProvider.SPOTIFY, HttpMethod.Put, url, "synthetic"))
                assertEquals(empty, http.getJson(ExcorCatalogProvider.SPOTIFY, url, bearer = "synthetic"))
            } finally {
                client.close()
            }
        }
    }
}
