package com.taqin.droid2run.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.taqin.droid2run.vm.OSType
import com.taqin.droid2run.vm.VMArch
import com.taqin.droid2run.vm.VMConfig
import com.taqin.droid2run.vm.VMState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VMManagerScreen(
    vms: List<VMConfig>,
    qemuInstalled: Boolean = false,
    getVMState: (String) -> VMState,
    onCreateVM: (VMConfig) -> Unit,
    onStartVM: (VMConfig) -> Unit,
    onStopVM: (String) -> Unit,
    onDeleteVM: (String) -> Unit,
    onConnectVNC: (Int) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showQemuHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Virtual Machines") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQemuHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create VM")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create VM")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // QEMU status notice
            if (!qemuInstalled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { showQemuHelp = true },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "QEMU Not Installed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                "Tap for installation instructions",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            if (vms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "No Virtual Machines",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Create a VM to get started",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create VM")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vms) { vm ->
                        VMCard(
                            vm = vm,
                            state = getVMState(vm.id),
                            onStart = { onStartVM(vm) },
                            onStop = { onStopVM(vm.id) },
                            onDelete = { onDeleteVM(vm.id) },
                            onConnect = { onConnectVNC(vm.vncPort) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateVMDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { config ->
                onCreateVM(config)
                showCreateDialog = false
            }
        )
    }

    if (showQemuHelp) {
        AlertDialog(
            onDismissRequest = { showQemuHelp = false },
            title = { Text("QEMU Installation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("To run VMs, you need QEMU installed:")

                    Text("Option 1: Via Termux", fontWeight = FontWeight.Bold)
                    Text(
                        "1. Install Termux from F-Droid\n" +
                        "2. Run: pkg install qemu-system-x86_64-headless\n" +
                        "3. QEMU will be available to this app",
                        fontSize = 13.sp
                    )

                    Text("Option 2: Use Limbo Emulator", fontWeight = FontWeight.Bold)
                    Text(
                        "Download 'Limbo PC Emulator' from F-Droid for a standalone VM solution.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQemuHelp = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun VMCard(
    vm: VMConfig,
    state: VMState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = vm.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = vm.arch.displayName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StateChip(state)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip("${vm.memory}MB RAM")
                InfoChip("${vm.cpus} CPU")
                InfoChip("VNC:${vm.vncPort}")
            }

            // Show ISO/disk info if configured
            if (vm.cdrom != null || vm.diskImage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vm.cdrom?.let {
                        val fileName = it.substringAfterLast("/").take(20)
                        InfoChip("ISO: $fileName")
                    }
                    vm.diskImage?.let {
                        InfoChip("Disk: ${vm.diskSize}MB")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == VMState.RUNNING) {
                    TextButton(onClick = onConnect) {
                        Icon(Icons.Default.Screenshot, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Connect")
                    }
                    TextButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    TextButton(onClick = onStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                }

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete VM?") },
            text = { Text("Are you sure you want to delete ${vm.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StateChip(state: VMState) {
    val (color, text) = when (state) {
        VMState.RUNNING -> Color(0xFF2E7D32) to "Running"
        VMState.STARTING -> Color(0xFFF57C00) to "Starting"
        VMState.STOPPING -> Color(0xFFF57C00) to "Stopping"
        VMState.ERROR -> Color(0xFFC62828) to "Error"
        VMState.STOPPED -> Color(0xFF757575) to "Stopped"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateVMDialog(
    onDismiss: () -> Unit,
    onCreate: (VMConfig) -> Unit,
    initialConfig: VMConfig? = null
) {
    val context = LocalContext.current
    val isEditing = initialConfig != null
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var selectedArch by remember { mutableStateOf(initialConfig?.arch ?: VMArch.X86_64) }
    var selectedOS by remember { mutableStateOf(initialConfig?.osType ?: OSType.OTHER) }
    var memory by remember { mutableStateOf(initialConfig?.memory?.toString() ?: "512") }
    var cpus by remember { mutableStateOf(initialConfig?.cpus?.toString() ?: "2") }
    var diskSize by remember { mutableStateOf(initialConfig?.diskSize?.toString() ?: "4096") }
    var isoPath by remember { mutableStateOf<String?>(initialConfig?.cdrom) }
    var isoFileName by remember { mutableStateOf<String?>(initialConfig?.cdrom?.substringAfterLast("/")) }
    var bootOrder by remember { mutableStateOf(initialConfig?.bootOrder ?: "cd") }
    var enableKvm by remember { mutableStateOf(initialConfig?.enableKvm ?: false) }
    var vncPort by remember { mutableStateOf(initialConfig?.vncPort?.toString() ?: "5900") }

    // Update defaults when OS type changes (only for new VMs)
    LaunchedEffect(selectedOS) {
        if (!isEditing) {
            memory = selectedOS.recommendedMemory.toString()
            diskSize = selectedOS.minDisk.toString()
        }
        // Always use x86_64 - it can run 32-bit OS and we only have x86_64 QEMU installed
        selectedArch = VMArch.X86_64
    }

    // ISO file picker launcher
    val isoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Get persistent permission
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            isoPath = it.toString()
            isoFileName = it.lastPathSegment?.substringAfterLast("/") ?: "ISO selected"
        }
    }

    var showAdvanced by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (isEditing) "Edit VM" else "Create VM", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                // VM Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // OS Type - single row with wrap
                Text("OS Type", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OSType.values().forEach { os ->
                        FilterChip(
                            selected = selectedOS == os,
                            onClick = { selectedOS = os },
                            label = { Text(os.displayName, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Hardware row - RAM, CPU, Disk in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = memory,
                        onValueChange = { memory = it.filter { c -> c.isDigit() } },
                        label = { Text("RAM MB") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cpus,
                        onValueChange = { cpus = it.filter { c -> c.isDigit() } },
                        label = { Text("CPU") },
                        modifier = Modifier.weight(0.6f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = diskSize,
                        onValueChange = { diskSize = it.filter { c -> c.isDigit() } },
                        label = { Text("Disk MB") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                // ISO File picker - compact
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isoPickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Album, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isoFileName != null) isoFileName!! else "Select ISO (optional)",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        if (isoPath != null) {
                            IconButton(onClick = { isoPath = null; isoFileName = null }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Boot order - compact
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Boot:", fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    listOf("cd" to "CD", "dc" to "HDD", "d" to "CD Only").forEach { (value, label) ->
                        FilterChip(
                            selected = bootOrder == value,
                            onClick = { bootOrder = value },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.padding(end = 4.dp).height(28.dp)
                        )
                    }
                }

                // Advanced toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(16.dp)
                    )
                }

                if (showAdvanced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = vncPort,
                            onValueChange = { vncPort = it.filter { c -> c.isDigit() } },
                            label = { Text("VNC Port") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = enableKvm, onCheckedChange = { enableKvm = it }, Modifier.size(24.dp))
                            Text("KVM", fontSize = 12.sp)
                        }
                        VMArch.values().take(2).forEach { arch ->
                            FilterChip(
                                selected = selectedArch == arch,
                                onClick = { selectedArch = arch },
                                label = { Text(if (arch == VMArch.X86_64) "64" else "32", fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(
                                    VMConfig(
                                        id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
                                        name = name,
                                        arch = selectedArch,
                                        osType = selectedOS,
                                        memory = memory.toIntOrNull() ?: selectedOS.recommendedMemory,
                                        cpus = cpus.toIntOrNull() ?: 2,
                                        diskSize = diskSize.toIntOrNull() ?: selectedOS.minDisk,
                                        cdrom = isoPath,
                                        bootOrder = bootOrder,
                                        enableKvm = enableKvm,
                                        vncPort = vncPort.toIntOrNull() ?: 5900
                                    )
                                )
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Icon(if (isEditing) Icons.Default.Save else Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEditing) "Save" else "Create")
                    }
                }
            }
        }
    }
}
