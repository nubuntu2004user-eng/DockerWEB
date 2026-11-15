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
fun containersTopBar(
    font: Font,
    appState: AppState,
    scope: CoroutineScope,
    userInputSearch: String,
    onSearchChange: (String) -> Unit
) {
    val secondaryColor = Color(50, 74, 110)
    var showRunDialog by remember { mutableStateOf(false) }
    var newImageName by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.07f).padding(4.dp)) {
        Text("Containers", fontFamily = font.font, color = font.fontColor, fontSize = 32.sp)

        Spacer(modifier = Modifier.fillMaxWidth(0.3f))

        OutlinedTextField(
            value = userInputSearch,
            onValueChange = onSearchChange,
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
                showRunDialog = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(31, 111, 235),
                contentColor = Color.White
            )
        ) {
            Text("+ Run new container", fontFamily = font.font, color = font.fontColor)
        }
    }

    if (showRunDialog) {
        AlertDialog(
            onDismissRequest = { showRunDialog = false },
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
                            showRunDialog = false
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
                TextButton(onClick = { showRunDialog = false }) {
                    Text("Cancel", fontFamily = font.font, color = Color.White)
                }
            },
            containerColor = Color(27, 45, 68),
            shape = RoundedCornerShape(12.dp)
        )
    }
}
