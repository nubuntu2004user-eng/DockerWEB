package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import com.example.dockerwebui.NetworkContainerUsage
import com.example.dockerwebui.NetworkData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface NetworkDialog {
    data class Inspect(val networkName: String, val content: String, val isLoading: Boolean) : NetworkDialog
    data class Connect(val networkId: String, val networkName: String) : NetworkDialog
    data class Disconnect(val networkId: String, val networkName: String, val containers: List<NetworkContainerUsage>) : NetworkDialog
    data class Remove(val networkId: String, val networkName: String, val isSystem: Boolean, val hasContainers: Boolean) : NetworkDialog
}

@Composable
fun networksTable(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    searchQuery: String
) {
    val clipboardManager = LocalClipboardManager.current
    var activeDialog by remember { mutableStateOf<NetworkDialog?>(null) }

    // Connect form state
    var selectedContainerId by remember { mutableStateOf("") }
    var connectIpv4 by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.68f).padding(4.dp)) {
        val filteredNetworks = if (searchQuery.isBlank()) {
            appState.networks
        } else {
            appState.networks.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.driver.contains(searchQuery, ignoreCase = true) ||
                it.subnet.contains(searchQuery, ignoreCase = true) ||
                it.gateway.contains(searchQuery, ignoreCase = true) ||
                it.scope.contains(searchQuery, ignoreCase = true) ||
                it.containers.any { c -> c.name.contains(searchQuery, ignoreCase = true) }
            }
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
                Text("NAME", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.15f))
                Text("DRIVER", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.07f))
                Text("SCOPE", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.07f))
                Text("SUBNET", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.13f))
                Text("GATEWAY", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.13f))
                Text("CONTAINERS", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.17f))
                Text("ACTIONS", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.28f))
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(40, 60, 85)))

            if (filteredNetworks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (appState.networks.isEmpty()) "No Docker networks found." else "No networks match the search filter.",
                        fontFamily = font.font,
                        color = Color(140, 160, 185),
                        fontSize = 16.sp
                    )
                }
            }

            filteredNetworks.forEachIndexed { index, network ->
                val rowBackground = if (index % 2 == 0) Color(22, 37, 56) else Color(27, 45, 68)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NAME Column (with System badge if applicable)
                    Row(
                        modifier = Modifier.weight(0.15f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = network.name,
                            fontFamily = font.font,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (network.isSystem) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(60, 80, 105), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("System", color = Color(200, 220, 240), fontSize = 11.sp, fontFamily = font.font, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    // DRIVER Column
                    Text(
                        text = network.driver,
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.07f).padding(end = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )

                    // SCOPE Column
                    Text(
                        text = network.scope,
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.07f).padding(end = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )

                    // SUBNET Column
                    Text(
                        text = network.formattedSubnet(),
                        fontFamily = font.font,
                        color = Color(180, 200, 220),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.13f).padding(end = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )

                    // GATEWAY Column
                    Text(
                        text = network.formattedGateway(),
                        fontFamily = font.font,
                        color = Color(180, 200, 220),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.13f).padding(end = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )

                    // CONTAINERS Column
                    Row(
                        modifier = Modifier.weight(0.17f).padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (network.containers.isEmpty()) {
                            if (network.isSystem) {
                                Text("None", color = Color(140, 160, 185), fontSize = 13.sp, fontFamily = font.font, maxLines = 1, softWrap = false)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(255, 193, 7, 40), shape = RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("Unused", color = Color(255, 180, 0), fontSize = 12.sp, fontFamily = font.font, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                network.containers.forEach { container ->
                                    val isRunning = container.status.lowercase() == "running"
                                    val badgeBg = if (isRunning) Color(40, 167, 69, 50) else Color(108, 117, 125, 50)
                                    val badgeText = if (isRunning) Color(40, 167, 69) else Color(180, 180, 180)
                                    Box(
                                        modifier = Modifier
                                            .background(badgeBg, shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(container.name, color = badgeText, fontSize = 12.sp, fontFamily = font.font, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }

                    // ACTIONS Column
                    Row(
                        modifier = Modifier.weight(0.28f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val btnPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                        // Connect
                        ElevatedButton(
                            onClick = {
                                selectedContainerId = ""
                                connectIpv4 = ""
                                activeDialog = NetworkDialog.Connect(network.id, network.name)
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(50, 74, 110),
                                contentColor = Color.White
                            ),
                            contentPadding = btnPadding
                        ) {
                            Text("Connect", fontFamily = font.font, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Disconnect
                        ElevatedButton(
                            onClick = {
                                activeDialog = NetworkDialog.Disconnect(network.id, network.name, network.containers)
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(50, 74, 110),
                                contentColor = Color.White
                            ),
                            contentPadding = btnPadding
                        ) {
                            Text("Disconnect", fontFamily = font.font, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Inspect
                        ElevatedButton(
                            onClick = {
                                activeDialog = NetworkDialog.Inspect(network.name, "Loading metadata...", true)
                                scope.launch {
                                    try {
                                        val jsonString = appState.api.inspectNetwork(network.id)
                                        activeDialog = NetworkDialog.Inspect(network.name, jsonString, false)
                                    } catch (e: Exception) {
                                        activeDialog = NetworkDialog.Inspect(network.name, "Error: ${e.message}", false)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(50, 74, 110),
                                contentColor = Color.White
                            ),
                            contentPadding = btnPadding
                        ) {
                            Text("Inspect", fontFamily = font.font, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Remove
                        ElevatedButton(
                            onClick = {
                                activeDialog = NetworkDialog.Remove(
                                    networkId = network.id,
                                    networkName = network.name,
                                    isSystem = network.isSystem,
                                    hasContainers = network.containers.isNotEmpty()
                                )
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(65, 30, 35),
                                contentColor = Color(248, 113, 113)
                            ),
                            contentPadding = btnPadding
                        ) {
                            Text("Remove", fontFamily = font.font, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs
    when (val dialog = activeDialog) {
        is NetworkDialog.Inspect -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Inspect Network: ${dialog.networkName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                        if (!dialog.isLoading) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(dialog.content))
                                scope.launch { appState.toastState.showToast("JSON copied to clipboard") }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = Color.White)
                            }
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color(14, 24, 38), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            Text(
                                text = dialog.content,
                                fontFamily = FontFamily.Monospace,
                                color = Color(200, 220, 240),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", fontFamily = font.font, color = font.fontColor)
                    }
                },
                containerColor = Color(27, 45, 68)
            )
        }

        is NetworkDialog.Connect -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Connect Container to '${dialog.networkName}'", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Select a container or enter container name/ID:",
                            fontFamily = font.font,
                            color = Color(200, 200, 200),
                            fontSize = 13.sp
                        )

                        // Available containers quick picker
                        if (appState.containers.isNotEmpty()) {
                            Text("Quick select from active containers:", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                appState.containers.take(4).forEach { c ->
                                    val cName = c.name ?: c.id ?: "container"
                                    val isSelected = selectedContainerId == (c.id ?: "") || selectedContainerId == (c.name ?: "")
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) Color(31, 111, 235) else Color(40, 60, 85),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedContainerId = cName }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(cName, color = Color.White, fontSize = 12.sp, fontFamily = font.font)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = selectedContainerId,
                            onValueChange = { selectedContainerId = it },
                            label = { Text("Container Name or ID", fontFamily = font.font) },
                            placeholder = { Text("e.g. web-nginx or 4c6f2b...", fontFamily = font.font, color = Color(140, 160, 185)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                focusedContainerColor = Color(14, 24, 38),
                                unfocusedTextColor = Color.White,
                                unfocusedContainerColor = Color(14, 24, 38)
                            )
                        )

                        OutlinedTextField(
                            value = connectIpv4,
                            onValueChange = { connectIpv4 = it },
                            label = { Text("IPv4 Address (optional)", fontFamily = font.font) },
                            placeholder = { Text("e.g. 172.20.0.10", fontFamily = font.font, color = Color(140, 160, 185)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                focusedContainerColor = Color(14, 24, 38),
                                unfocusedTextColor = Color.White,
                                unfocusedContainerColor = Color(14, 24, 38)
                            )
                        )
                    }
                },
                confirmButton = {
                    ElevatedButton(
                        onClick = {
                            val containerToConnect = selectedContainerId.trim()
                            if (containerToConnect.isBlank()) {
                                scope.launch { appState.toastState.showToast("Please provide a container name or ID") }
                                return@ElevatedButton
                            }
                            activeDialog = null
                            scope.launch {
                                try {
                                    appState.api.connectNetwork(
                                        networkId = dialog.networkId,
                                        containerId = containerToConnect,
                                        ipv4 = connectIpv4.trim().ifBlank { null }
                                    )
                                    appState.toastState.showToast("Connected '$containerToConnect' to '${dialog.networkName}'")
                                } catch (e: Exception) {
                                    appState.toastState.showToast("Failed to connect container: ${e.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(31, 111, 235),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Connect", fontFamily = font.font)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel", fontFamily = font.font, color = font.fontColor)
                    }
                },
                containerColor = Color(27, 45, 68)
            )
        }

        is NetworkDialog.Disconnect -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Disconnect from '${dialog.networkName}'", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (dialog.containers.isEmpty()) {
                            Text("No containers are currently attached to this network.", fontFamily = font.font, color = Color(180, 180, 180), fontSize = 14.sp)
                        } else {
                            Text("Select an attached container to disconnect:", fontFamily = font.font, color = Color(200, 200, 200), fontSize = 13.sp)

                            dialog.containers.forEach { container ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(14, 24, 38), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(container.name, color = Color.White, fontSize = 14.sp, fontFamily = font.font, fontWeight = FontWeight.SemiBold)
                                        if (container.ipv4.isNotBlank()) {
                                            Text("IP: ${container.ipv4}", color = Color(140, 160, 185), fontSize = 12.sp, fontFamily = font.font)
                                        }
                                    }

                                    ElevatedButton(
                                        onClick = {
                                            activeDialog = null
                                            scope.launch {
                                                try {
                                                    appState.api.disconnectNetwork(
                                                        networkId = dialog.networkId,
                                                        containerId = container.id.ifBlank { container.name }
                                                    )
                                                    appState.toastState.showToast("Disconnected '${container.name}' from '${dialog.networkName}'")
                                                } catch (e: Exception) {
                                                    appState.toastState.showToast("Failed to disconnect: ${e.message}")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(220, 53, 69),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Disconnect", fontFamily = font.font, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close", fontFamily = font.font, color = font.fontColor)
                    }
                },
                containerColor = Color(27, 45, 68)
            )
        }

        is NetworkDialog.Remove -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text("Remove Network '${dialog.networkName}'", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dialog.isSystem) {
                            Text(
                                "Warning: '${dialog.networkName}' is a predefined Docker system network. Docker Engine typically prohibits removing default system networks.",
                                fontFamily = font.font,
                                color = Color(255, 180, 0),
                                fontSize = 14.sp
                            )
                        } else if (dialog.hasContainers) {
                            Text(
                                "Warning: This network currently has attached containers. Please disconnect all containers before attempting to remove the network.",
                                fontFamily = font.font,
                                color = Color(255, 180, 0),
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                "Are you sure you want to remove network '${dialog.networkName}'? This action cannot be undone.",
                                fontFamily = font.font,
                                color = Color(200, 200, 200),
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    ElevatedButton(
                        onClick = {
                            activeDialog = null
                            scope.launch {
                                try {
                                    appState.api.removeNetwork(dialog.networkId)
                                    appState.toastState.showToast("Network '${dialog.networkName}' removed successfully")
                                } catch (e: Exception) {
                                    appState.toastState.showToast("Failed to remove network: ${e.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(220, 53, 69),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Remove", fontFamily = font.font)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel", fontFamily = font.font, color = font.fontColor)
                    }
                },
                containerColor = Color(27, 45, 68)
            )
        }

        null -> {}
    }
}
