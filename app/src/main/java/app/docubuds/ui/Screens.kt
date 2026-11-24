package app.docubuds.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        ) { Text("Opening DocuBuds…", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
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
    var showSheet by remember { mutableStateOf(false) }
    var showAddRecord by remember { mutableStateOf(false) }
    var showAddDocument by remember { mutableStateOf(false) }
    var showAddSpace by remember { mutableStateOf(false) }
    val vaultForAdd by vm.vault.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DocuBuds", fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                "OFFLINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text("1m lock", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
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
                    icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                    label = { Text("Vault") }
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
                    label = { Text("Security") }
                )
            }
        },
        floatingActionButton = {
            if (screen == AppScreen.SPACES) {
                FloatingActionButton(
                    onClick = { showSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Item", modifier = Modifier.size(24.dp))
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
                AppScreen.SPACES -> SpacesScreen(
                    vm = vm,
                    donate = donate,
                    context = context,
                    onOpenAddSheet = { showSheet = true }
                )
                AppScreen.SEARCH -> SearchScreen(vm, donate, context)
                AppScreen.TRASH -> TrashScreen(vm, donate, context)
                AppScreen.MORE -> MoreScreen(vm, donate, context)
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add to Encrypted Vault",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Hardware encrypted with AES-256 GCM. Never sent to any server.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddSheetCard(
                        title = "Scan Document",
                        subtitle = "Camera OCR",
                        icon = Icons.Filled.CameraAlt,
                        modifier = Modifier.weight(1f)
                    ) {
                        showSheet = false
                        showAddDocument = true
                    }
                    AddSheetCard(
                        title = "Import File",
                        subtitle = "PDF, Image, Doc",
                        icon = Icons.Filled.UploadFile,
                        modifier = Modifier.weight(1f)
                    ) {
                        showSheet = false
                        showAddDocument = true
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddSheetCard(
                        title = "Card or Bank",
                        subtitle = "Masked PIN & CVV",
                        icon = Icons.Filled.CreditCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        showSheet = false
                        showAddRecord = true
                    }
                    AddSheetCard(
                        title = "New Space",
                        subtitle = "Organize folders",
                        icon = Icons.Filled.Folder,
                        modifier = Modifier.weight(1f)
                    ) {
                        showSheet = false
                        showAddSpace = true
                    }
                }
            }
        }
    }

    val addSpaceId = vm.currentSpaceId.collectAsState().value
        ?: vaultForAdd?.spaces?.firstOrNull()?.id
    if (showAddRecord) {
        AddRecordDialog(vm, addSpaceId) { showAddRecord = false }
    }
    if (showAddDocument) {
        AddDocumentDialog(vm, addSpaceId) { showAddDocument = false }
    }
    if (showAddSpace) {
        TextInputDialog(
            title = "New Space",
            label = "Space name (e.g. Identity, Vehicle, Taxes)",
            onDismiss = { showAddSpace = false },
            onConfirm = { vm.addSpace(it, vm.currentSpaceId.value); showAddSpace = false }
        )
    }
}

@Composable
fun AddSheetCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SetupScreen(onSetup: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Welcome to DocuBuds", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your 100% offline personal vault for documents, IDs, cards and logins. Fully encrypted on this device.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            placeholder = { Text("e.g. Personal Vault") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    "DocuBuds has zero cloud sync or master password. Unlock is strictly tied to your phone's biometric or PIN lock. Losing this device or clearing app data erases records permanently.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    lineHeight = 16.sp
                )
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (name.isBlank()) error = "Please enter a display name."
                else onSetup(name)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Initialize Empty Vault", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

