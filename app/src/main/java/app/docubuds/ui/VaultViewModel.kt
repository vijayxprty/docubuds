package app.docubuds.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.docubuds.android.KeystoreKeyManager
import app.docubuds.android.ReminderWorker
import app.docubuds.android.VaultStore
import app.docubuds.core.LockLogic
import app.docubuds.core.RecordType
import app.docubuds.core.Space
import app.docubuds.core.TrashEntry
import app.docubuds.core.VaultData
import app.docubuds.core.VaultRecord
import app.docubuds.core.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen { SPACES, SEARCH, TRASH, MORE }

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val store = VaultStore(application, KeystoreKeyManager(application))

    private val _vault = MutableStateFlow<VaultData?>(null)
    val vault: StateFlow<VaultData?> = _vault.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _blankForRecents = MutableStateFlow(false)
    val blankForRecents: StateFlow<Boolean> = _blankForRecents.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.SPACES)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _currentSpaceId = MutableStateFlow<String?>(null)
    val currentSpaceId: StateFlow<String?> = _currentSpaceId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Last user interaction; the app locks 1 minute after this. */
    var lastActiveMs: Long = System.currentTimeMillis()
        private set

    val isSetup: Boolean get() = store.exists()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val data = store.load()
            if (data != null) {
                val purged = store.purgeTrash(data)
                if (purged != data) store.save(purged)
                _vault.value = purged
            }
            _loaded.value = true
        }
    }

    fun touch() { lastActiveMs = System.currentTimeMillis() }

    /** Called on resume: lock if inactive for >= 1 minute. Returns true if locked now. */
    fun checkInactivityLock(): Boolean {
        if (_unlocked.value && LockLogic.shouldLock(lastActiveMs, System.currentTimeMillis())) {
            _unlocked.value = false
            return true
        }
        return false
    }

    fun setBlankForRecents(blank: Boolean) { _blankForRecents.value = blank }

    fun unlock() {
        _unlocked.value = true
        touch()
        ReminderWorker.schedule(getApplication())
    }

    fun lock() { _unlocked.value = false }

    fun goTo(screen: AppScreen) { _screen.value = screen; touch() }
    fun openSpace(spaceId: String?) { _currentSpaceId.value = spaceId; touch() }
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** First launch: save the display name and seed the suggested spaces.
     *  The in-memory state is set synchronously so the unlock that follows
     *  immediately cannot race the disk write. */
    fun setup(displayName: String) {
        val spaces = VaultData.SUGGESTED_SPACES.mapIndexed { i, name ->
            Space(name = name, order = i)
        }
        val data = VaultData(displayName = displayName.trim(), spaces = spaces)
        _vault.value = data
        viewModelScope.launch(Dispatchers.IO) { store.save(data) }
    }

    private fun updateVault(transform: (VaultData) -> VaultData) {
        val current = _vault.value ?: return
        val next = transform(current)
        _vault.value = next
        viewModelScope.launch(Dispatchers.IO) { store.save(next) }
        touch()
    }

    fun addSpace(name: String, parentId: String? = null) {
        updateVault { d ->
            val order = d.spaces.filter { it.parentId == parentId }.size
            d.copy(spaces = d.spaces + Space(name = name.trim(), parentId = parentId, order = order))
        }
    }

    fun renameSpace(space: Space, newName: String) {
        updateVault { d ->
            d.copy(spaces = d.spaces.map { if (it.id == space.id) it.copy(name = newName.trim()) else it })
        }
    }

    fun deleteSpace(space: Space) {
        // Move its records to trash, then remove the space and its child spaces.
        updateVault { d ->
            val childIds = d.spaces.filter { it.parentId == space.id }.map { it.id }
            val affected = listOf(space.id) + childIds
            val now = System.currentTimeMillis()
            val trashed = d.records.filter { it.spaceId in affected }
                .map { TrashEntry(it, now) }
            d.copy(
                spaces = d.spaces.filter { it.id !in affected },
                records = d.records.filter { it.spaceId !in affected },
                trash = d.trash + trashed
            )
        }
    }

    fun addRecord(record: VaultRecord) {
        updateVault { d -> d.copy(records = d.records + record) }
    }

    fun updateRecord(record: VaultRecord) {
        updateVault { d ->
            d.copy(records = d.records.map { if (it.id == record.id) record.copy(updatedAt = System.currentTimeMillis()) else it })
        }
    }

    fun deleteRecord(record: VaultRecord) {
        updateVault { d ->
            d.copy(
                records = d.records.filter { it.id != record.id },
                trash = d.trash + TrashEntry(record)
            )
        }
    }

    fun restoreFromTrash(entry: TrashEntry) {
        updateVault { d ->
            d.copy(
                trash = d.trash.filter { it.record.id != entry.record.id },
                records = d.records + entry.record
            )
        }
    }

    fun deleteForever(entry: TrashEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            entry.record.fileId?.let { store.deleteAttachment(it) }
        }
        updateVault { d -> d.copy(trash = d.trash.filter { it.record.id != entry.record.id }) }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _vault.value?.trash?.forEach { it.record.fileId?.let { id -> store.deleteAttachment(id) } }
        }
        updateVault { d -> d.copy(trash = emptyList()) }
    }

    fun markNotified(key: String) {
        updateVault { d -> d.copy(notified = d.notified + key) }
    }

    /** Search titles, field values and OCR text (in-document search). */
    fun searchResults(query: String): List<VaultRecord> {
        val data = _vault.value ?: return emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return data.records.filter { r ->
            r.title.lowercase().contains(q) ||
                r.ocrText?.lowercase()?.contains(q) == true ||
                r.fields.values.any { it.lowercase().contains(q) } ||
                r.fileName?.lowercase()?.contains(q) == true
        }
    }

    fun spacePath(spaceId: String?): String {
        val data = _vault.value ?: return ""
        val chain = mutableListOf<String>()
        var cur = data.spaces.find { it.id == spaceId }
        while (cur != null) {
            chain += cur.name
            cur = data.spaces.find { it.id == cur?.parentId }
        }
        return chain.reversed().joinToString(" › ")
    }

    /** Decrypts an attachment into the cache for viewing/sharing. Returns the raw bytes. */
    fun readAttachmentBytes(fileId: String): ByteArray? =
        store.readAttachment(fileId)

    fun saveAttachment(bytes: ByteArray): String = store.saveAttachment(bytes)

    /**
     * Shares a record's file in original quality via Android's share menu
     * (WhatsApp, email, any app). The file is decrypted to a cache file and
     * shared as a file/document — receiving apps may compress images, which is
     * why sharing as a file is preferred. Text-only records are shared as text.
     */
    fun shareRecord(context: Context, record: VaultRecord) {
        val bytes = record.fileId?.let { store.readAttachment(it) }
        if (bytes != null) {
            val cacheDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val out = File(cacheDir, record.fileName ?: "document")
            out.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = record.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, record.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${record.title}"))
        } else {
            val text = buildString {
                appendLine(record.title)
                record.fields.forEach { (k, v) -> appendLine("$k: $v") }
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${record.title}"))
        }
        touch()
    }

    /** Exports a record's file: writes the decrypted bytes to a SAF Uri chosen by the user. */
    fun exportToUri(context: Context, record: VaultRecord, uri: Uri): Boolean {
        val bytes = record.fileId?.let { store.readAttachment(it) } ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            touch()
            true
        } catch (_: Exception) {
            false
        }
    }
}
