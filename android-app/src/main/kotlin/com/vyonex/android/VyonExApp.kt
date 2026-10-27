package com.vyonex.android

import android.app.Application
import android.content.Context
import android.util.Log
import com.vyonex.VyonEx
import com.vyonex.VyonExConfigStore
import com.vyonex.VyonExHost
import com.vyonex.VyonExLogLevel
import com.vyonex.VyonExSession
import com.vyonex.VyonExStream
import com.vyonex.app.Playback
import com.vyonex.app.StreamSource
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

/** One VyonEx and one play queue for the whole app; the screens and the media service share them. */
class VyonExApp : Application() {
    lateinit var vyonEx: VyonEx
        private set
    lateinit var playback: Playback
        private set

    override fun onCreate() {
        super.onCreate()
        vyonEx = VyonEx(AndroidHost(this)).apply { init() }
        playback = Playback(
            object : StreamSource {
                // The best audio; VyonEx picks the format.
                override suspend fun stream(videoId: String): VyonExStream? = vyonEx.extract(videoId, maxKbps = 160)

                override fun refused(url: String) {
                    vyonEx.onRefused(url)
                }
            },
            MainScope(),
            nowSeconds = { System.currentTimeMillis() / 1000 },
        )
    }
}

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
