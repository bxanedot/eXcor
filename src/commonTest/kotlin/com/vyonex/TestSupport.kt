package com.vyonex

import kotlinx.coroutines.channels.Channel
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal fun fixture(name: String) = SystemFileSystem.source(Path(FIXTURES, name)).buffered().use { it.readString() }

internal fun fixtureJson(name: String): JsonObject = Json.parseToJsonElement(fixture(name)).jsonObject

internal class FakeHost : VyonExHostServices {
    var now = 1_000_000L
    var enabled = true
    /** Lets warm-up be observed without it reaching the network. */
    var failSession = false
    val sessionCalls = Channel<Unit>(Channel.UNLIMITED)
    val logs = mutableListOf<Pair<VyonExLogLevel, String>>()

    override suspend fun session(): VyonExSession {
        sessionCalls.trySend(Unit)
        check(!failSession) { "offline test" }
        return VyonExSession(cookie = null, visitorData = null, region = "US", language = "en")
    }

    override val configStore = object : VyonExConfigStore {
        override val enabled get() = this@FakeHost.enabled
        override fun getString(key: String): String? = null
        override fun getLong(key: String): Long? = null
        override fun putString(key: String, value: String) {}
        override fun putLong(key: String, value: Long) {}
    }

    override fun nowMs() = now

    override fun log(level: VyonExLogLevel, message: String, trackId: String?) {
        logs += level to message
    }
}
