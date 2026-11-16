package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun shell(appState: AppState, font: Font, scope: CoroutineScope, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    val outputs = remember { mutableStateMapOf<String, String>() }
    var currentShellID by remember { mutableStateOf("Host") }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val clipboard = LocalClipboardManager.current
    val terminalScrollState = rememberScrollState()

    fun appendOutput(id: String, chunk: String) {
        outputs[id] = (outputs[id] ?: "") + chunk + "\n"
    }

    fun stopHost() {
        currentJob?.cancel()
        currentJob = null
        scope.launch {
            appState.api.closeShell("Host")
            appState.toastState.showToast("Host shell stopped")
        }
    }

    fun runCurrentCommand() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        input = ""
        if (cmd == "clear") {
            outputs[currentShellID] = ""
        } else {
            appendOutput(currentShellID, "$ ${cmd}")
            currentJob?.cancel()
            currentJob = scope.launch {
                try {
                    if (currentShellID == "Host") {
                        appState.api.shellOnHost(cmd) { chunk -> appendOutput("Host", chunk) }
                    } else {
                        appState.api.streamShell(id = currentShellID, cmd = cmd) { chunk -> appendOutput(currentShellID, chunk) }
                    }
                } catch (e: Exception) {
                    appendOutput(currentShellID, "Error: ${e.message}")
                }
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(35, 55, 80), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(15, 23, 42)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Tab / Title Bar ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(13, 21, 33))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(40, 167, 69), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TERMINAL",
                    fontFamily = font.font,
                    color = Color(140, 160, 185),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(16.dp))

                // Scrollable Tabs
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Host tab
                    val isHostActive = currentShellID == "Host"
                    val hostBg = if (isHostActive) Color(27, 45, 68) else Color(18, 28, 42)
                    val hostBorder = if (isHostActive) Color(50, 75, 105) else Color(28, 42, 60)
                    val hostTextColor = if (isHostActive) Color.White else Color(140, 160, 185)

                    Row(
                        modifier = Modifier
                            .background(hostBg, RoundedCornerShape(6.dp))
                            .border(1.dp, hostBorder, RoundedCornerShape(6.dp))
                            .clickable { currentShellID = "Host" }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Host Shell",
                            fontFamily = font.font,
                            color = hostTextColor,
                            fontSize = 12.sp
                        )
                        if (isHostActive && currentJob != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { stopHost() },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop Host Shell",
                                    tint = Color(248, 81, 73),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Container tabs
                    for (shellID in appState.openShells) {
                        val container = appState.containers.find { it.id == shellID }
                        val isContainerActive = currentShellID == shellID
                        val tabBg = if (isContainerActive) Color(27, 45, 68) else Color(18, 28, 42)
                        val tabBorder = if (isContainerActive) Color(50, 75, 105) else Color(28, 42, 60)
                        val tabTextColor = if (isContainerActive) Color.White else Color(140, 160, 185)
                        val containerName = container?.name?.removePrefix("/") ?: shellID.take(12)

                        Row(
                            modifier = Modifier
                                .background(tabBg, RoundedCornerShape(6.dp))
                                .border(1.dp, tabBorder, RoundedCornerShape(6.dp))
                                .clickable { currentShellID = shellID }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                containerName,
                                fontFamily = font.font,
                                color = tabTextColor,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    appState.openShells.remove(shellID)
                                    outputs.remove(shellID)
                                    if (currentShellID == shellID) {
                                        currentShellID = "Host"
                                    }
                                    scope.launch { appState.api.closeShell(shellID) }
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Tab",
                                    tint = Color(160, 175, 195),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Quick tools on top right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { outputs[currentShellID] = "" },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            val text = outputs[currentShellID] ?: ""
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { appState.toastState.showToast("Terminal output copied") }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Output",
                            tint = Color(140, 160, 185),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Output Area ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(8, 12, 19))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val currentOutput = outputs[currentShellID] ?: ""
                if (currentOutput.isBlank()) {
                    Text(
                        text = "> Terminal ready on [${if (currentShellID == "Host") "Host OS" else currentShellID}]. Type command below.",
                        fontFamily = font.font,
                        color = Color(85, 110, 140),
                        fontSize = 12.sp
                    )
                } else {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = currentOutput,
                            fontFamily = font.font,
                            color = Color(220, 230, 245),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(terminalScrollState)
                        )
                    }
                }
            }

            // ── Input Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(13, 21, 33))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentShellID == "Host") "host$" else "docker#",
                    fontFamily = font.font,
                    color = Color(56, 189, 248),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            "Type command (e.g. ls -la, ps, env)...",
                            color = Color(85, 110, 140),
                            fontSize = 12.sp,
                            fontFamily = font.font
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(56, 189, 248),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                ElevatedButton(
                    onClick = { runCurrentCommand() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(31, 111, 235),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = "Run",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run", fontFamily = font.font, fontSize = 11.sp)
                }
            }
        }
    }
}
