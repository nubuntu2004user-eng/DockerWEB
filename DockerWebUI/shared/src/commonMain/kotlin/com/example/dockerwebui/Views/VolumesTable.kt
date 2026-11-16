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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.example.dockerwebui.VolumeBrowseResponse
import com.example.dockerwebui.VolumeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface VolumeDialog {
    data class Inspect(val volumeName: String, val content: String, val isLoading: Boolean) : VolumeDialog
    data class Browse(val volumeName: String, val currentPath: String, val data: VolumeBrowseResponse?, val isLoading: Boolean) : VolumeDialog
    data class Clone(val sourceVolumeName: String) : VolumeDialog
    data class Backup(val volumeName: String) : VolumeDialog
    data class Remove(val volumeName: String, val isUsed: Boolean) : VolumeDialog
}

@Composable
fun volumesTable(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    searchQuery: String
) {
    val clipboardManager = LocalClipboardManager.current
    var activeDialog by remember { mutableStateOf<VolumeDialog?>(null) }

    // Clone dialog form state
    var cloneTargetName by remember { mutableStateOf("") }

    // Backup dialog form state
    var backupDestDir by remember { mutableStateOf("") }

    // Remove dialog state
    var forceRemove by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.68f).padding(4.dp)) {
        val filteredVolumes = if (searchQuery.isBlank()) {
            appState.volumes
        } else {
            appState.volumes.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.driver.contains(searchQuery, ignoreCase = true) ||
                it.mountpoint.contains(searchQuery, ignoreCase = true) ||
                it.labels.any { (k, v) -> k.contains(searchQuery, ignoreCase = true) || v.contains(searchQuery, ignoreCase = true) }
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
                Text("NAME", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.16f))
                Text("DRIVER", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.07f))
                Text("MOUNTPOINT", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.24f))
                Text("SIZE", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.07f))
                Text("USED BY", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.13f))
                Text("CREATED", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.11f))
                Text("ACTIONS", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.22f))
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(40, 60, 85)))

            if (filteredVolumes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (appState.volumes.isEmpty()) "No Docker volumes found." else "No volumes match the search filter.",
                        fontFamily = font.font,
                        color = Color(140, 160, 185),
                        fontSize = 16.sp
                    )
                }
            }

            filteredVolumes.forEachIndexed { index, volume ->
                val rowBackground = if (index % 2 == 0) Color(22, 37, 56) else Color(27, 45, 68)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NAME Column
                    Column(modifier = Modifier.weight(0.16f).padding(end = 8.dp)) {
                        Text(
                            text = volume.displayName(),
                            fontFamily = font.font,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (volume.anonymous) {
                            Text(
                                text = "(anonymous)",
                                fontFamily = font.font,
                                color = Color(140, 160, 185),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // DRIVER Column
                    Text(
                        text = volume.driver,
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.07f).padding(end = 8.dp)
                    )

                    // MOUNTPOINT Column
                    Text(
                        text = volume.mountpoint.ifBlank { "—" },
                        fontFamily = font.font,
                        color = Color(180, 200, 220),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.24f).padding(end = 8.dp)
                    )

                    // SIZE Column
                    Text(
                        text = volume.formattedSize(),
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.07f).padding(end = 8.dp)
                    )

                    // USED BY Column
                    Column(
                        modifier = Modifier.weight(0.13f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (volume.usedBy.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(Color(255, 193, 7, 40), shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Unused", color = Color(255, 180, 0), fontSize = 12.sp, fontFamily = font.font, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            volume.usedBy.forEach { container ->
                                val isRunning = container.status.lowercase() == "running"
                                val badgeBg = if (isRunning) Color(40, 167, 69, 50) else Color(108, 117, 125, 50)
                                val badgeText = if (isRunning) Color(40, 167, 69) else Color(180, 180, 180)
                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, shape = RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(container.name, color = badgeText, fontSize = 12.sp, fontFamily = font.font, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // CREATED Column
                    Text(
                        text = volume.formattedCreated(),
                        fontFamily = font.font,
                        color = Color(160, 180, 200),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.11f).padding(end = 8.dp)
                    )

                    // ACTIONS Column
                    Column(
                        modifier = Modifier.weight(0.22f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Top row of action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Browse
                            ElevatedButton(
                                onClick = {
                                    activeDialog = VolumeDialog.Browse(volume.name, "/", null, true)
                                    scope.launch {
                                        try {
                                            val browseData = appState.api.browseVolume(volume.name, "/")
                                            activeDialog = VolumeDialog.Browse(volume.name, "/", browseData, false)
                                        } catch (e: Exception) {
                                            activeDialog = VolumeDialog.Browse(volume.name, "/", VolumeBrowseResponse(error = e.message), false)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(50, 74, 110), contentColor = Color.White),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Browse", fontSize = 11.sp, fontFamily = font.font)
                            }

                            // Backup
                            ElevatedButton(
                                onClick = {
                                    backupDestDir = ""
                                    activeDialog = VolumeDialog.Backup(volume.name)
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(50, 74, 110), contentColor = Color.White),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Backup", fontSize = 11.sp, fontFamily = font.font)
                            }

                            // Clone
                            ElevatedButton(
                                onClick = {
                                    cloneTargetName = "${volume.name}-clone"
                                    activeDialog = VolumeDialog.Clone(volume.name)
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(50, 74, 110), contentColor = Color.White),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clone", fontSize = 11.sp, fontFamily = font.font)
                            }

                            // Inspect
                            ElevatedButton(
                                onClick = {
                                    activeDialog = VolumeDialog.Inspect(volume.name, "", true)
                                    scope.launch {
                                        try {
                                            val jsonString = appState.api.inspectVolume(volume.name)
                                            activeDialog = VolumeDialog.Inspect(volume.name, jsonString, false)
                                        } catch (e: Exception) {
                                            activeDialog = VolumeDialog.Inspect(volume.name, "Error: ${e.message}", false)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(50, 74, 110), contentColor = Color.White),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Inspect", fontSize = 11.sp, fontFamily = font.font)
                            }
                        }

                        // Bottom row of action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Remove
                            ElevatedButton(
                                onClick = {
                                    forceRemove = false
                                    activeDialog = VolumeDialog.Remove(volume.name, volume.isUsed())
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(75, 30, 35), contentColor = Color(255, 120, 120)),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Remove", fontSize = 11.sp, fontFamily = font.font, color = Color(255, 120, 120), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(35, 52, 75)))
            }
        }
    }

    // Inspect Modal Dialog
    (activeDialog as? VolumeDialog.Inspect)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inspect Volume: ${dialog.volumeName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(dialog.content))
                            scope.launch {
                                appState.toastState.showToast("Volume inspect JSON copied to clipboard")
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = Color.White)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .background(Color(14, 24, 38), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (dialog.isLoading) {
                        Text("Loading metadata...", fontFamily = font.font, color = Color(140, 160, 185))
                    } else {
                        SelectionContainer {
                            Text(
                                text = dialog.content,
                                fontFamily = FontFamily.Monospace,
                                color = Color(160, 220, 160),
                                fontSize = 12.sp
                            )
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

    // Browse Modal Dialog
    (activeDialog as? VolumeDialog.Browse)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browse Volume: ${dialog.volumeName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                    Text("Path: ${dialog.currentPath}", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color(14, 24, 38), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (dialog.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading files...", fontFamily = font.font, color = Color(140, 160, 185))
                        }
                    } else if (dialog.data?.entries.isNullOrEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (dialog.data?.error != null) "Error reading volume: ${dialog.data.error}" else "Directory is empty or no files found.",
                                fontFamily = font.font,
                                color = if (dialog.data?.error != null) Color(255, 120, 120) else Color(140, 160, 185),
                                fontSize = 13.sp
                            )
                            if (!dialog.data?.raw.isNullOrBlank()) {
                                Text("Raw output:", fontFamily = font.font, color = Color.White, fontSize = 12.sp)
                                SelectionContainer {
                                    Text(
                                        text = dialog.data?.raw ?: "",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(180, 200, 220),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Table of files
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text("NAME", color = Color(140, 160, 185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.4f))
                                Text("TYPE", color = Color(140, 160, 185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.15f))
                                Text("SIZE", color = Color(140, 160, 185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.15f))
                                Text("PERMS", color = Color(140, 160, 185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.15f))
                                Text("MODIFIED", color = Color(140, 160, 185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.15f))
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(40, 60, 85)))

                            dialog.data?.entries?.forEach { entry ->
                                val isDir = entry.type == "dir"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable(enabled = isDir) {
                                            val nextPath = if (dialog.currentPath == "/") "/${entry.name}" else "${dialog.currentPath}/${entry.name}"
                                            activeDialog = VolumeDialog.Browse(dialog.volumeName, nextPath, null, true)
                                            scope.launch {
                                                try {
                                                    val nextData = appState.api.browseVolume(dialog.volumeName, nextPath)
                                                    activeDialog = VolumeDialog.Browse(dialog.volumeName, nextPath, nextData, false)
                                                } catch (e: Exception) {
                                                    activeDialog = VolumeDialog.Browse(dialog.volumeName, nextPath, VolumeBrowseResponse(error = e.message), false)
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (if (isDir) "📁 " else "📄 ") + entry.name,
                                        color = if (isDir) Color(100, 180, 255) else Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(0.4f)
                                    )
                                    Text(entry.type, color = Color(160, 180, 200), fontSize = 12.sp, modifier = Modifier.weight(0.15f))
                                    Text(entry.size, color = Color(160, 180, 200), fontSize = 12.sp, modifier = Modifier.weight(0.15f))
                                    Text(entry.permissions, fontFamily = FontFamily.Monospace, color = Color(160, 180, 200), fontSize = 11.sp, modifier = Modifier.weight(0.15f))
                                    Text(entry.modified, color = Color(160, 180, 200), fontSize = 11.sp, modifier = Modifier.weight(0.15f))
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

    // Clone Modal Dialog
    (activeDialog as? VolumeDialog.Clone)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Clone Volume: ${dialog.sourceVolumeName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Specify a name for the new cloned volume. All files will be copied from '${dialog.sourceVolumeName}'.",
                        fontFamily = font.font,
                        color = Color(200, 200, 200),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = cloneTargetName,
                        onValueChange = { cloneTargetName = it },
                        label = { Text("Target Volume Name", color = Color(140, 160, 185)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
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
                        val target = cloneTargetName.trim()
                        if (target.isBlank()) {
                            scope.launch { appState.toastState.showToast("Target volume name cannot be empty") }
                            return@ElevatedButton
                        }
                        activeDialog = null
                        scope.launch {
                            try {
                                appState.api.cloneVolume(dialog.sourceVolumeName, target)
                                appState.toastState.showToast("Volume cloned successfully to '$target'")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to clone volume: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(31, 111, 235),
                        contentColor = Color.White
                    )
                ) {
                    Text("Clone Volume", fontFamily = font.font)
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

    // Backup Modal Dialog
    (activeDialog as? VolumeDialog.Backup)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Backup Volume: ${dialog.volumeName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Create a compressed tar archive (.tar.gz) backup of '${dialog.volumeName}'.",
                        fontFamily = font.font,
                        color = Color(200, 200, 200),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = backupDestDir,
                        onValueChange = { backupDestDir = it },
                        placeholder = { Text("Destination directory (optional)", color = Color(140, 160, 185)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
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
                        activeDialog = null
                        scope.launch {
                            try {
                                val res = appState.api.backupVolume(dialog.volumeName, backupDestDir.trim().ifBlank { null })
                                appState.toastState.showToast("Backup created successfully for '${dialog.volumeName}'")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to backup volume: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(31, 111, 235),
                        contentColor = Color.White
                    )
                ) {
                    Text("Create Backup", fontFamily = font.font)
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

    // Remove Confirmation Dialog
    (activeDialog as? VolumeDialog.Remove)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Remove Volume: ${dialog.volumeName}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dialog.isUsed) {
                        Text(
                            "Warning: This volume is currently in use by one or more containers. Force remove may be required.",
                            fontFamily = font.font,
                            color = Color(255, 180, 0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Are you sure you want to delete volume '${dialog.volumeName}'? All persisted data will be lost.",
                            fontFamily = font.font,
                            color = Color(200, 200, 200),
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = forceRemove,
                            onCheckedChange = { forceRemove = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(220, 53, 69),
                                uncheckedColor = Color(140, 160, 185)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Force remove (even if volume is attached/in-use)",
                            fontFamily = font.font,
                            color = Color.White,
                            fontSize = 13.sp
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
                                appState.api.removeVolume(dialog.volumeName, force = forceRemove)
                                appState.toastState.showToast("Volume '${dialog.volumeName}' removed successfully")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to remove volume: ${e.message}")
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
}
