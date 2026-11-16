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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import com.example.dockerwebui.ImageData
import com.example.dockerwebui.ImageHistoryLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface ImageDialog {
    data class Inspect(val imageRepoTag: String, val content: String, val isLoading: Boolean) : ImageDialog
    data class History(val imageRepoTag: String, val layers: List<ImageHistoryLayer>, val isLoading: Boolean) : ImageDialog
    data class Tag(val imageId: String, val currentRepo: String, val currentTag: String) : ImageDialog
    data class Run(val imageRepoTag: String) : ImageDialog
    data class Remove(val imageId: String, val imageRepoTag: String) : ImageDialog
}

@Composable
fun imagesTable(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    searchQuery: String
) {
    val clipboardManager = LocalClipboardManager.current
    var activeDialog by remember { mutableStateOf<ImageDialog?>(null) }

    // Tag dialog form state
    var newRepoInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    // Run dialog form state
    var runContainerName by remember { mutableStateOf("") }
    var runContainerCmd by remember { mutableStateOf("") }

    // Remove dialog state
    var forceRemove by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f).padding(4.dp)) {
        val filteredImages = if (searchQuery.isBlank()) {
            appState.images
        } else {
            appState.images.filter {
                it.repository.contains(searchQuery, ignoreCase = true) ||
                it.tag.contains(searchQuery, ignoreCase = true) ||
                it.shortId.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
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
                Text("REPOSITORY", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.13f))
                Text("TAG", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.07f))
                Text("IMAGE ID", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.09f))
                Text("SIZE", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.07f))
                Text("CREATED", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.10f))
                Text("USED BY", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.10f))
                Text("ACTIONS", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp, modifier = Modifier.weight(0.44f))
            }

            if (filteredImages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No Docker images found", color = Color.Gray, fontFamily = font.font, fontSize = 16.sp)
                }
            }

            // Image Data Rows
            for (image in filteredImages) {
                val fullRepoTag = if (image.repository == "<none>") image.shortId else "${image.repository}:${image.tag}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // REPOSITORY
                    Text(
                        text = image.repository,
                        fontFamily = font.font,
                        color = if (image.dangling || image.repository == "<none>") Color(255, 153, 0) else font.fontColor,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(0.13f)
                    )

                    // TAG
                    Box(modifier = Modifier.weight(0.07f)) {
                        Box(
                            modifier = Modifier
                                .background(Color(35, 55, 80), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = image.tag,
                                fontFamily = font.font,
                                color = Color(200, 220, 245),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // IMAGE ID
                    Text(
                        text = image.shortId,
                        fontFamily = font.font,
                        color = Color(140, 165, 195),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.09f),
                        maxLines = 1,
                        softWrap = false
                    )

                    // SIZE
                    Text(
                        text = image.formattedSize(),
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(0.07f),
                        maxLines = 1,
                        softWrap = false
                    )

                    // CREATED
                    Text(
                        text = image.formattedCreated(),
                        fontFamily = font.font,
                        color = Color(170, 185, 205),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.10f),
                        maxLines = 1,
                        softWrap = false
                    )

                    // USED BY
                    Box(modifier = Modifier.weight(0.10f)) {
                        when {
                            image.dangling || image.repository == "<none>" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(180, 80, 20), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Dangling", color = Color.White, fontFamily = font.font, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }
                            image.usedBy.isNotEmpty() -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (u in image.usedBy.take(2)) {
                                        val isRunning = u.status.lowercase() == "running"
                                        val bg = if (isRunning) Color(40, 140, 60) else Color(70, 85, 105)
                                        Box(
                                            modifier = Modifier
                                                .background(bg, shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${u.name.removePrefix("/")} (${u.status})",
                                                color = Color.White,
                                                fontFamily = font.font,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    if (image.usedBy.size > 2) {
                                        Text("+${image.usedBy.size - 2}", color = Color.LightGray, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(120, 100, 30), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Unused", color = Color(255, 230, 150), fontFamily = font.font, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    // ACTIONS
                    Row(
                        modifier = Modifier.weight(0.44f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val buttonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)

                        // Copy ID
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(image.id))
                                scope.launch { appState.toastState.showToast(message = "Image ID copied", icon = Icons.Filled.ContentCopy) }
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Copy ID", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Run Button
                        TextButton(
                            onClick = {
                                runContainerName = ""
                                runContainerCmd = ""
                                activeDialog = ImageDialog.Run(fullRepoTag)
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Run", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Pull latest
                        TextButton(
                            onClick = {
                                val target = if (image.repository != "<none>") "${image.repository}:${image.tag}" else image.shortId
                                scope.launch {
                                    try {
                                        appState.toastState.showToast("Pulling $target...")
                                        appState.showPullSection = true
                                        appState.isPullingImage = true
                                        appState.pullStatusText = "Pulling $target..."
                                        appState.pullProgressPercentage = 0f
                                        appState.api.streamPullImage(target) { p ->
                                            if (!p.error.isNullOrBlank()) {
                                                appState.pullStatusText = "Error: ${p.error}"
                                            } else {
                                                appState.pullStatusText = "$target · ${p.status}"
                                            }
                                        }
                                        appState.toastState.showToast("Updated $target")
                                        appState.pullStatusText = "Updated $target"
                                        appState.pullProgressPercentage = 1f
                                    } catch (e: Exception) {
                                        appState.toastState.showToast("Pull error: ${e.message}")
                                    } finally {
                                        appState.isPullingImage = false
                                    }
                                }
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Pull", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Tag button
                        TextButton(
                            onClick = {
                                newRepoInput = if (image.repository != "<none>") image.repository else ""
                                newTagInput = if (image.tag != "<none>") image.tag else "latest"
                                activeDialog = ImageDialog.Tag(image.id, image.repository, image.tag)
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Tag", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // History button
                        TextButton(
                            onClick = {
                                activeDialog = ImageDialog.History(fullRepoTag, emptyList(), true)
                                scope.launch {
                                    try {
                                        val layers = appState.api.getImageHistory(image.id)
                                        activeDialog = ImageDialog.History(fullRepoTag, layers, false)
                                    } catch (e: Exception) {
                                        appState.toastState.showToast("Failed to fetch history: ${e.message}")
                                        activeDialog = null
                                    }
                                }
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("History", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Inspect button
                        TextButton(
                            onClick = {
                                activeDialog = ImageDialog.Inspect(fullRepoTag, "", true)
                                scope.launch {
                                    try {
                                        val metadata = appState.api.inspectImage(image.id)
                                        activeDialog = ImageDialog.Inspect(fullRepoTag, metadata, false)
                                    } catch (e: Exception) {
                                        appState.toastState.showToast("Failed to inspect: ${e.message}")
                                        activeDialog = null
                                    }
                                }
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Inspect", fontFamily = font.font, color = font.fontColor, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }

                        // Remove button
                        TextButton(
                            onClick = {
                                val target = if (image.repository != "<none>") "${image.repository}:${image.tag}" else image.id
                                scope.launch {
                                    try {
                                        appState.toastState.showToast("Removing image $target...")
                                        appState.api.removeImage(target, force = false)
                                        appState.toastState.showToast("Image $target removed")
                                    } catch (e: Exception) {
                                        appState.toastState.showToast("Error: ${e.message}")
                                    }
                                }
                            },
                            contentPadding = buttonPadding
                        ) {
                            Text("Remove", fontFamily = font.font, color = Color(220, 53, 69), fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    // INSPECT DIALOG
    (activeDialog as? ImageDialog.Inspect)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inspect Image: ${dialog.imageRepoTag}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(dialog.content))
                            scope.launch {
                                appState.toastState.showToast("Metadata copied to clipboard")
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = Color.LightGray)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Color(15, 23, 42), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (dialog.isLoading) {
                        Text("Loading metadata...", color = font.fontColor, fontFamily = font.font)
                    } else {
                        SelectionContainer {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = dialog.content,
                                    color = Color(160, 210, 255),
                                    fontFamily = font.font,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
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

    // HISTORY DIALOG
    (activeDialog as? ImageDialog.History)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Layer History: ${dialog.imageRepoTag}", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color(15, 23, 42), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (dialog.isLoading) {
                        Text("Loading layer history...", color = font.fontColor, fontFamily = font.font)
                    } else if (dialog.layers.isEmpty()) {
                        Text("No layer history found", color = Color.Gray, fontFamily = font.font)
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SIZE", color = Color(140, 160, 185), fontSize = 12.sp, modifier = Modifier.weight(0.15f))
                                Text("CREATED BY INSTRUCTION", color = Color(140, 160, 185), fontSize = 12.sp, modifier = Modifier.weight(0.85f))
                            }
                            for (layer in dialog.layers) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = layer.formattedSize(),
                                        color = Color(200, 220, 245),
                                        fontFamily = font.font,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(0.15f)
                                    )
                                    Text(
                                        text = (layer.createdBy ?: "N/A").trim(),
                                        color = Color(180, 200, 220),
                                        fontFamily = font.font,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(0.85f)
                                    )
                                }
                            }
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

    // TAG DIALOG
    (activeDialog as? ImageDialog.Tag)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Tag Image", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Repository:", fontFamily = font.font, color = font.fontColor, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newRepoInput,
                        onValueChange = { newRepoInput = it },
                        placeholder = { Text("e.g. my-registry/app", fontFamily = font.font, color = Color.Gray) },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tag:", fontFamily = font.font, color = font.fontColor, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("e.g. v1.0.0, latest", fontFamily = font.font, color = Color.Gray) },
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
                        if (newRepoInput.isNotBlank()) {
                            val r = newRepoInput.trim()
                            val t = if (newTagInput.isNotBlank()) newTagInput.trim() else "latest"
                            activeDialog = null
                            scope.launch {
                                try {
                                    appState.toastState.showToast("Tagging image as $r:$t...")
                                    appState.api.tagImage(dialog.imageId, r, t)
                                    appState.toastState.showToast("Successfully tagged $r:$t")
                                } catch (e: Exception) {
                                    appState.toastState.showToast("Tagging failed: ${e.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(31, 111, 235), contentColor = Color.White)
                ) {
                    Text("Add Tag", fontFamily = font.font)
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

    // RUN DIALOG
    (activeDialog as? ImageDialog.Run)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Run Container: ${dialog.imageRepoTag}", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Container Name (Optional):", fontFamily = font.font, color = font.fontColor, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = runContainerName,
                        onValueChange = { runContainerName = it },
                        placeholder = { Text("e.g. my-app-instance", fontFamily = font.font, color = Color.Gray) },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Command / Args (Optional):", fontFamily = font.font, color = font.fontColor, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = runContainerCmd,
                        onValueChange = { runContainerCmd = it },
                        placeholder = { Text("e.g. sh -c 'sleep 3600'", fontFamily = font.font, color = Color.Gray) },
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
                        val name = runContainerName.trim().ifBlank { null }
                        val cmd = runContainerCmd.trim().ifBlank { null }
                        val img = dialog.imageRepoTag
                        activeDialog = null
                        scope.launch {
                            try {
                                appState.toastState.showToast("Starting container from $img...")
                                appState.api.runImage(img, name, cmd)
                                appState.toastState.showToast("Container started successfully")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to run container: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(31, 111, 235), contentColor = Color.White)
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

    // REMOVE CONFIRMATION DIALOG
    (activeDialog as? ImageDialog.Remove)?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text("Delete Image", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Are you sure you want to delete '${dialog.imageRepoTag}'?",
                        fontFamily = font.font,
                        color = font.fontColor,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = forceRemove,
                            onCheckedChange = { forceRemove = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(220, 53, 69))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Force remove (even if stopped containers reference it)",
                            fontFamily = font.font,
                            color = font.fontColor,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { forceRemove = !forceRemove }
                        )
                    }
                }
            },
            confirmButton = {
                ElevatedButton(
                    onClick = {
                        val imgId = dialog.imageId
                        val force = forceRemove
                        activeDialog = null
                        scope.launch {
                            try {
                                appState.toastState.showToast("Removing image...")
                                appState.api.removeImage(imgId, force = force)
                                appState.toastState.showToast("Image removed successfully")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to remove image: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(220, 53, 69), contentColor = Color.White)
                ) {
                    Text("Delete", fontFamily = font.font)
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
}
