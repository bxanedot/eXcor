package com.excor.preview

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Puts finished files in Downloads/eXcor, where the phone's Files app and music and video players find them.
 * Android 10 and later: through MediaStore, which needs no permission. Android 8 and 9: straight into the shared
 * Downloads folder, which needs the storage permission (asked for by the activity first).
 */
object Saver {
    const val FOLDER = "eXcor"

    /** Whether saving needs the storage permission on this Android version. */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Copies [file] into Downloads/eXcor as [name] and returns where it went, for the status line. */
    fun save(context: Context, file: File, name: String, mimeType: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, file, name, mimeType) else saveToFolder(context, file, name, mimeType)

    @TargetApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, file: File, name: String, mimeType: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Android refused a new file in Downloads")
        try {
            (resolver.openOutputStream(uri) ?: error("Android gave no way to write the file")).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (failure: Exception) {
            resolver.delete(uri, null, null)
            throw failure
        }
        return "Download/$FOLDER/$name"
    }

    @Suppress("DEPRECATION") // getExternalStoragePublicDirectory is the way on Android 8 and 9, which this path is for.
    private fun saveToFolder(context: Context, file: File, name: String, mimeType: String): String {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        check(folder.isDirectory || folder.mkdirs()) { "can't create Download/$FOLDER" }
        val target = uniqueFile(folder, name)
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType), null)
        return "Download/$FOLDER/${target.name}"
    }

    private fun uniqueFile(folder: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        return generateSequence(1) { it + 1 }.map { n -> File(folder, if (n == 1) name else "$base ($n).$extension") }.first { !it.exists() }
    }
}
