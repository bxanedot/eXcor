package com.excor.preview

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the preview's YouTube cookie outside backups, encrypted with a per-install Android Keystore key. */
internal class CookieVault(
    file: File,
    private val keyAlias: String = "com.excor.preview.youtube-cookie",
) {
    private val file = AtomicFile(file)

    fun read(): String? {
        val stored = try {
            file.openRead().use { it.readBytes() }
        } catch (_: IOException) {
            delete()
            return null
        }

        return try {
            if (stored.startsWith(HEADER)) {
                val ivStart = HEADER.size
                require(stored.size >= ivStart + IV_SIZE + TAG_SIZE)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, stored.copyOfRange(ivStart, ivStart + IV_SIZE)))
                cipher.updateAAD(HEADER)
                String(cipher.doFinal(stored, ivStart + IV_SIZE, stored.size - ivStart - IV_SIZE), Charsets.US_ASCII)
            } else {
                // Upgrades the plaintext cookie file written by earlier preview versions.
                String(stored, Charsets.US_ASCII).also { if (!write(it)) delete() }
            }
        } catch (_: IOException) {
            delete()
            null
        } catch (_: GeneralSecurityException) {
            delete()
            null
        } catch (_: IllegalArgumentException) {
            delete()
            null
        }
    }

    /** False means secure persistence failed; callers may keep the validated cookie for this session only. */
    fun write(cookie: String): Boolean {
        require(cookieProblem(cookie) == null) { "cookie is not a valid bounded YouTube session header" }
        val encrypted = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.updateAAD(HEADER)
            HEADER + cipher.iv + cipher.doFinal(cookie.toByteArray(Charsets.US_ASCII))
        } catch (_: GeneralSecurityException) {
            delete()
            return false
        } catch (_: IOException) {
            delete()
            return false
        }

        val output = try {
            file.startWrite()
        } catch (_: IOException) {
            delete()
            return false
        }
        return try {
            output.write(encrypted)
            file.finishWrite(output)
            true
        } catch (_: IOException) {
            file.failWrite(output)
            delete()
            false
        }
    }

    fun delete() {
        file.delete()
        try {
            keyStore().deleteEntry(keyAlias)
        } catch (_: IOException) {
        } catch (_: GeneralSecurityException) {
        }
    }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val TAG_SIZE = TAG_BITS / 8
        val HEADER = byteArrayOf(0, 0x45, 0x58, 0x43, 0x01)
    }
}
