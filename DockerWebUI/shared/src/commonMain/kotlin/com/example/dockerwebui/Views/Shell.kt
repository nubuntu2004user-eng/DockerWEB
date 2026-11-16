package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun shell(appState: AppState, font: Font, scope: CoroutineScope) {
    var input by remember { mutableStateOf("") }
    val outputs = remember { mutableStateMapOf<String, String>() }
    val textPadding = 8.dp
    var currentShellID by remember { mutableStateOf("Host") }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val clipboard = LocalClipboardManager.current

    fun appendOutput(id: String, chunk: String) {
        outputs[id] = (outputs[id] ?: "") + chunk + "\n"
    }

    fun stopHost() {
        currentJob?.cancel()
        currentJob = null
        scope.launch { appState.api.closeShell("Host") }
    }

    ElevatedCard(modifier = Modifier.fillMaxSize() ,
        colors = CardDefaults.cardColors(
            containerColor = Color(63, 126, 138)
        )

        ) {
        // ── Tab bar ──────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().weight(0.2f).horizontalScroll(rememberScrollState())) {
            // Host tab
            val hostColor = if (currentShellID == "Host") Color(27, 45, 68) else Color(50, 74, 110)
            ElevatedCard(
                onClick = { currentShellID = "Host" },
                colors = CardDefaults.cardColors(containerColor = hostColor),
                shape = RoundedCornerShape(0)
            ) {
                Row {
                    Text("Host", modifier = Modifier.padding(textPadding), fontFamily = font.font)
                    IconButton(onClick = { stopHost() }) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
                    }
                }
            }
            // Container tabs
            for (shellID in appState.openShells) {
                val container = appState.containers.find { it.id == shellID }
                val buttonColor = if (currentShellID == shellID) Color(27, 45, 68) else Color(50, 74, 110)
                ElevatedCard(
                    onClick = { currentShellID = shellID },
                    colors = CardDefaults.cardColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(0)

                ) {
                    Row {
                        Text(
                            container?.name.toString().substringAfter("/"),
                            modifier = Modifier.padding(textPadding),
                            fontFamily = font.font
                        )
                        IconButton(onClick = {
                            appState.openShells.remove(shellID)
                            outputs.remove(shellID)
                            currentShellID = "Host"
                            scope.launch { appState.api.closeShell(shellID) }
                        }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                        }
                    }
                }
            }
        }

        // ── Output area ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().weight(0.6f).background(Color.Black)) {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                Text(
                    outputs[currentShellID] ?: "",
                    fontFamily = font.font,
                    color = font.fontColor,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp)
                )
            }
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(outputs[currentShellID] ?: "")) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy output",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        // ── Input bar ────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().weight(0.25f)) {
            TextField(
                value = input,
                onValueChange = { input = it },
                trailingIcon = {
                    IconButton(onClick = {
                        val cmd = input
                        input = ""
                        if (cmd.trim() == "clear") {
                            outputs[currentShellID] = ""
                        } else {
                            currentJob?.cancel()
                            currentJob = scope.launch {
                                if (currentShellID == "Host") {
                                    appState.api.shellOnHost(cmd) { chunk -> appendOutput("Host", chunk) }
                                } else {
                                    appState.api.streamShell(id = currentShellID, cmd = cmd) { chunk -> appendOutput(currentShellID, chunk) }
                                }
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Filled.RocketLaunch, contentDescription = null)
                    }
                },
                placeholder = { Text("Enter command and press the button to run") },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
