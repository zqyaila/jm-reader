package com.jm.reader.data.net

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helpers that mirror the JMComic3 mobile API / web-app usage.
 *
 * The API uses:
 *  - md5 for request tokens and AES key derivation
 *  - AES-256-ECB (PKCS7) where the key is the ASCII bytes of an md5 hex string
 *  - Base64 for ciphertext transport
 *
 * The reference implementation is `jmcomic.JmCryptoTool`
 * (`decode_resp_data`: base64 → AES-256-ECB with key `md5("<ts><secret>")` → strip PKCS#7).
 */
object Crypto {

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Decodes base64 the way the servers actually emit it.
     *
     * The CDN host-bootstrap files are served with a UTF-8 BOM (`EF BB BF`), and Java/Android
     * turn those bytes into `?` characters when the body is read as an ASCII string. A strict
     * `Base64.decode` rejects them, which used to make the whole bootstrap fall back to a stale
     * embedded payload. So: drop a leading BOM, ignore whitespace, and skip anything that is not
     * part of the base64 alphabet before handing the text to the platform decoder.
     */
    fun base64Decode(text: String): ByteArray? {
        val cleaned = buildString(text.length) {
            for ((index, ch) in text.withIndex()) {
                when {
                    // UTF-8 BOM survives as U+FEFF, or as "?" once the bytes were decoded as ASCII.
                    index == 0 && ch == '\uFEFF' -> Unit
                    ch.isWhitespace() -> Unit
                    ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                    ch == '+' || ch == '/' || ch == '=' -> append(ch)
                    else -> Unit
                }
            }
        }
        if (cleaned.isBlank()) return null
        return runCatching { Base64.decode(cleaned, Base64.DEFAULT) }.getOrNull()
    }

    /**
     * Decrypts a base64 AES-256-ECB payload.
     *
     * @param base64Ciphertext base64-encoded ciphertext
     * @param keyMd5Hex md5 hex string; its ASCII bytes form the AES key (32 bytes -> AES-256)
     */
    fun aesEcbDecrypt(base64Ciphertext: String, keyMd5Hex: String): String? {
        val bytes = base64Decode(base64Ciphertext) ?: return null
        return aesEcbDecrypt(bytes, keyMd5Hex)
    }

    /** Same as [aesEcbDecrypt] but for an already-decoded ciphertext. */
    fun aesEcbDecrypt(ciphertext: ByteArray, keyMd5Hex: String): String? {
        return try {
            val key = SecretKeySpec(keyMd5Hex.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts the AES-encrypted host bootstrap file.
     * Key = ASCII(md5("diosfjckwpqpdfjkvnqQjsik")) (equivalent to `md5("<empty ts><secret>")`).
     */
    fun decryptHostText(encryptedText: String): String? =
        aesEcbDecrypt(encryptedText, md5Hex(HOST_SECRET))

    const val HOST_SECRET = "diosfjckwpqpdfjkvnqQjsik"
}