@Composable
fun LockScreen(isDeviceSecure: Boolean, onUnlock: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("DocuBuds is Locked", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (!isDeviceSecure) {
            Text(
                "Your phone has no device lock (no PIN, pattern, or biometric password). DocuBuds requires a device lock to encrypt your vault keys.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) {
                Text("Open Security Settings")
            }
        } else {
            Text(
                "Unlock with your phone PIN, pattern, face or fingerprint.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Unlock Vault", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun SpacesScreen(
    vm: VaultViewModel,
    donate: (Context) -> Unit,
    context: Context,
    onOpenAddSheet: () -> Unit
) {
    val vault by vm.vault.collectAsState()
    val currentSpaceId by vm.currentSpaceId.collectAsState()
    val data = vault ?: return
    val currentSpace = data.spaces.find { it.id == currentSpaceId }
    val childSpaces = data.spaces.filter { it.parentId == currentSpaceId }.sortedBy { it.order }
    val records = data.records.filter { it.spaceId == currentSpaceId }
    var selectedRecord by remember { mutableStateOf<VaultRecord?>(null) }
    var selectedCategory by remember { mutableStateOf<RecordType?>(null) }

    val filteredRecords = if (selectedCategory == null) records else records.filter { it.type == selectedCategory }
    val todayEpoch = remember { LocalDate.now().toEpochDay() }

    // Watchtower / Expiry check (documents expiring within 30 days)
    val expiringRecords = remember(data.records) {
        data.records.filter {
            val exp = it.expiryEpochDay
            exp != null && (exp - todayEpoch) in 0..30
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (currentSpace != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.openSpace(currentSpace.parentId) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(vm.spacePath(currentSpaceId), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Secure Vault", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("All documents and credentials encrypted on-device.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Expiry alert banner
        if (expiringRecords.isNotEmpty() && currentSpace == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("!", fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val first = expiringRecords.first()
                            val daysLeft = (first.expiryEpochDay ?: todayEpoch) - todayEpoch
                            Text("${first.title} Expiring", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Expires in $daysLeft days. Local reminder active.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        if (records.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All (${records.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == RecordType.DOCUMENT || selectedCategory == RecordType.ID,
                            onClick = { selectedCategory = if (selectedCategory == RecordType.DOCUMENT) null else RecordType.DOCUMENT },
                            label = { Text("Documents") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == RecordType.CARD || selectedCategory == RecordType.BANK_ACCOUNT,
                            onClick = { selectedCategory = if (selectedCategory == RecordType.CARD) null else RecordType.CARD },
                            label = { Text("Cards & Banking") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == RecordType.LOGIN,
                            onClick = { selectedCategory = if (selectedCategory == RecordType.LOGIN) null else RecordType.LOGIN },
                            label = { Text("Inert Logins") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == RecordType.NOTE,
                            onClick = { selectedCategory = if (selectedCategory == RecordType.NOTE) null else RecordType.NOTE },
                            label = { Text("Notes") }
                        )
                    }
                }
            }
        }

        // Child Spaces
        items(childSpaces) { space ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { vm.openSpace(space.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(space.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "${data.records.count { it.spaceId == space.id }}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Records list
        items(filteredRecords) { record ->
            RecordCard(record) { selectedRecord = record }
        }

        // TRUE EMPTY STATE: Zero spaces, zero records
        if (childSpaces.isEmpty() && records.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Your Vault is Empty", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Zero pre-seeded folders, zero cloud tracking. Everything saved here is encrypted directly into your phone hardware.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onOpenAddSheet,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan First Document", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenAddSheet,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Add Card, Login or Space")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DonateFooter(isMoreScreen = false, onDonate = { donate(context) })
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(vm, record, donate, context) { selectedRecord = null }
    }
}

@Composable
fun RecordCard(record: VaultRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (record.type) {
                RecordType.ID, RecordType.DOCUMENT -> Icons.Filled.Description
                RecordType.CARD, RecordType.BANK_ACCOUNT -> Icons.Filled.CreditCard
                RecordType.LOGIN -> Icons.Filled.Key
                RecordType.NOTE -> Icons.Filled.Note
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val typeLabel = record.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                val expLabel = record.expiryEpochDay?.let { " • Exp: ${formatEpochDay(it)}" } ?: ""
                Text("$typeLabel$expLabel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (record.fileName != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text("FILE", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val query by vm.searchQuery.collectAsState()
    val results = remember(query) { vm.searchResults(query) }
    var selectedRecord by remember { mutableStateOf<VaultRecord?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setSearchQuery(it) },
                label = { Text("Search OCR titles, fields and text") },
                placeholder = { Text("English / हिन्दी") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Text(
                "Search runs strictly on this phone with local ML Kit models.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(results) { record ->
            RecordCard(record) { selectedRecord = record }
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item {
                Text("No matches found in your encrypted vault.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trash", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Deleted items are kept locally for 30 days, then permanently erased. You can also empty the trash manually anytime.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trash.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedButton(
                        onClick = { vm.emptyTrash() },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Empty Trash Now") }
                }
            }
        }
        items(trash) { entry: TrashEntry ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.record.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Deleted ${formatMillis(entry.deletedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.restoreFromTrash(entry) }) { Text("Restore") }
                    TextButton(onClick = { vm.deleteForever(entry) }) { Text("Delete") }
                }
            }
        }
        if (trash.isEmpty()) {
            item {
                Text("Trash is empty.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { DonateFooter(isMoreScreen = false, onDonate = { donate(context) }) }
    }
}

@Composable
fun MoreScreen(vm: VaultViewModel, donate: (Context) -> Unit, context: Context) {
    val vault by vm.vault.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Security & About", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Offline personal vault architecture", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("DocuBuds v1.0.1", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("Vault Name: ${vault?.displayName ?: ""}")
                    Text(
                        "Strict Offline Contract: No user accounts, zero remote servers, no telemetry, no analytics. All encryption keys are derived on-device with Android Keystore and SQLCipher.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zero Backup Guarantee", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "By design, there is no remote backup or master password. If this device is lost or the app uninstalled, data cannot be recovered. Auto-locks after 60 seconds of inactivity.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
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
        title = { Text(record.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Type: ${record.type.name.lowercase().replace('_', ' ')}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                record.spaceId.let { Text("Space: ${vm.spacePath(it)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (record.expiryEpochDay != null) {
                    Text("Expiry: ${formatEpochDay(record.expiryEpochDay)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                record.fields.forEach { (key, value) ->
                    MaskedFieldRow(key, value)
                }
                record.fileName?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Encrypted File: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(
                        onClick = { showFile = true },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("View Decrypted File") }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.shareRecord(context, record) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move to Trash?") },
            text = { Text("This record will remain in your trash for 30 days before being permanently removed.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteRecord(record); confirmDelete = false; onDismiss() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Move to Trash") }
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
        Text("$key: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            text = if (sensitive && !revealed) "••••••••" else value,
            fontSize = 13.sp,
            fontFamily = if (sensitive) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
            modifier = Modifier.weight(1f)
        )
        if (sensitive) {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide" else "Reveal",
                    modifier = Modifier.size(18.dp)
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
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
