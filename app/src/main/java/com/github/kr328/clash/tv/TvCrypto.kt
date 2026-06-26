package com.github.kr328.clash.tv

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helpers for the secured TV import channel (phone → TV via /api/transfer).
 *
 * The TV puts a one-time token and an AES-256-GCM key in the QR code; the phone
 * authenticates with the token and encrypts the payload with the key. The key never
 * travels over the network (only screen → camera), so a LAN sniffer/MITM cannot read
 * the subscription URL or age-secret-key, nor post on behalf of the user.
 */
object TvCrypto {
    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12
    private const val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    private val random = SecureRandom()

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { random.nextBytes(it) }

    fun encodeB64(bytes: ByteArray): String = Base64.encodeToString(bytes, B64)

    fun decodeB64(value: String): ByteArray = Base64.decode(value, B64)

    /** Ciphertext (with GCM tag) and its nonce, both base64url-encoded. */
    data class Encrypted(val nonce: String, val data: String)

    /** AES-256-GCM encrypt [plaintext] with [key] (32 bytes); fresh random nonce per call. */
    fun encrypt(key: ByteArray, plaintext: String): Encrypted {
        val nonce = randomBytes(NONCE_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Encrypted(encodeB64(nonce), encodeB64(ct))
    }

    /** Decrypts; returns null on any failure (wrong key, tampering, bad input). */
    fun decrypt(key: ByteArray, nonceB64: String, dataB64: String): String? {
        return try {
            val nonce = decodeB64(nonceB64)
            val ct = decodeB64(dataB64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Length-aware constant-time comparison to avoid token timing leaks. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        if (x.size != y.size) return false
        var r = 0
        for (i in x.indices) r = r or (x[i].toInt() xor y[i].toInt())
        return r == 0
    }
}
