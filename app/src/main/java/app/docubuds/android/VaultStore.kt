package app.docubuds.android

import android.content.Context
import app.docubuds.core.TrashLogic
import app.docubuds.core.VaultCrypto
import app.docubuds.core.VaultData
import app.docubuds.core.VaultRecord
import com.google.gson.Gson
import java.io.File
import javax.crypto.SecretKey

/**
 * Encrypted vault storage in the app's internal (private) storage.
 *
 * - `vault/index.enc` — the whole VaultData (titles, space/folder names, OCR
 *   text, fields, indexes) serialised to JSON and encrypted with the data key.
 * - `vault/files/<random-id>.enc` — each attachment encrypted with the data
 *   key under a random id; the real filename lives only inside the encrypted
 *   index, never on the filesystem.
 * - No vault content is ever written to external/shared storage except through
 *   the explicit, user-initiated Export/Share path (decrypted to a cache file
 *   and handed to the Android share sheet / SAF picker).
 * - allowBackup=false in the manifest excludes all of this from OS cloud backup.
 */
class VaultStore(context: Context, private val keyManager: KeystoreKeyManager) {
    private val gson = Gson()
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val filesDir = File(vaultDir, "files").apply { mkdirs() }
    private val indexFile = File(vaultDir, "index.enc")

    fun exists(): Boolean = indexFile.exists()

    fun load(): VaultData? {
        if (!indexFile.exists()) return null
        val key = keyManager.dataKey()
        val blob = indexFile.readBytes()
        val json = VaultCrypto.decryptString(key, android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP))
        return gson.fromJson(json, VaultData::class.java)
    }

    /** Atomic save: write temp, then rename. */
    @Synchronized
    fun save(data: VaultData) {
        val key = keyManager.dataKey()
        val blob = VaultCrypto.encrypt(key, gson.toJson(data).toByteArray(Charsets.UTF_8))
        val tmp = File(vaultDir, "index.tmp")
        tmp.writeBytes(blob)
        tmp.renameTo(indexFile)
    }

    /** Stores an attachment, returns its random file id. Never overwrites silently. */
    fun saveAttachment(bytes: ByteArray): String {
        val key = keyManager.dataKey()
        var id: String
        var file: File
        do {
            id = java.util.UUID.randomUUID().toString()
            file = File(filesDir, "$id.enc")
        } while (file.exists())
        file.writeBytes(VaultCrypto.encrypt(key, bytes))
        return id
    }

    fun readAttachment(fileId: String): ByteArray? {
        val file = File(filesDir, "$fileId.enc")
        if (!file.exists()) return null
        return VaultCrypto.decrypt(keyManager.dataKey(), file.readBytes())
    }

    fun deleteAttachment(fileId: String) {
        File(filesDir, "$fileId.enc").delete()
    }

    /** Purges trash entries older than 30 days (and their attachments). Returns purged records. */
    fun purgeTrash(data: VaultData, nowMs: Long = System.currentTimeMillis()): VaultData {
        val (kept, purged) = TrashLogic.purge(data.trash, nowMs)
        purged.forEach { it.record.fileId?.let(::deleteAttachment) }
        return data.copy(trash = kept)
    }
}
