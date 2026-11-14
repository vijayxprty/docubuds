package app.docubuds.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoTest {

    @Test
    fun `encrypt-decrypt round trip restores plaintext`() {
        val key = VaultCrypto.generateDataKey()
        val plain = "Aadhaar 1234 5678 9012 — secret".toByteArray(Charsets.UTF_8)
        val decrypted = VaultCrypto.decrypt(key, VaultCrypto.encrypt(key, plain))
        assertEquals(String(plain, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `string round trip preserves unicode and leading zeros`() {
        val key = VaultCrypto.generateDataKey()
        val text = "Account 0012345678 · IFSC SBIN0001234 · हिन्दी"
        assertEquals(text, VaultCrypto.decryptString(key, VaultCrypto.encryptString(key, text)))
    }

    @Test
    fun `decrypt with a different key fails`() {
        val key = VaultCrypto.generateDataKey()
        val other = VaultCrypto.generateDataKey()
        val blob = VaultCrypto.encrypt(key, "secret".toByteArray())
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(other, blob)
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val key = VaultCrypto.generateDataKey()
        val blob = VaultCrypto.encrypt(key, "secret".toByteArray()).toMutableList()
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(key, blob.toByteArray())
        }
    }

    @Test
    fun `each encryption uses a fresh random IV`() {
        val key = VaultCrypto.generateDataKey()
        val a = VaultCrypto.encrypt(key, "same".toByteArray())
        val b = VaultCrypto.encrypt(key, "same".toByteArray())
        assert(!a.contentEquals(b))
    }

    @Test
    fun `key serialisation round trip`() {
        val key = VaultCrypto.generateDataKey()
        val restored = VaultCrypto.keyFromBytes(VaultCrypto.keyToBytes(key))
        val blob = VaultCrypto.encrypt(key, "x".toByteArray())
        assertEquals("x", String(VaultCrypto.decrypt(restored, blob), Charsets.UTF_8))
    }

    @Test
    fun `two generated data keys differ`() {
        assertNotEquals(
            VaultCrypto.keyToBytes(VaultCrypto.generateDataKey()).toList(),
            VaultCrypto.keyToBytes(VaultCrypto.generateDataKey()).toList()
        )
    }
}
