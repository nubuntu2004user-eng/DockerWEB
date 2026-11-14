package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.ContainerStatus
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface ActiveDialog {
    data class Logs(val containerName: String, val id: String, val content: String, val isLoading: Boolean) : ActiveDialog
    data class Inspect(val containerName: String, val id: String, val content: String, val isLoading: Boolean) : ActiveDialog
    data class Shell(val containerName: String, val id: String, val output: String, val isLoading: Boolean) : ActiveDialog
    data object RunContainer : ActiveDialog
}

@Composable
fun rightPanelContainers(withToFill: Float, font: Font, appState: AppState, scope: CoroutineScope) {
    val clipboardManager = LocalClipboardManager.current
    val secondaryColor = Color(50, 74, 110)
    var userInputSearch by remember { mutableStateOf("") }

    var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }
    var shellCommand by remember { mutableStateOf("") }
    var newImageName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth(withToFill).background(Color(27, 45, 68)).padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.07f).padding(4.dp)) {

            Text("Containers", fontFamily = font.font, color = font.fontColor, fontSize = 32.sp)

            Spacer(modifier = Modifier.fillMaxWidth(0.3f))

            OutlinedTextField(
                value = userInputSearch,
                onValueChange = { userInputSearch = it },
                placeholder = { Text("Search by name", fontFamily = font.font, color = font.fontColor) },
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(20),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(27, 45, 68),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(27, 45, 68)
                )
            )

            Spacer(modifier = Modifier.fillMaxWidth(0.05f))

            ElevatedButton(
                onClick = {
                    scope.launch {
                        try {
                            appState.toastState.showToast("Pruning stopped containers...")
                            appState.api.pruneStopped()
                            appState.toastState.showToast("Pruned stopped containers")
                        } catch (e: Exception) {
                            appState.toastState.showToast("Error: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryColor,
                    contentColor = Color.White
                )
            ) {
                Text("Prune stopped", fontFamily = font.font, color = font.fontColor)
            }

            Spacer(modifier = Modifier.fillMaxWidth(0.05f))

            ElevatedButton(
                onClick = {
                    newImageName = ""
                    activeDialog = ActiveDialog.RunContainer
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(31, 111, 235),
                    contentColor = Color.White
                )
            ) {
                Text("+ Run new container", fontFamily = font.font, color = font.fontColor)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.25f).padding(4.dp)) {
            val runningContainers = appState.containers.filter { it.containerStatus() == ContainerStatus.RUNNING }

            ElevatedCard(
                modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = secondaryColor)
            ) {
                val runningCount = runningContainers.size
                Text("Running\n$runningCount", color = font.fontColor, fontFamily = font.font, fontSize = 48.sp, modifier = Modifier.padding(8.dp))
            }

            ElevatedCard(
                modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = secondaryColor)
            ) {
                val stoppedCount = appState.containers.count { it.containerStatus() == ContainerStatus.STOPPED }
                Text("Stopped\n$stoppedCount", color = font.fontColor, fontFamily = font.font, fontSize = 48.sp, modifier = Modifier.padding(8.dp))
            }

            ElevatedCard(
                modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = secondaryColor)
            ) {
                val totalCpu = runningContainers.sumOf { it.cpuPercentage() }
                val totalCpuRounded = (totalCpu * 10.0).roundToInt() / 10.0
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Host CPU", color = font.fontColor, fontFamily = font.font, fontSize = 20.sp)
                    Text("$totalCpuRounded%", color = font.fontColor, fontFamily = font.font, fontSize = 36.sp)
                }
            }

            ElevatedCard(
                modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = secondaryColor)
            ) {
                val totalUsedBytes = runningContainers.sumOf { it.memoryUsageBytes() }
                val hostLimitBytes = appState.containers.mapNotNull { it.memoryStats?.limit }.maxOrNull() ?: 0L
                val ramPercentage = if (hostLimitBytes > 0L) {
                    ((totalUsedBytes.toDouble() / hostLimitBytes.toDouble()) * 1000.0).roundToInt() / 10.0
                } else 0.0

                fun formatBytes(bytes: Long): String {
                    val mib = bytes.toDouble() / (1024.0 * 1024.0)
                    return if (mib >= 1024.0) {
                        val gib = mib / 1024.0
                        "${(gib * 10.0).roundToInt() / 10.0} GiB"
                    } else {
                        "${(mib * 10.0).roundToInt() / 10.0} MiB"
                    }
                }

                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Host RAM", color = font.fontColor, fontFamily = font.font, fontSize = 20.sp)
                    Text("$ramPercentage%", color = font.fontColor, fontFamily = font.font, fontSize = 36.sp)
                    if (hostLimitBytes > 0L) {
                        Text(
                            "${formatBytes(totalUsedBytes)} / ${formatBytes(hostLimitBytes)}",
                            color = Color.LightGray,
                            fontFamily = font.font,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(4.dp)) {
            val filteredContainers = if (userInputSearch.isBlank()) {
                appState.containers
            } else {
                appState.containers.filter { it.name?.contains(userInputSearch, ignoreCase = true) == true }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Name", fontFamily = font.font, color = font.fontColor, modifier = Modifier.weight(0.14f))
                    Text("Status", fontFamily = font.font, color = font.fontColor, modifier = Modifier.weight(0.09f))
                    Text("OS", fontFamily = font.font, color = font.fontColor, modifier = Modifier.weight(0.06f))
                    Text("%Cpu", fontFamily = font.font, color = font.fontColor, modifier = Modifier.weight(0.08f))
                    Text("RAM", fontFamily = font.font, color = font.fontColor, modifier = Modifier.weight(0.13f))
                    Spacer(modifier = Modifier.weight(0.50f))
                }

                // Container Data Rows
                for (container in filteredContainers) {
                    val status = container.containerStatus()
                    val statusColor = when (status) {
                        ContainerStatus.RUNNING -> Color(40, 167, 69)
                        ContainerStatus.PAUSED -> Color(255, 193, 7)
                        ContainerStatus.STOPPED -> Color(220, 53, 69)
                        else -> font.fontColor
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            container.name?.removePrefix("/") ?: "unknown",
                            fontFamily = font.font,
                            color = font.fontColor,
                            modifier = Modifier.weight(0.14f)
                        )
                        Text(
                            status.name,
                            fontFamily = font.font,
                            color = statusColor,
                            modifier = Modifier.weight(0.09f)
                        )
                        Text(
                            container.osType.toString(),
                            fontFamily = font.font,
                            color = font.fontColor,
                            modifier = Modifier.weight(0.06f)
                        )
                        Text(
                            "${container.cpuPercentage()}%",
                            fontFamily = font.font,
                            color = font.fontColor,
                            modifier = Modifier.weight(0.08f)
                        )
                        Text(
                            "${container.memoryUsageFormatted()} (${container.memoryPercentage()}%)",
                            fontFamily = font.font,
                            color = font.fontColor,
                            modifier = Modifier.weight(0.13f)
                        )

                        // Actions column
                        Row(
                            modifier = Modifier.weight(0.50f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val buttonPadding = 2.dp
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(annotatedString = AnnotatedString(container.id ?: "Error retrieving id try again"))
                                    scope.launch { appState.toastState.showToast(message = "Copied to clipboard", icon = Icons.Filled.ContentCopy) }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Copy ID", fontFamily = font.font, color = font.fontColor)
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    scope.launch {
                                        try {
                                            appState.toastState.showToast("Stopping container...")
                                            appState.api.stopContainer(id)
                                            appState.toastState.showToast("Container stopped")
                                        } catch (e: Exception) {
                                            appState.toastState.showToast("Error: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Stop", fontFamily = font.font, color = font.fontColor)
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    scope.launch {
                                        try {
                                            appState.toastState.showToast("Restarting container...")
                                            appState.api.restartContainer(id)
                                            appState.toastState.showToast("Container restarted")
                                        } catch (e: Exception) {
                                            appState.toastState.showToast("Error: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Restart", fontFamily = font.font, color = font.fontColor)
                            }
                            if (container.containerStatus() == ContainerStatus.PAUSED) {
                                TextButton(
                                    onClick = {
                                        val id = container.id ?: return@TextButton
                                        scope.launch {
                                            try {
                                                appState.api.unpauseContainer(id)
                                                appState.toastState.showToast("Container unpaused")
                                            } catch (e: Exception) {
                                                appState.toastState.showToast("Error: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(buttonPadding)
                                ) {
                                    Text("Unpause", color = Color(255, 193, 7))
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        val id = container.id ?: return@TextButton
                                        scope.launch {
                                            try {
                                                appState.api.pauseContainer(id)
                                                appState.toastState.showToast("Container paused")
                                            } catch (e: Exception) {
                                                appState.toastState.showToast("Error: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(buttonPadding)
                                ) {
                                    Text("Pause", fontFamily = font.font, color = font.fontColor)
                                }
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    val name = container.name ?: id
                                    activeDialog = ActiveDialog.Logs(containerName = name, id = id, content = "", isLoading = true)
                                    scope.launch {
                                        try {
                                            val logs = appState.api.getLogs(id)
                                            activeDialog = ActiveDialog.Logs(containerName = name, id = id, content = logs, isLoading = false)
                                        } catch (e: Exception) {
                                            activeDialog = ActiveDialog.Logs(containerName = name, id = id, content = "Error: ${e.message}", isLoading = false)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Logs", fontFamily = font.font, color = font.fontColor)
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    val name = container.name ?: id
                                    shellCommand = ""
                                    activeDialog = ActiveDialog.Shell(containerName = name, id = id, output = "", isLoading = false)
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Shell", fontFamily = font.font, color = font.fontColor)
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    val name = container.name ?: id
                                    activeDialog = ActiveDialog.Inspect(containerName = name, id = id, content = "", isLoading = true)
                                    scope.launch {
                                        try {
                                            val details = appState.api.inspectContainer(id)
                                            activeDialog = ActiveDialog.Inspect(containerName = name, id = id, content = details, isLoading = false)
                                        } catch (e: Exception) {
                                            activeDialog = ActiveDialog.Inspect(containerName = name, id = id, content = "Error: ${e.message}", isLoading = false)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Inspect", fontFamily = font.font, color = font.fontColor)
                            }
                            TextButton(
                                onClick = {
                                    val id = container.id ?: return@TextButton
                                    scope.launch {
                                        try {
                                            appState.toastState.showToast("Removing container...")
                                            appState.api.removeContainer(id)
                                            appState.toastState.showToast("Container removed")
                                        } catch (e: Exception) {
                                            appState.toastState.showToast("Error: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.padding(buttonPadding)
                            ) {
                                Text("Remove", fontFamily = font.font, color = Color(220, 53, 69))
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Dialogs
    when (val dialog = activeDialog) {
        is ActiveDialog.Logs -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Logs: ${dialog.containerName.removePrefix("/")}", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color(15, 23, 42), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        if (dialog.isLoading) {
                            Text("Loading logs...", fontFamily = font.font, color = Color.Gray, fontSize = 14.sp)
                        } else {
                            Text(
                                if (dialog.content.isBlank()) "(No logs)" else dialog.content,
                                fontFamily = font.font,
                                color = Color(220, 220, 220),
                                fontSize = 12.sp,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", fontFamily = font.font, color = Color.White)
                    }
                },
                containerColor = Color(27, 45, 68),
                shape = RoundedCornerShape(12.dp)
            )
        }
        is ActiveDialog.Inspect -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Inspect: ${dialog.containerName.removePrefix("/")}", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color(15, 23, 42), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        if (dialog.isLoading) {
                            Text("Loading inspect details...", fontFamily = font.font, color = Color.Gray, fontSize = 14.sp)
                        } else {
                            Text(
                                dialog.content,
                                fontFamily = font.font,
                                color = Color(220, 220, 220),
                                fontSize = 12.sp,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", fontFamily = font.font, color = Color.White)
                    }
                },
                containerColor = Color(27, 45, 68),
                shape = RoundedCornerShape(12.dp)
            )
        }
        is ActiveDialog.Shell -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Shell: ${dialog.containerName.removePrefix("/")}", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = shellCommand,
                                onValueChange = { shellCommand = it },
                                placeholder = { Text("Command (e.g. ls -la)", fontFamily = font.font, color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    focusedContainerColor = Color(15, 23, 42),
                                    unfocusedTextColor = Color.White,
                                    unfocusedContainerColor = Color(15, 23, 42)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ElevatedButton(
                                onClick = {
                                    if (shellCommand.isNotBlank()) {
                                        activeDialog = dialog.copy(isLoading = true)
                                        scope.launch {
                                            try {
                                                val out = appState.api.runShell(dialog.id, shellCommand)
                                                activeDialog = dialog.copy(output = out, isLoading = false)
                                            } catch (e: Exception) {
                                                activeDialog = dialog.copy(output = "Error: ${e.message}", isLoading = false)
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(31, 111, 235),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Run", fontFamily = font.font)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(15, 23, 42), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (dialog.isLoading) {
                                Text("Executing command...", fontFamily = font.font, color = Color.Gray, fontSize = 14.sp)
                            } else {
                                Text(
                                    if (dialog.output.isBlank()) "(Enter a command and press Run)" else dialog.output,
                                    fontFamily = font.font,
                                    color = Color(220, 220, 220),
                                    fontSize = 12.sp,
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", fontFamily = font.font, color = Color.White)
                    }
                },
                containerColor = Color(27, 45, 68),
                shape = RoundedCornerShape(12.dp)
            )
        }
        is ActiveDialog.RunContainer -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Run New Container", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Enter Docker image name to run detached:", fontFamily = font.font, color = font.fontColor, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newImageName,
                            onValueChange = { newImageName = it },
                            placeholder = { Text("e.g. nginx:alpine, ubuntu:latest", fontFamily = font.font, color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                focusedContainerColor = Color(15, 23, 42),
                                unfocusedTextColor = Color.White,
                                unfocusedContainerColor = Color(15, 23, 42)
                            )
                        )
                    }
                },
                confirmButton = {
                    ElevatedButton(
                        onClick = {
                            if (newImageName.isNotBlank()) {
                                val img = newImageName
                                activeDialog = null
                                scope.launch {
                                    try {
                                        appState.toastState.showToast("Starting container $img...")
                                        appState.api.addContainer(img)
                                        appState.toastState.showToast("Container $img started successfully")
                                    } catch (e: Exception) {
                                        appState.toastState.showToast("Error: ${e.message}")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(31, 111, 235),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Run", fontFamily = font.font)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel", fontFamily = font.font, color = Color.White)
                    }
                },
                containerColor = Color(27, 45, 68),
                shape = RoundedCornerShape(12.dp)
            )
        }
        null -> {}
    }
}
