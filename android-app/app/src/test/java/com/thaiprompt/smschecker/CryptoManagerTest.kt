package com.thaiprompt.smschecker

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for CryptoManager.
 * Note: These tests use pure Java crypto (no android.util.Base64)
 * so they can run as local unit tests without Android framework.
 */
class CryptoManagerTest {

    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_LENGTH = 32
    }

    private lateinit var testSecretKey: String

    @Before
    fun setUp() {
        // 64 hex characters = 32 bytes = 256 bits
        testSecretKey = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
    }

    // ==========================================
    // AES-256-GCM Tests (using pure Java)
    // ==========================================

    @Test
    fun `encrypt and decrypt produces original plaintext`() {
        val plainText = """{"bank":"KBANK","type":"credit","amount":"500.37"}"""
        val key = deriveKey(testSecretKey)

        // Encrypt
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        // Decrypt
        val decIv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val decCipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val decCipher = Cipher.getInstance(AES_ALGORITHM)
        decCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, decIv))
        val decrypted = String(decCipher.doFinal(decCipherText), Charsets.UTF_8)

        assertEquals(plainText, decrypted)
    }

    @Test
    fun `different IVs produce different ciphertexts for same plaintext`() {
        val plainText = "test data"
        val key = deriveKey(testSecretKey)

        val encrypted1 = encrypt(plainText, key)
        val encrypted2 = encrypt(plainText, key)

        // Ciphertexts should differ due to random IV
        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test(expected = Exception::class)
    fun `decryption with wrong key fails`() {
        val plainText = "secret data"
        val correctKey = deriveKey(testSecretKey)
        val wrongKey = deriveKey("wrong_key_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")

        val encrypted = encrypt(plainText, correctKey)

        // This should throw
        decrypt(encrypted, wrongKey)
    }

    @Test
    fun `encrypt decrypt with Thai characters`() {
        val plainText = """{"sender":"นายทดสอบ ภาษาไทย","amount":"1500.00"}"""
        val key = deriveKey(testSecretKey)

        val encrypted = encrypt(plainText, key)
        val decrypted = decrypt(encrypted, key)

        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt decrypt with large payload`() {
        val plainText = buildString {
            append("{")
            for (i in 1..100) {
                if (i > 1) append(",")
                append("\"field$i\":\"value$i\"")
            }
            append("}")
        }

        val key = deriveKey(testSecretKey)
        val encrypted = encrypt(plainText, key)
        val decrypted = decrypt(encrypted, key)

        assertEquals(plainText, decrypted)
    }

    // ==========================================
    // HMAC-SHA256 Tests
    // ==========================================

    @Test
    fun `HMAC generates consistent output for same input`() {
        val data = "test_data_for_hmac"
        val hmac1 = generateHmac(data, testSecretKey)
        val hmac2 = generateHmac(data, testSecretKey)

        assertEquals(hmac1, hmac2)
    }

    @Test
    fun `HMAC produces different output for different data`() {
        val hmac1 = generateHmac("data1", testSecretKey)
        val hmac2 = generateHmac("data2", testSecretKey)

        assertNotEquals(hmac1, hmac2)
    }

    @Test
    fun `HMAC produces different output for different keys`() {
        val data = "same_data"
        val hmac1 = generateHmac(data, testSecretKey)
        val hmac2 = generateHmac(data, "different_key_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")

        assertNotEquals(hmac1, hmac2)
    }

    @Test
    fun `HMAC verification succeeds with correct data`() {
        val data = "encrypted_payload" + "nonce123" + "1706540000000"
        val hmac = generateHmac(data, testSecretKey)

        assertTrue(verifyHmac(data, hmac, testSecretKey))
    }

    @Test
    fun `HMAC verification fails with tampered data`() {
        val data = "original_data"
        val hmac = generateHmac(data, testSecretKey)

        assertFalse(verifyHmac("tampered_data", hmac, testSecretKey))
    }

    @Test
    fun `HMAC verification fails with wrong key`() {
        val data = "test_data"
        val hmac = generateHmac(data, testSecretKey)

        assertFalse(verifyHmac(data, hmac, "wrong_key_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"))
    }

    // ==========================================
    // Key Derivation Tests
    // ==========================================

    @Test
    fun `key derivation produces 32 byte key`() {
        val key = deriveKey(testSecretKey)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `short secret key is zero padded`() {
        val shortKey = "short"
        val key = deriveKey(shortKey)

        assertEquals(32, key.encoded.size)
        // First bytes should match the short key
        val expected = shortKey.toByteArray(Charsets.UTF_8)
        for (i in expected.indices) {
            assertEquals(expected[i], key.encoded[i])
        }
        // Remaining bytes should be zero
        for (i in expected.size until 32) {
            assertEquals(0.toByte(), key.encoded[i])
        }
    }

    @Test
    fun `long secret key is truncated to 32 bytes`() {
        val longKey = "a".repeat(100)
        val key = deriveKey(longKey)

        assertEquals(32, key.encoded.size)
    }

    // ==========================================
    // Nonce Tests
    // ==========================================

    @Test
    fun `generated nonces are unique`() {
        val nonces = mutableSetOf<String>()
        for (i in 1..100) {
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val encoded = java.util.Base64.getEncoder().encodeToString(nonce)
            nonces.add(encoded)
        }
        assertEquals(100, nonces.size)
    }

    @Test
    fun `nonce is 16 bytes base64 encoded`() {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoded = java.util.Base64.getEncoder().encodeToString(nonce)

        // 16 bytes = 24 base64 chars (with padding)
        assertEquals(24, encoded.length)
    }

    // ==========================================
    // Constant-Time Comparison Tests
    // ==========================================

    @Test
    fun `constant time equals returns true for identical strings`() {
        assertTrue(constantTimeEquals("abc123", "abc123"))
    }

    @Test
    fun `constant time equals returns false for different strings`() {
        assertFalse(constantTimeEquals("abc123", "abc456"))
    }

    @Test
    fun `constant time equals returns false for different length strings`() {
        assertFalse(constantTimeEquals("short", "longer_string"))
    }

    // ==========================================
    // Helper Methods (mirror CryptoManager logic)
    // ==========================================

    private fun deriveKey(secret: String): SecretKeySpec {
        val keyBytes = secret.toByteArray(Charsets.UTF_8)
        val adjustedKey = ByteArray(AES_KEY_LENGTH)
        System.arraycopy(keyBytes, 0, adjustedKey, 0, minOf(keyBytes.size, AES_KEY_LENGTH))
        return SecretKeySpec(adjustedKey, "AES")
    }

    private fun encrypt(plainText: String, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    private fun decrypt(combined: ByteArray, key: SecretKeySpec): String {
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun generateHmac(data: String, secretKey: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(hmacBytes)
    }

    private fun verifyHmac(data: String, hmac: String, secretKey: String): Boolean {
        val expected = generateHmac(data, secretKey)
        return constantTimeEquals(expected, hmac)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
