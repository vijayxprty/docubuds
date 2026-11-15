package app.docubuds.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.docubuds.android.MlKitOcr
import app.docubuds.core.ExpiryParser
import app.docubuds.core.RecordType
import app.docubuds.core.VaultRecord
import app.docubuds.core.newId
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Add/edit a structured record (ID, bank account, card, login, note).
 * All fields are stored encrypted in the vault index. Card PIN and CVV are
 * included by the owner's explicit decision — there are no excluded fields.
 * The website field of a login is inert text: it is never fetched, validated
 * or opened automatically.
 */
@Composable
fun AddRecordDialog(vm: VaultViewModel, spaceId: String?, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(RecordType.ID) }
    var title by remember { mutableStateOf("") }
    val fields = remember { mutableStateOf(mutableMapOf<String, String>()) }
    var expiryText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldDefs: List<String> = when (type) {
        RecordType.ID -> listOf("ID type (Aadhaar / PAN / passport / licence / voter ID)", "Number", "Name on document")
        RecordType.BANK_ACCOUNT -> listOf("Bank name", "Account number", "IFSC", "Branch")
        RecordType.CARD -> listOf("Card name", "Card number", "Expiry (YYYY-MM-DD)", "PIN", "CVV")
        RecordType.LOGIN -> listOf("Service", "Website (text only, never opened)", "Username / email", "Password", "Notes")
        RecordType.NOTE -> listOf("Note")
        RecordType.DOCUMENT -> listOf("Details")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add record") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t; fields.value = mutableMapOf() },
                            label = { Text(t.name.lowercase().replace('_', ' ')) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                fieldDefs.forEach { label ->
                    val key = label.substringBefore(" (").lowercase()
                    OutlinedTextField(
                        value = fields.value[key] ?: "",
                        onValueChange = { v ->
                            fields.value = fields.value.toMutableMap().apply { put(key, v) }
                        },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = expiryText,
                    onValueChange = { expiryText = it },
                    label = { Text("Expiry date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) { error = "Please enter a title."; return@Button }
                val expiry = expiryText.trim().takeIf { it.isNotEmpty() }?.let {
                    runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull()
                }
                if (expiryText.isNotBlank() && expiry == null) {
                    error = "Expiry date must be YYYY-MM-DD."
                    return@Button
                }
                if (spaceId == null) {
                    error = "Create a folder first — tap “New folder”, then add your record to it."
                    return@Button
                }
                val targetSpace = spaceId
                vm.addRecord(
                    VaultRecord(
                        type = type,
                        spaceId = targetSpace,
                        title = title.trim(),
                        fields = fields.value.filterValues { it.isNotBlank() },
                        expiryEpochDay = expiry
                    )
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Add a document: camera scan (CameraX), gallery pick, or file pick.
 * Any file type is accepted for storage. If the file is an image, on-device
 * OCR reads the text (English + Hindi); an expiry date found in the text is
 * shown to the user to confirm before saving — never saved blind.
 * Video/audio files are stored only; in-app playback is deferred (spec §5).
 */
@Composable
fun AddDocumentDialog(vm: VaultViewModel, spaceId: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedMime by remember { mutableStateOf("*/*") }
    var suggestedExpiry by remember { mutableStateOf<LocalDate?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var expiryText by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handlePicked(bytes: ByteArray, name: String, mime: String) {
        pickedBytes = bytes
        pickedName = name
        pickedMime = mime
        if (title.isBlank()) title = name.substringBeforeLast('.')
        // OCR only for images; other files keep their original bytes untouched.
        if (mime.startsWith("image/")) {
            scope.launch {
                val text = runCatching { MlKitOcr().recognize(bytes) }.getOrDefault("")
                ocrText = text
                val suggested = ExpiryParser.suggestExpiry(text)
                suggestedExpiry = suggested
                if (suggested != null) expiryText = suggested.toString()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
        handlePicked(bytes, name, mime)
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        handlePicked(bytes, name, mime)
    }

    if (showCamera) {
        CameraScreen(
            onCaptured = { bytes ->
                showCamera = false
                handlePicked(bytes, "scan-${System.currentTimeMillis()}.jpg", "image/jpeg")
            },
            onClose = { showCamera = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add document") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showCamera = true }) { Text("Scan") }
                    Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Gallery") }
                    Button(onClick = { fileLauncher.launch("*/*") }) { Text("File") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(pickedName?.let { "Picked: $it" } ?: "No file picked yet. Any file type is accepted.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestedExpiry != null) {
                    Text("Expiry date found in the scan: $suggestedExpiry. Confirm or correct it below — it is not saved until you confirm.")
                }
                OutlinedTextField(
                    value = expiryText,
                    onValueChange = { expiryText = it },
                    label = { Text("Expiry date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bytes = pickedBytes
                    if (bytes == null) { error = "Pick a file, photo or scan first."; return@Button }
                    if (title.isBlank()) { error = "Please enter a title."; return@Button }
                    val expiry = expiryText.trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull()
                    }
                    if (expiryText.isNotBlank() && expiry == null) {
                        error = "Expiry date must be YYYY-MM-DD."
                        return@Button
                    }
                    if (spaceId == null) {
                        error = "Create a folder first — tap “New folder”, then add your document to it."
                        return@Button
                    }
                    val targetSpace = spaceId
                    val fileId = vm.saveAttachment(bytes)
                    vm.addRecord(
                        VaultRecord(
                            id = newId(),
                            type = RecordType.DOCUMENT,
                            spaceId = targetSpace,
                            title = title.trim(),
                            fileId = fileId,
                            fileName = pickedName,
                            mimeType = pickedMime,
                            expiryEpochDay = expiry,
                            ocrText = ocrText
                        )
                    )
                    Toast.makeText(context, "Document saved", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
