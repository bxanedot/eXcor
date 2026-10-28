package com.vyonex.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.vyonex.VyonEx
import com.vyonex.VyonExContainer
import com.vyonex.VyonExFormat
import com.vyonex.VyonExMerge
import com.vyonex.VyonExUnplayableException
import com.vyonex.app.Choice
import com.vyonex.app.Failure
import com.vyonex.app.Plan
import com.vyonex.app.Refused
import com.vyonex.app.Source
import com.vyonex.app.Track
import com.vyonex.app.bestTranscoding
import com.vyonex.app.fileNameOf
import com.vyonex.app.planOf
import com.vyonex.app.soundCloudExtensionOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The download queue: one track at a time, in [DownloadService] so it goes on with the app in the background. Files go
 * to Music/VyonEx (audio) and Movies/VyonEx (video), where music and gallery apps find them. The queue lives in memory:
 * if Android ends the app, unfinished downloads are gone.
 */
private const val DRM_ONLY = "SoundCloud only offers this one encrypted (DRM), which VyonEx does not save."

class Downloads(private val context: Context, private val vyonEx: VyonEx) {
    data class Job(val id: Int, val track: Track, val choice: Choice, val label: String, val state: State)

    sealed interface State {
        data object Queued : State
        /** [done] runs from 0 to 1; null while YouTube is asked for the formats. */
        data class Fetching(val done: Float?) : State
        data object Merging : State
        data class Saved(val where: String) : State
        data class Failed(val message: String) : State
    }

    private val _jobs = MutableStateFlow(emptyList<Job>())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    /** Android 10 and later write Opus (into Ogg alone, or into WebM with VP9). */
    val writesOpus = VyonExMerge.supports(VyonExContainer.WEBM)

