import com.excor.Excor
import com.excor.ExcorYouTubeChannelTab
import com.excor.excorDefaultHost
import kotlinx.coroutines.runBlocking

/** Exercises the README quick start against live YouTube and prints what it found; exits non-zero on a failed step. */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--playback" -> runBlocking { runPlaybackDiagnostics(args.drop(1)) }
        "--help" -> println("Usage: [--playback VIDEO_ID [--seconds N] [--seek-seconds N] [--full-decode]]")
        null -> runQuickstart()
        else -> {
            println("Unknown arguments. Use --help for usage.")
            throw IllegalArgumentException("unsupported arguments")
        }
    }
}

private fun runQuickstart() = runBlocking {
    val host = excorDefaultHost(region = "US", language = "en")
    val excor = Excor(host)
    try {
        val failures = mutableListOf<String>()
        fun check(step: String, ok: Boolean, detail: String) {
            println("${if (ok) "ok  " else "FAIL"} $step: $detail")
            if (!ok) failures += step
        }

        val video = excor.videoInfo("dQw4w9WgXcQ")
        check("videoInfo", video?.title?.isNotBlank() == true, "${video?.title} (${video?.durationSeconds}s, playable=${video?.playable})")

        val search = excor.search("daft punk")
        check("search", (search?.videos?.size ?: 0) > 0, "${search?.videos?.size} videos, ${search?.filters?.size} filters")
        val more = search?.continuation?.let { excor.search("daft punk", it) }
        check("search continuation", (more?.videos?.size ?: 0) > 0, "${more?.videos?.size} more videos")

        val playlists = excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.PLAYLISTS)
        check("channel playlists", (playlists?.playlists?.size ?: 0) > 0, "${playlists?.title}: ${playlists?.playlists?.size} playlists")

        val music = excor.musicSearch("daft punk")
        check("musicSearch", (music?.items?.size ?: 0) > 0, "${music?.items?.size} items: ${music?.items?.map { it.type }?.distinct()}")
        val radio = excor.musicRadio(videoId = "FGBhQbmPwH8")
        check("musicRadio", (radio?.tracks?.size ?: 0) > 0, "${radio?.tracks?.size} tracks")

        if (failures.isNotEmpty()) error("failed steps: $failures")
    } finally {
        excor.close()
        host.httpClient.connectionPool.evictAll()
        host.httpClient.dispatcher.executorService.shutdown()
    }
}
