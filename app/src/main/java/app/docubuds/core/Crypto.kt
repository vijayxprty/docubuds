package app.docubuds.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for vault data. Pure JVM code (javax.crypto) so it is
 * unit-testable without Android. Keys come from [generateDataKey] (fresh per
 * vault, from secure randomness) and are wrapped by the AndroidKeyStore master
 * key in the Android layer (KeystoreKeyManager) — never stored in plaintext.
 *
 * Blob layout: 12-byte random IV || ciphertext+tag.
 */
object VaultCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun generateDataKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, SecureRandom())
        return kg.generateKey()
    }

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "ciphertext too short" }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ct = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    fun encryptString(key: SecretKey, text: String): String =
        Base64.getEncoder().encodeToString(encrypt(key, text.toByteArray(Charsets.UTF_8)))

    fun decryptString(key: SecretKey, base64: String): String =
        String(decrypt(key, Base64.getDecoder().decode(base64)), Charsets.UTF_8)

    fun keyToBytes(key: SecretKey): ByteArray = key.encoded

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")
}
