package com.vyonex.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vyonex.VyonEx
import com.vyonex.VyonExUnplayableException
import com.vyonex.app.Choice
import com.vyonex.app.Option
import com.vyonex.app.Track
import com.vyonex.app.VIDEO_CAPS
import com.vyonex.app.audioOptions
import com.vyonex.app.videoOptions
import kotlinx.coroutines.CancellationException

/**
 * Audio or video, then a quality. For one track the list is what YouTube offers for it (asked when the dialog opens);
 * for many it is "the best of each", up to a height.
 */
@Composable
fun DownloadDialog(tracks: List<Track>, vyonEx: VyonEx, writesOpus: Boolean, onDismiss: () -> Unit, onDownload: (Choice, String) -> Unit) {
    var video by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<Pair<List<Option>, List<Option>>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Option?>(null) }
    LaunchedEffect(tracks) {
        if (tracks.size > 1) {
            // Above 1080p YouTube has only VP9, which needs Opus beside it (Android 10 and later).
            val caps = VIDEO_CAPS.filter { writesOpus || it <= 1080 }
            options = listOf(Option(Choice.Audio(null), "Best audio")) to caps.map { Option(Choice.Video(it), "Best up to ${it}p") }
            return@LaunchedEffect
        }
        try {
            val formats = vyonEx.formats(tracks.single().videoId)
            if (formats == null) problem = "YouTube offers no download for this one." else options = audioOptions(formats, writesOpus) to videoOptions(formats, writesOpus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VyonExUnplayableException) {
            problem = e.youtubeReason ?: "YouTube does not allow this one."
        } catch (e: Exception) {
            problem = "Could not reach YouTube. Check the connection."
        }
    }
    val shown = options?.let { if (video) it.second else it.first }
    // The best quality is picked until another is.
    val selected = picked?.takeIf { it in shown.orEmpty() } ?: shown?.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Download" else "Download ${tracks.size} tracks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !video, onClick = { video = false }, label = { Text("Audio") })
                    FilterChip(selected = video, onClick = { video = true }, label = { Text("Video") })
                }
                when {
                    problem != null -> Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error)
                    shown == null -> CircularProgressIndicator()
                    shown.isEmpty() -> Text("YouTube offers nothing of this kind that can be saved here.")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown) { option ->
                            Row(Modifier.fillMaxWidth().clickable { picked = option }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = option == selected, onClick = { picked = option })
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let { onDownload(it.choice, it.label) } }, enabled = selected != null) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The downloads of this session, newest first, with their progress or where they were saved. */
@Composable
fun DownloadList(jobs: List<Downloads.Job>, onClear: () -> Unit) {
    if (jobs.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = jobs.any { it.state is Downloads.State.Saved || it.state is Downloads.State.Failed }) {
                Text("Clear finished")
            }
        }
        LazyColumn(Modifier.heightIn(max = 160.dp)) {
            items(jobs.asReversed(), key = { it.id }) { job ->
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${job.track.title ?: job.track.videoId} · ${job.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val small = MaterialTheme.typography.bodySmall
                    when (val state = job.state) {
                        Downloads.State.Queued -> Text("Waiting", style = small)
                        is Downloads.State.Fetching -> state.done?.let { done -> LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth()) }
                            ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                        Downloads.State.Merging -> Text("Putting the file together…", style = small)
                        is Downloads.State.Saved -> Text("Saved to ${state.where}", style = small)
                        is Downloads.State.Failed -> Text(state.message, style = small, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
