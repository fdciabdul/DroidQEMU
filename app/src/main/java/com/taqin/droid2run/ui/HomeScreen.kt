package com.taqin.droid2run.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FeatureCard(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val status: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    linuxInstalled: Boolean,
    onOpenTerminal: () -> Unit,
    onOpenLinux: () -> Unit,
    onInstallLinux: () -> Unit,
    onOpenVMManager: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Android in Android",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "VM & Linux Environment",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Quick Access",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                FeatureCardView(
                    card = FeatureCard(
                        title = "Terminal",
                        description = "Android shell terminal emulator",
                        icon = Icons.Default.Code,
                        color = Color(0xFF2E7D32),
                        onClick = onOpenTerminal
                    )
                )
            }

            item {
                FeatureCardView(
                    card = FeatureCard(
                        title = "Linux Environment",
                        description = if (linuxInstalled) "Alpine Linux ready" else "Install Alpine Linux",
                        icon = Icons.Default.Computer,
                        color = Color(0xFF1565C0),
                        onClick = if (linuxInstalled) onOpenLinux else onInstallLinux,
                        status = if (linuxInstalled) "Installed" else "Not Installed"
                    )
                )
            }

            item {
                FeatureCardView(
                    card = FeatureCard(
                        title = "Virtual Machines",
                        description = "Create and run x86/ARM VMs with QEMU",
                        icon = Icons.Default.Memory,
                        color = Color(0xFF6A1B9A),
                        onClick = onOpenVMManager
                    )
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "System Info",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SystemInfoCard()
            }
        }
    }
}

@Composable
fun FeatureCardView(card: FeatureCard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = card.enabled) { card.onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = card.color,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = card.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                card.status?.let { status ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        color = if (status == "Installed") Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SystemInfoCard() {
    val arch = System.getProperty("os.arch") ?: "unknown"
    val cores = Runtime.getRuntime().availableProcessors()
    val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow("Architecture", arch)
            InfoRow("CPU Cores", cores.toString())
            InfoRow("Max Memory", "${maxMemory}MB")
            InfoRow("Android API", android.os.Build.VERSION.SDK_INT.toString())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
