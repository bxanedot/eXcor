package com.bxane.excor.app

import com.bxane.excor.ExcorContainer
import com.bxane.excor.ExcorFormat
import com.bxane.excor.ExcorFormats
import com.bxane.excor.ExcorMergePair
import com.bxane.excor.mergePair

/** What to save of a track: its audio alone, or its video with audio merged into one file. */
sealed interface Choice {
    /** The audio format [itag]; null takes the best one the device can save (for many tracks at once). */
    data class Audio(val itag: Int?) : Choice

    /** The tallest video at most [maxHeight], with the audio that goes into the same container. */
    data class Video(val maxHeight: Int) : Choice
}

/** A line of the quality list, such as "1080p60 · MP4 · 85 MB". */
data class Option(val choice: Choice, val label: String)

/** What a choice comes to for one track: the formats to fetch, and the saved file's extension. */
sealed interface Plan {
    val extension: String

    data class Audio(val format: ExcorFormat, override val extension: String) : Plan
    data class Video(val pair: ExcorMergePair, override val extension: String) : Plan
}

/** The heights offered when many tracks are saved at once; each track gets its tallest up to the one chosen. */
val VIDEO_CAPS = listOf(2160, 1440, 1080, 720, 480, 360)

/**
 * The audio formats that can be saved, best first. [writesOpus]: the device can write Opus (into Ogg or WebM; Android 10
 * and later), otherwise only AAC is offered.
 */
fun audioOptions(formats: ExcorFormats, writesOpus: Boolean): List<Option> =
    savableAudio(formats, writesOpus).sortedByDescending { it.kbps }.map { format ->
        Option(Choice.Audio(format.itag), listOfNotNull(codecOf(format), "${format.kbps} kbps", sizeOf(format.contentLength)).joinToString(" · "))
    }

/** The video qualities that can be saved as one file, tallest first. */
fun videoOptions(formats: ExcorFormats, writesOpus: Boolean): List<Option> =
    formats.formats.filter { it.hasVideo && !it.hasAudio }.mapNotNull { it.height }.distinct().sortedDescending().mapNotNull { height ->
        // A height offered only in a codec that cannot be merged (AV1) falls to a lower pair and is left out.
        val pair = pairFor(formats, height, writesOpus)?.takeIf { it.video.height == height } ?: return@mapNotNull null
        val fps = pair.video.fps?.takeIf { it > 30 }?.toString().orEmpty()
        val size = pair.video.contentLength?.let { video -> pair.audio.contentLength?.let { video + it } }
        Option(Choice.Video(height), listOfNotNull("${height}p$fps", containerName(pair.container), sizeOf(size)).joinToString(" · "))
    }

/** What [choice] comes to for this track; null when it offers nothing that fits. */
fun planOf(formats: ExcorFormats, choice: Choice, writesOpus: Boolean): Plan? = when (choice) {
    is Choice.Audio -> {
        val savable = savableAudio(formats, writesOpus)
        // The chosen format, or the best one when none was chosen or this track lacks it.
        val format = savable.firstOrNull { it.itag == choice.itag } ?: savable.maxByOrNull { it.kbps }
        format?.let { Plan.Audio(it, if (it.isAac) "m4a" else "opus") }
    }
    is Choice.Video -> pairFor(formats, choice.maxHeight, writesOpus)?.let { Plan.Video(it, if (it.container == ExcorContainer.MP4) "mp4" else "webm") }
}

/** A file name from [title]: no characters that file systems refuse, at most 120 characters. */
fun fileNameOf(title: String, extension: String): String {
    val name = title.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }.joinToString("").trim().trim('.').take(120).trim()
    return "${name.ifEmpty { "download" }}.$extension"
}

/** MP4 (H.264 and AAC) plays everywhere, so it wins unless WebM (VP9 and Opus) is taller; YouTube has no H.264 above 1080p. */
private fun pairFor(formats: ExcorFormats, maxHeight: Int, writesOpus: Boolean): ExcorMergePair? =
    listOfNotNull(
        formats.mergePair(maxHeight, ExcorContainer.MP4),
        if (writesOpus) formats.mergePair(maxHeight, ExcorContainer.WEBM) else null,
    ).maxWithOrNull(compareBy({ it.video.height ?: 0 }, { it.container == ExcorContainer.MP4 }))

private fun savableAudio(formats: ExcorFormats, writesOpus: Boolean) =
    formats.formats.filter { !it.hasVideo && (it.isAac || (writesOpus && it.mimeType.startsWith("audio/webm"))) }

private val ExcorFormat.isAac get() = mimeType.startsWith("audio/mp4")

private fun codecOf(format: ExcorFormat) = if (format.isAac) "AAC" else "Opus"

private fun containerName(container: ExcorContainer) = if (container == ExcorContainer.MP4) "MP4" else "WebM"

/** "85 MB", "1.2 GB"; null when YouTube gave no length. */
private fun sizeOf(bytes: Long?): String? = when {
    bytes == null -> null
    bytes < 1_000_000_000 -> "${maxOf(1, bytes / 1_000_000)} MB"
    else -> (bytes / 100_000_000).let { "${it / 10}.${it % 10} GB" }
}
