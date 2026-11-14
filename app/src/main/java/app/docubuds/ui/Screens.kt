package app.docubuds.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.docubuds.core.RecordType
import app.docubuds.core.Space
import app.docubuds.core.TrashEntry
import app.docubuds.core.VaultRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sensitive fields are masked until tapped to reveal (tap reveals, no second unlock). */
val SENSITIVE_KEYS = setOf("password", "pin", "cvv", "account number", "card number", "number")

fun formatEpochDay(epochDay: Long?): String =
    epochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ISO_LOCAL_DATE) } ?: "—"

fun formatMillis(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy"))

@Composable
fun DocuBudsRoot(
    vm: VaultViewModel,
    authenticate: (() -> Unit, (String) -> Unit) -> Unit,
    openSecuritySettings: () -> Unit,
    isDeviceSecure: () -> Boolean
) {
    val vault by vm.vault.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val donate = rememberDonateLauncher()
    val context = LocalContext.current

    when {
        !loaded -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { Text("Loading DocuBuds…") }
        vault == null -> SetupScreen(onSetup = { name ->
            vm.setup(name)
            authenticate({ vm.unlock() }, {})
        })
        !unlocked -> LockScreen(
            isDeviceSecure = isDeviceSecure(),
            onUnlock = { authenticate({ vm.unlock() }, {}) },
            onOpenSettings = openSecuritySettings
        )
        else -> MainScaffold(vm, donate, context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val screen by vm.screen.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddRecord by remember { mutableStateOf(false) }
    var showAddDocument by remember { mutableStateOf(false) }
    val vaultForAdd by vm.vault.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DocuBuds") },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text("On your device", fontSize = 13.sp)
                        DonateHeartButton(onDonate = { donate(context) })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == AppScreen.SPACES,
                    onClick = { vm.goTo(AppScreen.SPACES); vm.openSpace(null) },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    label = { Text("Spaces") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.SEARCH,
                    onClick = { vm.goTo(AppScreen.SEARCH) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.TRASH,
                    onClick = { vm.goTo(AppScreen.TRASH) },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    label = { Text("Trash") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.MORE,
                    onClick = { vm.goTo(AppScreen.MORE) },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                    label = { Text("More") }
                )
            }
        },
        floatingActionButton = {
            if (screen == AppScreen.SPACES) {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screen) {
                AppScreen.SPACES -> SpacesScreen(vm, donate, context)
                AppScreen.SEARCH -> SearchScreen(vm, donate, context)
                AppScreen.TRASH -> TrashScreen(vm, donate, context)
                AppScreen.MORE -> MoreScreen(vm, donate, context)
            }
        }
    }
    if (showAddMenu) {
        AddMenuDialog(
            onAddRecord = { showAddMenu = false; showAddRecord = true },
            onAddDocument = { showAddMenu = false; showAddDocument = true },
            onDismiss = { showAddMenu = false }
        )
    }
    val addSpaceId = vm.currentSpaceId.collectAsState().value
        ?: vaultForAdd?.spaces?.firstOrNull()?.id
    if (showAddRecord) {
        AddRecordDialog(vm, addSpaceId) { showAddRecord = false }
    }
    if (showAddDocument) {
        AddDocumentDialog(vm, addSpaceId) { showAddDocument = false }
    }
}

@Composable
fun SetupScreen(onSetup: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to DocuBuds", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("A private place on your phone for your documents, IDs, logins and cards. Fully offline — nothing leaves this phone except what you deliberately share.")
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Important: DocuBuds has no backup and no recovery key. Your vault is " +
                "unlocked only by your phone's own lock (PIN, pattern, password, face or " +
                "fingerprint). If you lose this phone, uninstall the app, clear its data, " +
                "or lose its encryption keys, everything saved here is gone for good. " +
                "There is no way to recover it.",
            fontSize = 14.sp
        )
        error?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (name.isBlank()) error = "Please enter a display name."
                else onSetup(name)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Set up DocuBuds") }
    }
}

@Composable
fun LockScreen(isDeviceSecure: Boolean, onUnlock: () -> Unit, onOpenSettings: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(16.dp))
        Text("DocuBuds is locked", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (!isDeviceSecure) {
            Text("Your phone has no lock set (no PIN, pattern or password). DocuBuds can only be unlocked with your phone's lock, so it cannot open until you set one.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpenSettings) { Text("Open phone security settings") }
        } else {
            Text("Unlock with your phone lock — PIN, pattern, password, face or fingerprint.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
            error?.let { Text(it) }
        }
    }
}

@Composable
fun SpacesScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val vault by vm.vault.collectAsState()
    val currentSpaceId by vm.currentSpaceId.collectAsState()
    val data = vault ?: return
    val currentSpace = data.spaces.find { it.id == currentSpaceId }
    val childSpaces = data.spaces.filter { it.parentId == currentSpaceId }.sortedBy { it.order }
    val records = data.records.filter { it.spaceId == currentSpaceId }
    var selectedRecord by remember { mutableStateOf<VaultRecord?>(null) }
    var showAddSpace by remember { mutableStateOf(false) }
    var showAddRecord by remember { mutableStateOf(false) }
    var showAddDocument by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
        item {
            if (currentSpace != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.openSpace(currentSpace.parentId) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(vm.spacePath(currentSpaceId), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            } else {
                Text(
                    "Spaces",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    "Hello, ${data.displayName}. Your documents live only on this phone.",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        items(childSpaces) { space ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.openSpace(space.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(space.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${data.records.count { it.spaceId == space.id }}", fontSize = 13.sp)
            }
        }
        items(records) { record ->
            RecordRow(record) { selectedRecord = record }
        }
        if (childSpaces.isEmpty() && records.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. Tap + to add a space, a record, or a document.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddSpace = true }) { Text("New folder") }
                OutlinedButton(onClick = { showAddRecord = true }) { Text("Add record") }
                OutlinedButton(onClick = { showAddDocument = true }) { Text("Add document") }
            }
        }
        item { DonateFooter(isMoreScreen = false, onDonate = { donate(context) }) }
    }
    if (showAddSpace) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            onDismiss = { showAddSpace = false },
            onConfirm = { vm.addSpace(it, currentSpaceId); showAddSpace = false }
        )
    }
    if (showAddRecord) {
        AddRecordDialog(vm, currentSpaceId ?: data.spaces.firstOrNull()?.id) { showAddRecord = false }
    }
    if (showAddDocument) {
        AddDocumentDialog(vm, currentSpaceId ?: data.spaces.firstOrNull()?.id) { showAddDocument = false }
    }
    selectedRecord?.let { record ->
        RecordDetailDialog(vm, record, donate, context) { selectedRecord = null }
    }
}

