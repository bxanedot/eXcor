package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.util.generateNonce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An OAuth access token and what came with it. [toString] never prints a secret.
 * [expiresAtMs] is wall-clock milliseconds; null when the provider did not say.
 */
data class ExcorOAuthToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null,
    val scopes: Set<String> = emptySet(),
) {
    override fun toString(): String =
        "ExcorOAuthToken(accessToken=present, refreshToken=${if (refreshToken == null) "missing" else "present"}, " +
            "expiresAtMs=$expiresAtMs, scopes=$scopes)"
}

/**
 * Host-owned secure storage (Keychain, EncryptedSharedPreferences, an OS credential vault). Tokens are keyed by
 * provider, so one provider's flow never sees another's secrets. eXcor keeps no token on disk itself.
 */
interface ExcorTokenStore {
    suspend fun read(provider: ExcorCatalogProvider): ExcorOAuthToken?
    suspend fun write(provider: ExcorCatalogProvider, token: ExcorOAuthToken)
    suspend fun delete(provider: ExcorCatalogProvider)
}

/** Exchanges a refresh token for a new access token. Throws ExcorCatalogException(INVALID_CREDENTIAL) on revocation. */
fun interface ExcorTokenRefresher {
    @Throws(Throwable::class)
    suspend fun refresh(provider: ExcorCatalogProvider, token: ExcorOAuthToken): ExcorOAuthToken
}

/**
 * Hands out a current access token per provider: refreshes shortly before expiry, once at a time per provider, and
 * forgets a token the provider revoked. Disconnecting deletes the stored token.
 */
class ExcorTokenManager(
    private val store: ExcorTokenStore,
    private val refresher: ExcorTokenRefresher?,
    private val nowMs: () -> Long,
    private val refreshMarginMs: Long = 60_000,
) {
    private val locks = ExcorCatalogProvider.entries.associateWith { Mutex() }

    /** Null when the account is not connected. */
    @Throws(Throwable::class)
    suspend fun accessToken(provider: ExcorCatalogProvider): String? = locks.getValue(provider).withLock {
        val token = store.read(provider) ?: return@withLock null
        val expiresAt = token.expiresAtMs
        if (expiresAt == null || expiresAt - refreshMarginMs > nowMs()) return@withLock token.accessToken
        val refreshToken = token.refreshToken
        if (refresher == null || refreshToken == null) {
            if (expiresAt > nowMs()) return@withLock token.accessToken
            throw ExcorCatalogException(provider, ExcorCatalogError.AUTHENTICATION_REQUIRED)
        }
        val renewed = try {
            refresher.refresh(provider, token)
        } catch (e: ExcorCatalogException) {
            if (e.error == ExcorCatalogError.INVALID_CREDENTIAL) store.delete(provider)
            throw e
        }
        // Providers may omit the refresh token when it did not rotate.
        val kept = if (renewed.refreshToken == null) renewed.copy(refreshToken = refreshToken) else renewed
        store.write(provider, kept)
        kept.accessToken
    }

    suspend fun connect(provider: ExcorCatalogProvider, token: ExcorOAuthToken) = locks.getValue(provider).withLock { store.write(provider, token) }

    suspend fun disconnect(provider: ExcorCatalogProvider) = locks.getValue(provider).withLock { store.delete(provider) }

    suspend fun isConnected(provider: ExcorCatalogProvider): Boolean = store.read(provider) != null
}

/** An RFC 7636 PKCE pair: send [challenge] with the authorization request and [verifier] with the code exchange. */
class ExcorPkce private constructor(val verifier: String, val challenge: String) {
    override fun toString(): String = "ExcorPkce(verifier=present)"

    companion object {
        /** A 64-character verifier from 48 bytes of the platform's secure random source. */
        fun create(): ExcorPkce = of(base64Url(generateNonce(48)))

        internal fun of(verifier: String): ExcorPkce {
            require(verifier.length in 43..128 && verifier.all { it.isLetterOrDigit() || it in "-._~" })
            return ExcorPkce(verifier, base64Url(sha256(verifier.encodeToByteArray())))
        }
    }
}

internal fun base64Url(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val out = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xff
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
        out.append(alphabet[b0 shr 2])
        out.append(alphabet[((b0 and 3) shl 4) or (b1 shr 4)])
        if (i + 1 < bytes.size) out.append(alphabet[((b1 and 15) shl 2) or (b2 shr 6)])
        if (i + 2 < bytes.size) out.append(alphabet[b2 and 63])
        i += 3
    }
    return out.toString()
}

/** SHA-256 (FIPS 180-4); common Kotlin has none, and PKCE needs it on every target. */
internal fun sha256(message: ByteArray): ByteArray {
    val k = SHA256_K
    val h = hexWords("6a09e667 bb67ae85 3c6ef372 a54ff53a 510e527f 9b05688c 1f83d9ab 5be0cd19")
    val bitLength = message.size.toLong() * 8
    val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    val w = IntArray(64)
    for (chunk in padded.indices step 64) {
        for (t in 0 until 16) {
            val j = chunk + t * 4
            w[t] = ((padded[j].toInt() and 0xff) shl 24) or ((padded[j + 1].toInt() and 0xff) shl 16) or
                ((padded[j + 2].toInt() and 0xff) shl 8) or (padded[j + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + k[t] + w[t]
            val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }
    return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}

private fun hexWords(words: String): IntArray = words.split(' ').map { it.toLong(16).toInt() }.toIntArray()

private val SHA256_K = hexWords(
    "428a2f98 71374491 b5c0fbcf e9b5dba5 3956c25b 59f111f1 923f82a4 ab1c5ed5 d807aa98 12835b01 243185be 550c7dc3 " +
        "72be5d74 80deb1fe 9bdc06a7 c19bf174 e49b69c1 efbe4786 0fc19dc6 240ca1cc 2de92c6f 4a7484aa 5cb0a9dc 76f988da " +
        "983e5152 a831c66d b00327c8 bf597fc7 c6e00bf3 d5a79147 06ca6351 14292967 27b70a85 2e1b2138 4d2c6dfc 53380d13 " +
        "650a7354 766a0abb 81c2c92e 92722c85 a2bfe8a1 a81a664b c24b8b70 c76c51a3 d192e819 d6990624 f40e3585 106aa070 " +
        "19a4c116 1e376c08 2748774c 34b0bcb5 391c0cb3 4ed8aa4a 5b9cca4f 682e6ff3 748f82ee 78a5636f 84c87814 8cc70208 " +
        "90befffa a4506ceb bef9a3f7 c67178f2",
)
