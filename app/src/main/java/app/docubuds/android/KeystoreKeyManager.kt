package app.docubuds.android

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.docubuds.core.VaultCrypto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-vault data key management.
 *
 * - One fresh random AES-256 data key per vault, generated with secure
 *   randomness on first launch.
 * - The data key is stored only in wrapped form: encrypted under a master key
 *   held in the AndroidKeyStore ("DocuBudsMaster"). The wrapped bytes live in
 *   private SharedPreferences; the raw data key is never written to disk.
 * - The master key does NOT require user authentication (setUserAuthenticationRequired
 *   is false). This is deliberate and documented: the unlock is BiometricPrompt
 *   with BIOMETRIC_STRONG | DEVICE_CREDENTIAL, and Android does not allow a
 *   CryptoObject with device-credential authentication — so the key cannot be
 *   auth-bound while still allowing PIN/pattern unlock. The unlock is therefore
 *   an access gate enforced by the app (BiometricPrompt on every unlock, plus
 *   the 1-minute inactivity lock); the keys themselves are KeyStore-bound and
 *   never leave the device. A stolen vault file without the device KeyStore
 *   cannot be decrypted.
 * - No keys, documents or vault data are ever put in the APK.
 */
class KeystoreKeyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("docubuds_keys", Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    private fun masterKey(): SecretKey {
        val existing = keyStore.getKey(MASTER_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    /** Returns the vault data key, generating and wrapping a fresh one on first use. */
    @Synchronized
    fun dataKey(): SecretKey {
        val wrapped = prefs.getString(WRAPPED_KEY, null)
        val master = masterKey()
        if (wrapped != null) {
            val blob = Base64.decode(wrapped, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, 12)
            val ct = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, iv))
            return VaultCrypto.keyFromBytes(cipher.doFinal(ct))
        }
        val fresh = VaultCrypto.generateDataKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master)
        val blob = cipher.iv + cipher.doFinal(VaultCrypto.keyToBytes(fresh))
        prefs.edit().putString(WRAPPED_KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        return fresh
    }

    companion object {
        private const val MASTER_ALIAS = "DocuBudsMaster"
        private const val WRAPPED_KEY = "wrapped_data_key"
    }
}
