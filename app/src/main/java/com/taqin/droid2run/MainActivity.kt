package com.taqin.droid2run

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.taqin.droid2run.ui.CreateVMDialog
import com.taqin.droid2run.ui.theme.AndroidInAndroidTheme
import com.taqin.droid2run.vm.VMConfig
import com.taqin.droid2run.vm.VMState
import com.taqin.droid2run.vm.VirtualMachineManager
import com.taqin.droid2run.vnc.VncActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Catppuccin Mocha colors
val HeaderBg = Color(0xFF1e1e2e)
val DarkBg = Color(0xFF11111b)
val CardBg = Color(0xFF1e1e2e)
val CardBorder = Color(0xFF313244)
val AccentGreen = Color(0xFFa6e3a1)
val AccentRed = Color(0xFFf38ba8)
val AccentBlue = Color(0xFF89b4fa)
val AccentYellow = Color(0xFFf9e2af)
val AccentTeal = Color(0xFF94e2d5)
val TextMuted = Color(0xFF6c7086)
val TextLight = Color(0xFFcdd6f4)

class MainActivity : ComponentActivity() {

    private lateinit var vmManager: VirtualMachineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vmManager = VirtualMachineManager(this)
        enableEdgeToEdge()
        setContent {
            AndroidInAndroidTheme {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        var qemuInstalled by remember { mutableStateOf(vmManager.isQemuInstalled) }
        var isInstalling by remember { mutableStateOf(false) }
        var installProgress by remember { mutableStateOf(0) }
        var installStatus by remember { mutableStateOf("") }
        var vmList by remember { mutableStateOf(vmManager.getVMList()) }
        var showCreateDialog by remember { mutableStateOf(false) }
        var editingVM by remember { mutableStateOf<VMConfig?>(null) }
        var logs by remember { mutableStateOf(listOf<String>()) }
        val logListState = rememberLazyListState()

        fun addLog(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logs = (logs + "[$time] $msg").takeLast(100)
        }

        LaunchedEffect(Unit) {
            if (!vmManager.isQemuInstalled) {
                isInstalling = true
                addLog("Initializing QEMU...")
                vmManager.qemuInstaller.installQemu { progress, status ->
                    installProgress = progress
                    installStatus = status
                    if (progress % 20 == 0) addLog(status)
                }.onSuccess {
                    qemuInstalled = true
                    isInstalling = false
                    addLog("✓ QEMU ready")
                }.onFailure { e ->
                    isInstalling = false
                    addLog("✗ Install failed: ${e.message}")
                }
            } else {
                addLog("✓ QEMU ready")
            }
        }

        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) logListState.animateScrollToItem(logs.size - 1)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HeaderBg)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Droid2Run",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            )
                            Text(
                                if (isInstalling) "Setting up..." else "${vmList.size} virtual machines",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        if (qemuInstalled) {
                            FilledIconButton(
                                onClick = { showCreateDialog = true },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AccentBlue
                                )
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = DarkBg)
                            }
                        }
                    }
                }

                // Install progress
                AnimatedVisibility(visible = isInstalling) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentBlue
                            )
                            Text(installStatus, fontSize = 12.sp, color = TextMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { installProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentBlue,
                            trackColor = CardBorder
                        )
                    }
                }

                // VM List
                if (vmList.isEmpty() && qemuInstalled && !isInstalling) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Memory,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = TextMuted
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No virtual machines", color = TextMuted, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentBlue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = DarkBg)
                                Spacer(Modifier.width(8.dp))
                                Text("Create VM", color = DarkBg)
                            }
                        }
                    }
                } else if (vmList.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vmList, key = { it.id }) { vm ->
                            ModernVMCard(
                                vm = vm,
                                state = vmManager.getVMState(vm.id),
                                onStart = {
                                    addLog("▶ Starting ${vm.name}...")
                                    lifecycleScope.launch {
                                        vmManager.startVM(vm).onSuccess { port ->
                                            addLog("✓ ${vm.name} running")
                                            startActivity(Intent(this@MainActivity, VncActivity::class.java).apply {
                                                putExtra(VncActivity.EXTRA_HOST, "127.0.0.1")
                                                putExtra(VncActivity.EXTRA_PORT, port)
                                            })
                                        }.onFailure { e ->
                                            addLog("✗ Failed: ${e.message}")
                                        }
                                    }
                                },
                                onStop = {
                                    vmManager.stopVM(vm.id)
                                    vmList = vmManager.getVMList()
                                    addLog("■ ${vm.name} stopped")
                                },
                                onConnect = {
                                    startActivity(Intent(this@MainActivity, VncActivity::class.java).apply {
                                        putExtra(VncActivity.EXTRA_HOST, "127.0.0.1")
                                        putExtra(VncActivity.EXTRA_PORT, vm.vncPort)
                                    })
                                },
                                onEdit = { editingVM = vm },
                                onDelete = {
                                    vmManager.deleteVM(vm.id)
                                    vmList = vmManager.getVMList()
                                    addLog("🗑 ${vm.name} deleted")
                                }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(0.4f))
                }

                // Logs section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Terminal,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentBlue
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Console", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        IconButton(onClick = { logs = emptyList() }, Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.ClearAll, null, Modifier.size(16.dp), tint = TextMuted)
                        }
                    }

                    HorizontalDivider(color = CardBorder, thickness = 1.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        state = logListState,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                log,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when {
                                    log.contains("✗") || log.contains("error", true) || log.contains("fail", true) -> AccentRed
                                    log.contains("✓") || log.contains("ready", true) -> AccentGreen
                                    log.contains("▶") || log.contains("start", true) -> AccentBlue
                                    log.contains("■") -> AccentYellow
                                    else -> TextMuted
                                },
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
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
                    vmManager.saveConfig(config)
                    lifecycleScope.launch { vmManager.createDiskImage(config.id, config.diskSize) }
                    vmList = vmManager.getVMList()
                    showCreateDialog = false
                    addLog("+ Created: ${config.name}")
                }
            )
        }

        editingVM?.let { vm ->
            CreateVMDialog(
                onDismiss = { editingVM = null },
                onCreate = { config ->
                    vmManager.saveConfig(config)
                    vmList = vmManager.getVMList()
                    editingVM = null
                    addLog("~ Updated: ${config.name}")
                },
                initialConfig = vm
            )
        }
    }

    @Composable
    fun ModernVMCard(
        vm: VMConfig,
        state: VMState,
        onStart: () -> Unit,
        onStop: () -> Unit,
        onConnect: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        var showDelete by remember { mutableStateOf(false) }
        val isRunning = state == VMState.RUNNING

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    if (isRunning) listOf(AccentGreen.copy(0.5f), AccentGreen.copy(0.2f))
                    else listOf(CardBorder, CardBorder)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isRunning) AccentGreen.copy(0.15f)
                            else CardBorder.copy(0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRunning) Icons.Rounded.PlayArrow else Icons.Rounded.Memory,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isRunning) AccentGreen else TextMuted
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vm.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(vm.osType.displayName, AccentBlue)
                        StatusChip("${vm.memory}MB", TextMuted)
                        if (isRunning) StatusChip("VNC:${vm.vncPort}", AccentGreen)
                    }
                }

                // Actions - grouped together
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBorder.copy(0.5f)),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isRunning) {
                        IconButton(onClick = onConnect, Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Visibility, null, Modifier.size(18.dp), tint = AccentBlue)
                        }
                        IconButton(onClick = onStop, Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp), tint = AccentRed)
                        }
                    } else {
                        IconButton(onClick = onStart, Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = AccentGreen)
                        }
                        IconButton(onClick = onEdit, Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = AccentTeal)
                        }
                    }
                    IconButton(onClick = { showDelete = true }, Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = TextMuted)
                    }
                }
            }
        }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                containerColor = CardBg,
                title = { Text("Delete ${vm.name}?", color = Color.White) },
                text = { Text("This action cannot be undone.", color = TextMuted) },
                confirmButton = {
                    TextButton(onClick = { onDelete(); showDelete = false }) {
                        Text("Delete", color = AccentRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }

    @Composable
    fun StatusChip(text: String, color: Color) {
        Text(
            text,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier
                .background(color.copy(0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    @Composable
    fun SmallIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(0.1f))
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = tint)
        }
    }
}
