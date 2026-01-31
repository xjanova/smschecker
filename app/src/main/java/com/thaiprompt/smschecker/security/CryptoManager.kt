package com.thaiprompt.smschecker.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-256-GCM encryption and HMAC-SHA256 signing
 * for secure communication with the Laravel backend.
 */
class CryptoManager {

    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val GCM_IV_LENGTH = 12  // 96 bits
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val AES_KEY_LENGTH = 32  // 256 bits

        // PBKDF2 parameters for key derivation
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256 // bits

        // Fixed salt derived from app identifier (ไม่ต้องส่งไป server เพราะ server ใช้ salt เดียวกัน)
        private const val KEY_DERIVATION_SALT = "thaiprompt-smschecker-v1"
        // แยก context สำหรับ encrypt key กับ HMAC key เพื่อไม่ใช้ key เดียวกัน
        private const val ENCRYPT_KEY_CONTEXT = "encryption"
        private const val HMAC_KEY_CONTEXT = "hmac-signing"
    }

    /**
     * Encrypt data using AES-256-GCM.
     * Returns Base64 encoded string: IV + Ciphertext + Tag
     */
    fun encrypt(plainText: String, secretKey: String): String {
        val key = deriveKey(secretKey)
        val iv = generateIv()

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine IV + CipherText
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt data using AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String, secretKey: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val key = deriveKey(secretKey)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plainText = cipher.doFinal(cipherText)
        return String(plainText, Charsets.UTF_8)
    }

    /**
     * Generate HMAC-SHA256 signature for request integrity.
     * Uses a dedicated HMAC key derived from the secret (separate from encryption key).
     */
    fun generateHmac(data: String, secretKey: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val hmacKey = deriveKey(secretKey, HMAC_KEY_CONTEXT)
        mac.init(hmacKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }

    /**
     * Verify HMAC-SHA256 signature.
     */
    fun verifyHmac(data: String, hmac: String, secretKey: String): Boolean {
        val expected = generateHmac(data, secretKey)
        return constantTimeEquals(expected, hmac)
    }

    /**
     * Generate a unique nonce for each request to prevent replay attacks.
     */
    fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Derive a strong AES-256 key from the secret using PBKDF2.
     *
     * @param secret The secret key string (any length)
     * @param context Purpose context to derive separate keys for encryption vs HMAC
     */
    private fun deriveKey(secret: String, context: String = ENCRYPT_KEY_CONTEXT): SecretKey {
        val salt = "$KEY_DERIVATION_SALT:$context".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
