package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun imagesPullSection(
    font: Font,
    appState: AppState,
    scope: CoroutineScope
) {
    if (!appState.showPullSection && !appState.isPullingImage) return

    var pullImageName by remember { mutableStateOf("") }
    val layerStatuses = remember { mutableStateMapOf<String, String>() }
    val layerProgress = remember { mutableStateMapOf<String, Float>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .background(Color(15, 23, 42), shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pullImageName,
                onValueChange = { pullImageName = it },
                enabled = !appState.isPullingImage,
                placeholder = { Text("Enter image name (e.g. redis:7, nginx:alpine, postgres:16)...", fontFamily = font.font, color = Color.Gray) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(27, 45, 68),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(27, 45, 68),
                    disabledContainerColor = Color(20, 32, 48)
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            ElevatedButton(
                onClick = {
                    if (pullImageName.isNotBlank() && !appState.isPullingImage) {
                        val targetImage = pullImageName.trim()
                        appState.isPullingImage = true
                        appState.pullStatusText = "Connecting..."
                        appState.pullProgressPercentage = 0f
                        layerStatuses.clear()
                        layerProgress.clear()

                        scope.launch {
                            try {
                                appState.toastState.showToast("Pulling image $targetImage...")
                                appState.api.streamPullImage(targetImage) { progress ->
                                    if (!progress.error.isNullOrBlank()) {
                                        appState.pullStatusText = "Error: ${progress.error}"
                                    } else {
                                        val id = progress.id
                                        val status = progress.status
                                        if (id != null) {
                                            layerStatuses[id] = status
                                            val current = progress.progressDetail?.current
                                            val total = progress.progressDetail?.total
                                            if (current != null && total != null && total > 0) {
                                                layerProgress[id] = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            } else if (status.contains("complete", ignoreCase = true) || status.contains("already exists", ignoreCase = true)) {
                                                layerProgress[id] = 1f
                                            }
                                        }

                                        val totalLayers = layerStatuses.size
                                        val completedLayers = layerStatuses.values.count {
                                            it.contains("complete", ignoreCase = true) || it.contains("already exists", ignoreCase = true)
                                        }

                                        val sumProgress = if (layerProgress.isNotEmpty()) {
                                            layerProgress.values.sum() / layerProgress.size
                                        } else 0f

                                        appState.pullProgressPercentage = sumProgress.coerceIn(0f, 1f)

                                        if (totalLayers > 0) {
                                            val pct = (sumProgress * 100).roundToInt()
                                            appState.pullStatusText = "$targetImage [$pct%] · $completedLayers of $totalLayers layers ($status)"
                                        } else {
                                            appState.pullStatusText = "$targetImage · $status"
                                        }
                                    }
                                }
                                appState.toastState.showToast("Pull complete: $targetImage")
                                appState.pullStatusText = "Successfully pulled $targetImage"
                                appState.pullProgressPercentage = 1f
                            } catch (e: Exception) {
                                appState.pullStatusText = "Failed: ${e.message}"
                                appState.toastState.showToast("Pull failed: ${e.message}")
                            } finally {
                                appState.isPullingImage = false
                            }
                        }
                    }
                },
                enabled = !appState.isPullingImage && pullImageName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(31, 111, 235),
                    contentColor = Color.White
                )
            ) {
                Text(if (appState.isPullingImage) "Pulling..." else "Pull", fontFamily = font.font)
            }
        }

        if (appState.isPullingImage || appState.pullStatusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appState.pullStatusText,
                    fontFamily = font.font,
                    color = font.fontColor,
                    fontSize = 13.sp
                )
                Text(
                    text = "${(appState.pullProgressPercentage * 100).roundToInt()}%",
                    fontFamily = font.font,
                    color = Color(31, 111, 235),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { appState.pullProgressPercentage },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(31, 111, 235),
                trackColor = Color(27, 45, 68)
            )
        }
    }
}
