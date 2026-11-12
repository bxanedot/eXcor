internal object HttpRangeProbe {
    const val PROBE_BYTES = 4_096
    const val MIN_TOTAL_BYTES = PROBE_BYTES * 4L

    fun isAvailable(startByte: Long, totalBytes: Long): Boolean =
        totalBytes >= MIN_TOTAL_BYTES && startByte >= 0 && startByte <= totalBytes - PROBE_BYTES

    fun forwardStart(totalBytes: Long): Long = totalBytes - totalBytes / 4

    fun backwardStart(totalBytes: Long): Long = totalBytes / 4

    fun totalBytes(code: Int, contentRange: String?, contentLength: Long?): Long? {
        if (code == 200) return contentLength?.takeIf { it > 0 }
        if (code != 206) return null
        val match = contentRange?.let {
            Regex("^bytes 0-[0-9]+/([0-9]+)$", RegexOption.IGNORE_CASE).matchEntire(it)
        } ?: return null
        return match.groupValues[1].toLongOrNull()?.takeIf { it > 0 }
    }

    fun classifyResponse(
        code: Int,
        contentRange: String?,
        bytesRead: Int,
        startByte: Long,
        totalBytes: Long,
    ): String {
        if (code == 401 || code == 403) return "ACCESS_DENIED"
        if (code == 200) return "IGNORED"
        if (code != 206) return "ERROR"
        if (!isAvailable(startByte, totalBytes)) return "UNAVAILABLE"
        val range = contentRange?.let {
            Regex("^bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)$", RegexOption.IGNORE_CASE).matchEntire(it)
        } ?: return "ERROR"
        val returnedStart = range.groupValues[1].toLongOrNull() ?: return "ERROR"
        val returnedEnd = range.groupValues[2].toLongOrNull() ?: return "ERROR"
        val returnedTotal = range.groupValues[3]
        val totalMatches = returnedTotal == "*" || returnedTotal.toLongOrNull() == totalBytes
        return if (returnedStart == startByte && returnedEnd == startByte + PROBE_BYTES - 1 &&
            bytesRead == PROBE_BYTES && totalMatches) "PASS" else "ERROR"
    }
}
