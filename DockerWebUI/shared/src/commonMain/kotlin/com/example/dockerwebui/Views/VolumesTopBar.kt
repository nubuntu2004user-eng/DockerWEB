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
fun volumesTopBar(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val secondaryColor = Color(50, 74, 110)
    var showPruneDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.07f).padding(4.dp)) {
        Text("Volumes", fontFamily = font.font, color = font.fontColor, fontSize = 32.sp)

        Spacer(modifier = Modifier.fillMaxWidth(0.35f))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search name, driver, label...", fontFamily = font.font, color = font.fontColor) },
            modifier = Modifier.fillMaxWidth(0.55f),
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
                appState.showCreateVolumeSection = !appState.showCreateVolumeSection
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(31, 111, 235),
                contentColor = Color.White
            )
        ) {
            Text(if (appState.showCreateVolumeSection) "Hide Create" else "+ Create volume", fontFamily = font.font, color = font.fontColor)
        }
    }

    if (showPruneDialog) {
        AlertDialog(
            onDismissRequest = { showPruneDialog = false },
            title = {
                Text("Prune Unused Volumes", fontFamily = font.font, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to remove all local volumes not used by at least one container? This action cannot be undone.",
                        fontFamily = font.font,
                        color = Color(200, 200, 200),
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                ElevatedButton(
                    onClick = {
                        showPruneDialog = false
                        scope.launch {
                            try {
                                val result = appState.api.pruneVolumes()
                                appState.toastState.showToast("Unused volumes pruned successfully")
                            } catch (e: Exception) {
                                appState.toastState.showToast("Failed to prune volumes: ${e.message}")
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
                    Text("Cancel", fontFamily = font.font, color = font.fontColor)
                }
            },
            containerColor = Color(27, 45, 68)
        )
    }
}
