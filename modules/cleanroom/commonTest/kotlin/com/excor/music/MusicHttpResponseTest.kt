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
