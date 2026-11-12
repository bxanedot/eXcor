package com.excor.preview

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

/**
 * The youtube.com cookies of a Firefox-family browser (Zen, Firefox) on this computer, as a Cookie header value, or
 * null when no profile has a signed-in YouTube session. Profiles keep cookies unencrypted in cookies.sqlite; the
 * newest signed-in one wins. Read only when the user asks; the browser's files are copied, never changed.
 */
fun firefoxFamilyYouTubeCookie(appData: File? = System.getenv("APPDATA")?.let(::File)): String? {
    val roots = listOfNotNull(appData?.resolve("zen/Profiles"), appData?.resolve("Mozilla/Firefox/Profiles"))
    return roots.flatMap { it.listFiles().orEmpty().toList() }
        .map { it.resolve("cookies.sqlite") }
        .filter(File::isFile)
        .mapNotNull { db -> youTubeCookies(db)?.let { db.lastModified() to it } }
        .maxByOrNull { it.first }?.second
}

/** The profile's youtube.com cookies when they include SAPISID (a signed-in session); null otherwise. */
internal fun youTubeCookies(db: File): String? {
    // The browser keeps the database open and writes recent changes to the -wal file, so read a copy of both.
    val dir = Files.createTempDirectory("excor-cookies").toFile()
    try {
        val copy = dir.resolve("cookies.sqlite")
        Files.copy(db.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        db.resolveSibling("cookies.sqlite-wal").takeIf(File::isFile)
            ?.let { Files.copy(it.toPath(), dir.resolve("cookies.sqlite-wal").toPath(), StandardCopyOption.REPLACE_EXISTING) }
        val pairs = DriverManager.getConnection("jdbc:sqlite:${copy.path}").use { connection ->
            connection.prepareStatement(
                "SELECT name, value FROM moz_cookies WHERE host IN ('.youtube.com', 'youtube.com', 'www.youtube.com') " +
                    "AND (expiry = 0 OR expiry > ?) ORDER BY lastAccessed",
            ).use { query ->
                query.setLong(1, System.currentTimeMillis() / 1000)
                query.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) } }
            }
        }
        if ("SAPISID" !in pairs && "__Secure-3PAPISID" !in pairs) return null
        return pairs.entries.filter { (name, value) -> (name + value).all { it in ' '..'~' } && ';' !in value }
            .joinToString("; ") { (name, value) -> "$name=$value" }
    } catch (_: Exception) {
        return null
    } finally {
        dir.deleteRecursively()
    }
}
