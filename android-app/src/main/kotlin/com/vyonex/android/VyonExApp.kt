package com.vyonex.android

import android.app.Application
import android.content.Context
import android.util.Log
import com.vyonex.VyonEx
import com.vyonex.VyonExConfigStore
import com.vyonex.VyonExHost
import com.vyonex.VyonExLogLevel
import com.vyonex.VyonExManifestKind
import com.vyonex.VyonExSession
import com.vyonex.VyonExStream
import com.vyonex.app.HLS_MIME_TYPE
import com.vyonex.app.Playback
import com.vyonex.app.Source
import com.vyonex.app.StreamSource
import com.vyonex.app.Track
import com.vyonex.app.soundCloudStreamOf
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

/** One VyonEx, one play queue and one download queue for the whole app; the screens and the services share them. */
class VyonExApp : Application() {
    lateinit var vyonEx: VyonEx
        private set
    lateinit var playback: Playback
        private set
    lateinit var downloads: Downloads
        private set
    private val settings by lazy { getSharedPreferences("vyonex-settings", MODE_PRIVATE) }

    /**
     * The audio language for videos with dubbed audio (`VyonEx.preferAudioLanguage`), kept across launches; null keeps
     * each video's original track. A code VyonEx refuses is not kept: the original track it is.
     */
    var audioLanguage: String?
        get() = settings.getString(AUDIO_LANGUAGE, null)
        set(code) {
            val kept = try {
                vyonEx.preferAudioLanguage(code)
                code
            } catch (e: IllegalArgumentException) {
                vyonEx.preferAudioLanguage(null)
                null
            }
            settings.edit().apply { if (kept == null) remove(AUDIO_LANGUAGE) else putString(AUDIO_LANGUAGE, kept) }.apply()
        }

    override fun onCreate() {
        super.onCreate()
        vyonEx = VyonEx(AndroidHost(this)).apply { init() }
        audioLanguage = audioLanguage
        playback = Playback(
            object : StreamSource {
                override suspend fun stream(track: Track): VyonExStream? = when (track.source) {
                    // The best audio; VyonEx picks the format. A live stream has no single file, so its manifest plays.
                    Source.YOUTUBE -> vyonEx.extract(track.id, maxKbps = 160) ?: vyonEx.manifest(track.id)?.let { manifest ->
                        VyonExStream(
                            manifest.videoId, manifest.url, 0, MANIFEST_TYPES.getValue(manifest.kind), null,
                            manifest.profileId, manifest.profileId, manifest.headers,
                        )
                    }
                    // Looked up again by its page: the signed stream URL is fresh when it plays.
                    Source.SOUNDCLOUD -> soundCloudStreamOf(vyonEx.soundCloud.track(track.id), vyonEx.soundCloud::stream)
                }

                override fun refused(url: String) {
                    vyonEx.onRefused(url)
                }
            },
            MainScope(),
            nowSeconds = { System.currentTimeMillis() / 1000 },
        )
        downloads = Downloads(this, vyonEx)
    }
}

private const val AUDIO_LANGUAGE = "audioLanguage"

/** The MIME types the player picks its HLS or DASH source by. */
internal val MANIFEST_TYPES = mapOf(VyonExManifestKind.HLS to HLS_MIME_TYPE, VyonExManifestKind.DASH to "application/dash+xml")

/** Logged out (no cookie), in the phone's language and region. */
private class AndroidHost(context: Context) : VyonExHost {
    private val prefs = context.getSharedPreferences("vyonex", Context.MODE_PRIVATE)
    override val filesDir: File = context.filesDir
    override val httpClient = OkHttpClient()

    override suspend fun session(): VyonExSession {
        val locale = Locale.getDefault()
        return VyonExSession(cookie = null, visitorData = null, region = locale.country.ifEmpty { "US" }, language = locale.language.ifEmpty { "en" })
    }

    override val configStore = object : VyonExConfigStore {
        override val enabled = true
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
        override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
        override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    }

    override fun nowMs() = System.currentTimeMillis()

    // The library's messages never carry URLs, cookies or tokens.
    override fun log(level: VyonExLogLevel, message: String, trackId: String?) {
        val priority = when (level) {
            VyonExLogLevel.DEBUG -> Log.DEBUG
            VyonExLogLevel.INFO -> Log.INFO
            VyonExLogLevel.WARN -> Log.WARN
            VyonExLogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "VyonEx", listOfNotNull(trackId, message).joinToString(" "))
    }
}