    fun add(tracks: List<Track>, choice: Choice, label: String) {
        val first = (_jobs.value.maxOfOrNull { it.id } ?: 0) + 1
        _jobs.update { jobs -> jobs + tracks.mapIndexed { i, track -> Job(first + i, track, choice, label, State.Queued) } }
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun clearFinished() = _jobs.update { jobs -> jobs.filter { it.state !is State.Saved && it.state !is State.Failed } }

    /**
     * Called on the main thread by [DownloadService]: runs queued jobs until none is left. [onChange] is also called
     * from download threads, with progress.
     */
    suspend fun runQueued(onChange: (Job, position: Int, count: Int) -> Unit) {
        while (true) {
            val job = _jobs.value.firstOrNull { it.state == State.Queued } ?: return
            run(job) { state ->
                val now = job.copy(state = state)
                _jobs.update { jobs -> jobs.map { if (it.id == job.id) now else it } }
                val jobs = _jobs.value
                onChange(now, jobs.indexOfFirst { it.id == job.id } + 1, jobs.size)
            }
        }
    }

    /** Marks what is still queued or running as failed, with [message]. */
    fun stopAll(message: String) = _jobs.update { jobs ->
        jobs.map { if (it.state is State.Saved || it.state is State.Failed) it else it.copy(state = State.Failed(message)) }
    }

    private suspend fun run(job: Job, report: (State) -> Unit) {
        report(State.Fetching(null))
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val parts = mutableListOf<File>()
        try {
            if (job.track.source == Source.SOUNDCLOUD) return soundCloud(job, dir, parts, report)
            val formats = vyonEx.formats(job.track.id) ?: return report(State.Failed("YouTube offers no download for this one."))
            val plan = planOf(formats, job.choice, writesOpus) ?: return report(State.Failed("Not offered in this quality."))
            val output = File(dir, "${job.id}.${plan.extension}").also(parts::add)
            when (plan) {
                is Plan.Audio -> {
                    val audio = File(dir, "${job.id}-audio").also(parts::add)
                    fetch(plan.format, formats.headers, audio, 0, plan.format.contentLength ?: 0) { report(State.Fetching(it)) }
                    report(State.Merging)
                    withContext(Dispatchers.IO) { VyonExMerge.remuxAudio(audio, output) }
                }
                is Plan.Video -> {
                    val video = File(dir, "${job.id}-video").also(parts::add)
                    val audio = File(dir, "${job.id}-audio").also(parts::add)
                    // Progress counts the bytes of both; a length YouTube left out counts as nothing.
                    val videoBytes = plan.pair.video.contentLength ?: 0
                    val total = videoBytes + (plan.pair.audio.contentLength ?: 0)
                    fetch(plan.pair.video, formats.headers, video, 0, total) { report(State.Fetching(it)) }
                    fetch(plan.pair.audio, formats.headers, audio, videoBytes, total) { report(State.Fetching(it)) }
                    report(State.Merging)
                    withContext(Dispatchers.IO) { VyonExMerge.merge(video, audio, output, plan.pair.container) }
                }
            }
            val name = fileNameOf(job.track.title ?: job.track.id, plan.extension)
            report(State.Saved(withContext(Dispatchers.IO) { save(output, name, plan is Plan.Audio, plan.extension) }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: VyonExUnplayableException) {
            report(State.Failed(e.youtubeReason ?: "YouTube does not allow this one."))
        } catch (e: Refused) {
            report(State.Failed(if (e.failure == Failure.DRM) DRM_ONLY else "SoundCloud does not stream this one here."))
        } catch (e: Exception) {
            // The library's own messages carry no URL and are shown; other messages (the network's) can, so only the class
            // is logged.
            val said = e.message?.takeIf { it.startsWith("VyonEx: ") }?.removePrefix("VyonEx: ")?.replaceFirstChar(Char::uppercaseChar)
            // A SoundCloud ID is its page URL: only the source is logged.
            Log.w("VyonEx", "${if (job.track.source == Source.YOUTUBE) job.track.id else "SoundCloud track"} download failed: ${e.javaClass.simpleName}")
            report(State.Failed(said ?: if (e is IOException) "Could not download. Check the connection." else "Could not save this one."))
        } finally {
            // Also when cancelled (the service stopped), which withContext alone would refuse.
            withContext(NonCancellable + Dispatchers.IO) { parts.forEach(File::delete) }
        }
    }

    /**
     * A SoundCloud track: its best transcoding as one file (HLS segments joined), AAC remuxed from fragmented MP4 into a
     * plain `.m4a` as YouTube's audio is.
     */
    private suspend fun soundCloud(job: Job, dir: File, parts: MutableList<File>, report: (State) -> Unit) {
        val track = vyonEx.soundCloud.track(job.track.id) ?: return report(State.Failed("SoundCloud has nothing at that link any more."))
        if (track.isBlocked) throw Refused(Failure.NOT_IN_COUNTRY)
        val transcoding = bestTranscoding(track.transcodings) ?: throw Refused(if (track.hasDrm) Failure.DRM else Failure.NO_STREAM)
        val extension = soundCloudExtensionOf(transcoding)
        val raw = File(dir, "${job.id}-audio").also(parts::add)
        withContext(Dispatchers.IO) {
            FileOutputStream(raw).buffered().use { out -> vyonEx.soundCloud.download(transcoding, { out.write(it) }) { report(State.Fetching(it)) } }
        }
        val output = if (extension == "m4a") {
            report(State.Merging)
            File(dir, "${job.id}.m4a").also(parts::add).also { withContext(Dispatchers.IO) { VyonExMerge.remuxAudio(raw, it) } }
        } else {
            raw
        }
        val name = fileNameOf(job.track.title ?: "SoundCloud track", extension)
        report(State.Saved(withContext(Dispatchers.IO) { save(output, name, isAudio = true, extension) }))
    }

    /** Downloads [format] into [file], resuming twice after a dropped connection; progress over [total] bytes from [before]. */
    private suspend fun fetch(format: VyonExFormat, headers: Map<String, String>, file: File, before: Long, total: Long, progress: (Float?) -> Unit) {
        var from = 0L
        var attempts = 0
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    // The library hands over whole chunks, so the file always ends where the next range starts.
                    FileOutputStream(file, from > 0).buffered().use { out ->
                        vyonEx.download(format, headers, { out.write(it) }, from) { written, _ ->
                            progress(if (total > 0) ((before + written).toFloat() / total).coerceAtMost(1f) else null)
                        }
                    }
                }
                return
            } catch (e: IOException) {
                if (++attempts > 2) throw e
                from = file.length()
            }
        }
    }

    /** Copies [file] to where music and gallery apps look, under [name]; returns where it went, for the screen. */
    private fun save(file: File, name: String, isAudio: Boolean, extension: String): String {
        val mime = when (extension) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/ogg"
            "mp4" -> "video/mp4"
            else -> "video/webm"
        }
        val folder = (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/VyonEx"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                // Hidden from other apps until it is complete.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the file")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("MediaStore gave no stream")).use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            // MediaStore renames a name already taken ("name (1).m4a").
            return "$folder/$name"
        }
        // Android 8 and 9: the shared folder itself, with the storage permission asked for before downloading.
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "VyonEx")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $folder")
        val base = name.substringBeforeLast('.')
        val target = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) name else "$base ($n).$extension") }.first { !it.exists() }
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        return "$folder/${target.name}"
    }
}