@Composable
fun RecordRow(record: VaultRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            record.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            fontSize = 12.sp,
            modifier = Modifier.width(90.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(record.title, fontWeight = FontWeight.Bold)
            if (record.expiryEpochDay != null) {
                Text("Expires ${formatEpochDay(record.expiryEpochDay)}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SearchScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val query by vm.searchQuery.collectAsState()
    val results = remember(query) { vm.searchResults(query) }
    var selectedRecord by remember { mutableStateOf<VaultRecord?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setSearchQuery(it) },
                label = { Text("Search titles, fields and document text") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true
            )
            Text(
                "Search runs on this phone, in English and Hindi, inside your scanned documents too.",
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(results) { record ->
            RecordRow(record) { selectedRecord = record }
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item { Text("No matches.", modifier = Modifier.padding(16.dp)) }
        }
        item { DonateFooter(isMoreScreen = false, onDonate = { donate(context) }) }
    }
    selectedRecord?.let { record ->
        RecordDetailDialog(vm, record, donate, context) { selectedRecord = null }
    }
}

@Composable
fun TrashScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val vault by vm.vault.collectAsState()
    val trash = vault?.trash ?: emptyList()
    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
        item {
            Text("Trash", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
            Text(
                "Deleted items stay here for 30 days, then are gone for good. You can also empty the Trash now.",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (trash.isNotEmpty()) {
                Row(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { vm.emptyTrash() }) { Text("Empty Trash") }
                }
            }
        }
        items(trash) { entry: TrashEntry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.record.title, fontWeight = FontWeight.Bold)
                    Text("Deleted ${formatMillis(entry.deletedAt)}", fontSize = 12.sp)
                }
                TextButton(onClick = { vm.restoreFromTrash(entry) }) { Text("Restore") }
                TextButton(onClick = { vm.deleteForever(entry) }) { Text("Delete forever") }
            }
        }
        if (trash.isEmpty()) {
            item { Text("Trash is empty.", modifier = Modifier.padding(16.dp)) }
        }
        item { DonateFooter(isMoreScreen = false, onDonate = { donate(context) }) }
    }
}

@Composable
fun MoreScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val vault by vm.vault.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
        item {
            Text("More", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About DocuBuds", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version 1.0.0 · Android only")
                    Text("Signed in as: ${vault?.displayName ?: ""} (display name only, not an account)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fully offline. No account, no cloud, no backup, no recovery key. Free, with no ads. Your vault is unlocked only by your phone's lock, and locks again after 1 minute of inactivity.")
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your private records", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("No backup exists. Losing this phone, uninstalling, or clearing app data erases your records permanently. Deleted items can be recovered from Trash for 30 days only.")
                }
            }
        }
        item { DonateFooter(isMoreScreen = true, onDonate = { donate(context) }) }
    }
}

@Composable
fun RecordDetailDialog(vm: VaultViewModel, record: VaultRecord, donate: (Context) -> Unit, context: Context, onDismiss: () -> Unit) {
    var showFile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.title) },
        text = {
            Column {
                Text("Type: ${record.type.name.lowercase().replace('_', ' ')}", fontSize = 13.sp)
                record.spaceId.let { Text("Location: ${vm.spacePath(it)}", fontSize = 13.sp) }
                if (record.expiryEpochDay != null) {
                    Text("Expires: ${formatEpochDay(record.expiryEpochDay)}", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                record.fields.forEach { (key, value) ->
                    MaskedFieldRow(key, value)
                }
                record.fileName?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("File: $it", fontSize = 13.sp)
                    OutlinedButton(onClick = { showFile = true }) { Text("View file") }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.shareRecord(context, record) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("It will move to Trash for 30 days, then be gone for good.") },
            confirmButton = {
                Button(onClick = { vm.deleteRecord(record); confirmDelete = false; onDismiss() }) { Text("Move to Trash") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (showFile) {
        FileViewerDialog(vm, record) { showFile = false }
    }
}

@Composable
fun MaskedFieldRow(key: String, value: String) {
    var revealed by remember { mutableStateOf(false) }
    val sensitive = SENSITIVE_KEYS.any { key.lowercase().contains(it) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$key: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            text = if (sensitive && !revealed) "••••••••" else value,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (sensitive) {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide" else "Reveal"
                )
            }
        }
    }
}

@Composable
fun TextInputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddMenuDialog(onAddRecord: () -> Unit, onAddDocument: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add") },
        text = {
            Column {
                TextButton(onClick = onAddRecord) { Text("Add a record (ID, bank, card, login, note)") }
                TextButton(onClick = onAddDocument) { Text("Add a document (scan, gallery, file)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
