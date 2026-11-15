package app.docubuds.core

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

enum class RecordType {
    ID, BANK_ACCOUNT, CARD, LOGIN, NOTE, DOCUMENT
}

/** A named space. Spaces are organisation only, NOT a security boundary. */
data class Space(
    val id: String = newId(),
    val name: String,
    val parentId: String? = null,
    val order: Int = 0
)

data class VaultRecord(
    val id: String = newId(),
    val type: RecordType,
    val spaceId: String,
    val title: String,
    /** Free-form fields, e.g. number / ifsc / pin / cvv / username / password / notes.
     *  All values are stored encrypted inside the vault index (see VaultStore). */
    val fields: Map<String, String> = emptyMap(),
    /** Encrypted attachment id under vault/files/, if any. Real filename is in [fileName]. */
    val fileId: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    /** Expiry as epoch day (java.time.LocalDate.toEpochDay), if known. */
    val expiryEpochDay: Long? = null,
    /** On-device OCR text of the attachment, for in-document search. Encrypted at rest. */
    val ocrText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TrashEntry(
    val record: VaultRecord,
    val deletedAt: Long = System.currentTimeMillis()
)

/** The whole vault, serialised to JSON and encrypted as one blob by VaultStore. */
data class VaultData(
    val displayName: String = "",
    val spaces: List<Space> = emptyList(),
    val records: List<VaultRecord> = emptyList(),
    val trash: List<TrashEntry> = emptyList(),
    /** "recordId:offsetDays" keys already notified, so reminders are not repeated. */
    val notified: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
) {
    // No spaces are seeded: a new vault starts genuinely empty and the
    // user creates their own spaces. (v1.0.0 seeded 7 suggested spaces;
    // removed in v1.0.1 — the owner asked for a true empty state.)
}
