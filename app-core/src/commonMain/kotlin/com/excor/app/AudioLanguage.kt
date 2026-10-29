package com.excor.app

import com.excor.ExcorAudioTrack

/** One audio language a listener can pick; a null [code] keeps each video's original track. */
data class AudioChoice(val code: String?, val label: String)

/**
 * The choices for a video with dubbed audio: its original track first, then each other language once, in YouTube's
 * order, by YouTube's name for it. Descriptive tracks are left out (Excor never picks them for a language). Empty for
 * a video with one track.
 */
fun audioChoicesOf(tracks: List<ExcorAudioTrack>): List<AudioChoice> {
    if (tracks.isEmpty()) return emptyList()
    val original = tracks.firstOrNull { it.isOriginal }
    val others = tracks.filter { !it.isDescriptive && !it.isOriginal && !it.languageCode.equals(original?.languageCode, ignoreCase = true) }
        .distinctBy { it.languageCode.lowercase() }
        .map { AudioChoice(it.languageCode, it.name ?: it.languageCode) }
    return listOf(AudioChoice(null, original?.name?.let { "Original ($it)" } ?: "Original")) + others
}

/** The choice that plays with [preferred] set: its language when the video has it, else the original track. */
fun selectedChoice(choices: List<AudioChoice>, preferred: String?): AudioChoice? =
    choices.firstOrNull { it.code != null && it.code.equals(preferred, ignoreCase = true) } ?: choices.firstOrNull { it.code == null }
