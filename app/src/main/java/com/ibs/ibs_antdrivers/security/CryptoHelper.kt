package com.ibs.ibs_antdrivers.security

import android.util.Base64
import com.ibs.ibs_antdrivers.BuildConfig.PASSWORD_KEY
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * EXACT match to your C#:
 *  - key  = SHA256(PASSWORD_KEY)  // 32 bytes
 *  - mode = AES/CBC
 *  - pad  = PKCS7 (Android reports PKCS5Padding; it's PKCS#7-compatible)
 *  - out  = Base64( IV(16) || CIPHERTEXT )  [no newlines]
 */
object CryptoHelper {

    private fun keyBytes(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(PASSWORD_KEY.toByteArray(StandardCharsets.UTF_8))
    }

    /** Encrypts to Base64( IV(16) || CIPHERTEXT ) */
    fun encryptToCombinedBase64(plain: String): String {
        require(plain.isNotEmpty()) { "Empty plaintext not allowed." }

        val key = SecretKeySpec(keyBytes(), "AES")
        val iv = Random.nextBytes(16) // same as aes.GenerateIV() in C#
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        val pt = plain.toByteArray(StandardCharsets.UTF_8)
        val ct = cipher.doFinal(pt)

        val combined = ByteArray(16 + ct.size)
        System.arraycopy(iv, 0, combined, 0, 16)
        System.arraycopy(ct, 0, combined, 16, ct.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Decrypts Base64( IV(16) || CIPHERTEXT ). Handy for local tests. */
    fun decryptCombinedBase64(combinedB64: String): String {
        val combined = Base64.decode(combinedB64.trim(), Base64.NO_WRAP)
        require(combined.size > 16) { "Cipher too short." }

        val iv = combined.copyOfRange(0, 16)
        val ct = combined.copyOfRange(16, combined.size)

        val key = SecretKeySpec(keyBytes(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        val pt = cipher.doFinal(ct)
        return String(pt, StandardCharsets.UTF_8)
    }
}
