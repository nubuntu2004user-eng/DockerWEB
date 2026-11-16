package com.example.dockerwebui.Views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun imagesTopBar(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val secondaryColor = Color(50, 74, 110)
    var showImportDialog by remember { mutableStateOf(false) }
    var importFilePath by remember { mutableStateOf("") }
    var showPruneDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.07f).padding(4.dp)) {
        Text("Images", fontFamily = font.font, color = font.fontColor, fontSize = 32.sp)

        Spacer(modifier = Modifier.fillMaxWidth(0.25f))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search repository, tag, ID...", fontFamily = font.font, color = font.fontColor) },
            modifier = Modifier.fillMaxWidth(0.5f),
            shape = RoundedCornerShape(20),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                focusedContainerColor = Color(27, 45, 68),
                unfocusedTextColor = Color.White,
                unfocusedContainerColor = Color(27, 45, 68)
            )
        )

        Spacer(modifier = Modifier.fillMaxWidth(0.03f))

        ElevatedButton(
            onClick = {
                importFilePath = ""
                showImportDialog = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = secondaryColor,
                contentColor = Color.White
            )
        ) {
            Text("Import", fontFamily = font.font, color = font.fontColor)
        }

        Spacer(modifier = Modifier.fillMaxWidth(0.03f))

        ElevatedButton(
            onClick = {
                showPruneDialog = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = secondaryColor,
                contentColor = Color.White
            )
        ) {
            Text("Prune unused", fontFamily = font.font, color = font.fontColor)
        }

        Spacer(modifier = Modifier.fillMaxWidth(0.03f))

        ElevatedButton(
            onClick = {
                appState.showPullSection = !appState.showPullSection
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(31, 111, 235),
                contentColor = Color.White
            )
        ) {
            Text(if (appState.showPullSection) "Close Pull" else "+ Pull Image", fontFamily = font.font, color = font.fontColor)
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text("Import Image from File", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Enter absolute path to Docker tar archive on the host:", fontFamily = font.font, color = font.fontColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importFilePath,
                        onValueChange = { importFilePath = it },
                        placeholder = { Text("/path/to/image.tar", fontFamily = font.font, color = Color.Gray) },
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
                        if (importFilePath.isNotBlank()) {
                            val path = importFilePath
                            showImportDialog = false
                            scope.launch {
                                try {
                                    appState.toastState.showToast("Loading image from $path...")
                                    appState.api.importImage(path)
                                    appState.toastState.showToast("Image imported successfully")
                                } catch (e: Exception) {
                                    appState.toastState.showToast("Error importing image: ${e.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(31, 111, 235),
                        contentColor = Color.White
                    )
                ) {
                    Text("Import", fontFamily = font.font)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", fontFamily = font.font, color = Color.White)
                }
            },
            containerColor = Color(27, 45, 68),
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showPruneDialog) {
        AlertDialog(
            onDismissRequest = { showPruneDialog = false },
            title = {
                Text("Prune Unused Images", fontFamily = font.font, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Text(
                    "Are you sure you want to prune dangling/unused images? This will remove all images not associated with a container.",
                    fontFamily = font.font,
                    color = font.fontColor,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                ElevatedButton(
                    onClick = {
                        showPruneDialog = false
                        scope.launch {
                            try {
                                appState.toastState.showToast("Pruning images...")
                                appState.api.pruneImages(danglingOnly = false)
                                appState.toastState.showToast("Pruning completed")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Error pruning images: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(220, 53, 69),
                        contentColor = Color.White
                    )
                ) {
                    Text("Prune", fontFamily = font.font)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPruneDialog = false }) {
                    Text("Cancel", fontFamily = font.font, color = Color.White)
                }
            },
            containerColor = Color(27, 45, 68),
            shape = RoundedCornerShape(12.dp)
        )
    }
}
