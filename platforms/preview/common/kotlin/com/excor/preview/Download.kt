package com.excor.preview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

// Shared by the Android and the desktop preview.

/** For media downloads. It follows googlevideo's redirects; [download] checks the final host before writing. */
fun mediaClient(): OkHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

/**
 * Downloads [url] into [file] in 4 MiB range reads, as players do, with the stream's [headers]. [progress] gets the
 * bytes so far and the total when YouTube gave one. Only googlevideo.com may answer; the URL is never logged.
 */
suspend fun download(
    client: OkHttpClient,
    url: String,
    headers: Map<String, String>,
    file: File,
    progress: (done: Long, total: Long?) -> Unit,
): Long = withContext(Dispatchers.IO) {
    val chunk = 4L * 1024 * 1024
    var offset = 0L
    var total: Long? = null
    file.outputStream().use { out ->
        while (total == null || offset < total!!) {
            ensureActive()
            val request = Request.Builder().url(url).header("Range", "bytes=$offset-${offset + chunk - 1}").apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val read = client.newCall(request).execute().use { response ->
                check(response.request.url.host.endsWith(".googlevideo.com")) { "media was redirected off googlevideo.com" }
                check(response.code == 206 || (response.code == 200 && offset == 0L)) { "YouTube answered HTTP ${response.code}" }
                total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: response.body.contentLength().takeIf { response.code == 200 && it > 0 }
                response.body.byteStream().copyTo(out)
            }
            if (read == 0L) break
            offset += read
            progress(offset, total)
            if (total == null) break
        }
    }
    check(offset > 0) { "nothing was downloaded" }
    offset
}

/** "12 of 80 MB", for progress lines. */
fun megabytes(done: Long, total: Long?): String = "${done / 1_000_000} of ${total?.div(1_000_000) ?: "?"} MB"
