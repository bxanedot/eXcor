package com.excor

import kotlin.time.Clock

/**
 * The headers that make an InnerTube request count as signed in: with the account's cookies, YouTube expects
 * `SAPISIDHASH <time>_<sha1("<time> <SAPISID> <origin>")>` for the request's origin. Empty without a SAPISID cookie.
 */
internal fun signedInHeaders(cookie: String?, origin: String, nowSeconds: Long = Clock.System.now().epochSeconds): Map<String, String> {
    val cookies = cookie.orEmpty().split(';').associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
    val sapisid = (cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"])?.takeIf(String::isNotEmpty) ?: return emptyMap()
    val hash = sha1("$nowSeconds $sapisid $origin".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return mapOf("Authorization" to "SAPISIDHASH ${nowSeconds}_$hash", "X-Origin" to origin, "X-Goog-AuthUser" to "0")
}

/** SHA-1 (FIPS 180-4); common Kotlin has none. Only for SAPISIDHASH, which YouTube defines with it. */
internal fun sha1(message: ByteArray): ByteArray {
    val h = intArrayOf(0x67452301, 0xefcdab89.toInt(), 0x98badcfe.toInt(), 0x10325476, 0xc3d2e1f0.toInt())
    val bitLength = message.size.toLong() * 8
    val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    val w = IntArray(80)
    for (chunk in padded.indices step 64) {
        for (t in 0 until 16) {
            val j = chunk + t * 4
            w[t] = ((padded[j].toInt() and 0xff) shl 24) or ((padded[j + 1].toInt() and 0xff) shl 16) or
                ((padded[j + 2].toInt() and 0xff) shl 8) or (padded[j + 3].toInt() and 0xff)
        }
        for (t in 16 until 80) w[t] = (w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16]).rotateLeft(1)
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
        for (t in 0 until 80) {
            val (f, k) = when (t / 20) {
                0 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                1 -> (b xor c xor d) to 0x6ed9eba1
                2 -> ((b and c) or (b and d) or (c and d)) to 0x8f1bbcdc.toInt()
                else -> (b xor c xor d) to 0xca62c1d6.toInt()
            }
            val temp = a.rotateLeft(5) + f + e + k + w[t]
            e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e
    }
    return ByteArray(20) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}
