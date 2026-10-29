package com.excor.android

import android.app.Application
import android.content.Context
import android.util.Log
import com.excor.Excor
import com.excor.ExcorConfigStore
import com.excor.ExcorHost
import com.excor.ExcorLogLevel
import com.excor.ExcorManifestKind
import com.excor.ExcorSession
import com.excor.ExcorStream
import com.excor.app.HLS_MIME_TYPE
import com.excor.app.Playback
import com.excor.app.Source
import com.excor.app.StreamSource
import com.excor.app.Track
import com.excor.app.soundCloudStreamOf
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

/** One Excor, one play queue and one download queue for the whole app; the screens and the services share them. */
class ExcorApp : Application() {
    lateinit var excor: Excor
        private set
    lateinit var playback: Playback
        private set
    lateinit var downloads: Downloads
        private set
    private val settings by lazy { getSharedPreferences("excor-settings", MODE_PRIVATE) }

    /**
     * The audio language for videos with dubbed audio (`Excor.preferAudioLanguage`), kept across launches; null keeps
     * each video's original track. A code Excor refuses is not kept: the original track it is.
     */
    var audioLanguage: String?
        get() = settings.getString(AUDIO_LANGUAGE, null)
        set(code) {
            val kept = try {
                excor.preferAudioLanguage(code)
                code
            } catch (e: IllegalArgumentException) {
                excor.preferAudioLanguage(null)
                null
            }
            settings.edit().apply { if (kept == null) remove(AUDIO_LANGUAGE) else putString(AUDIO_LANGUAGE, kept) }.apply()
        }

    override fun onCreate() {
        super.onCreate()
        excor = Excor(AndroidHost(this)).apply { init() }
        audioLanguage = audioLanguage
        playback = Playback(
            object : StreamSource {
                override suspend fun stream(track: Track): ExcorStream? = when (track.source) {
                    // The best audio; Excor picks the format. A live stream has no single file, so its manifest plays.
                    Source.YOUTUBE -> excor.extract(track.id, maxKbps = 160) ?: excor.manifest(track.id)?.let { manifest ->
                        ExcorStream(
                            manifest.videoId, manifest.url, 0, MANIFEST_TYPES.getValue(manifest.kind), null,
                            manifest.profileId, manifest.profileId, manifest.headers,
                        )
                    }
                    // Looked up again by its page: the signed stream URL is fresh when it plays.
                    Source.SOUNDCLOUD -> soundCloudStreamOf(excor.soundCloud.track(track.id), excor.soundCloud::stream)
                }

                override fun refused(url: String) {
                    excor.onRefused(url)
                }
            },
            MainScope(),
            nowSeconds = { System.currentTimeMillis() / 1000 },
        )
        downloads = Downloads(this, excor)
    }
}

private const val AUDIO_LANGUAGE = "audioLanguage"

/** The MIME types the player picks its HLS or DASH source by. */
internal val MANIFEST_TYPES = mapOf(ExcorManifestKind.HLS to HLS_MIME_TYPE, ExcorManifestKind.DASH to "application/dash+xml")

/** Logged out (no cookie), in the phone's language and region. */
private class AndroidHost(context: Context) : ExcorHost {
    private val prefs = context.getSharedPreferences("excor", Context.MODE_PRIVATE)
    override val filesDir: File = context.filesDir
    override val httpClient = OkHttpClient()

    override suspend fun session(): ExcorSession {
        val locale = Locale.getDefault()
        return ExcorSession(cookie = null, visitorData = null, region = locale.country.ifEmpty { "US" }, language = locale.language.ifEmpty { "en" })
    }

    override val configStore = object : ExcorConfigStore {
        override val enabled = true
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
        override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
        override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    }

    override fun nowMs() = System.currentTimeMillis()

    // The library's messages never carry URLs, cookies or tokens.
    override fun log(level: ExcorLogLevel, message: String, trackId: String?) {
        val priority = when (level) {
            ExcorLogLevel.DEBUG -> Log.DEBUG
            ExcorLogLevel.INFO -> Log.INFO
            ExcorLogLevel.WARN -> Log.WARN
            ExcorLogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "Excor", listOfNotNull(trackId, message).joinToString(" "))
    }
}
