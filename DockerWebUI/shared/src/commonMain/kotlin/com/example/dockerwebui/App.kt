package com.example.dockerwebui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dockerwebui.Views.leftPanel
import com.example.dockerwebui.Views.rightPanelContainers
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEBufferPolicy
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 9456
)

@Composable
@Preview
fun App() {
    val client = remember {  HttpClient(Js){
        install(SSE){
            bufferPolicy = SSEBufferPolicy.LastEvents(10)
            maxReconnectionAttempts = 5
            reconnectionTime = 2.seconds
        }
    }
    }

    val scope = CoroutineScope(context = Dispatchers.Default)
    val state = remember{ AppState() }
    val font = Font()
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    var apiHost by remember { mutableStateOf("127.0.0.1") }
    var apiPort by remember { mutableStateOf(9456) }
    var configLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit){
        try{
            val response = client.get("config.json")
            if (response.status.value in 200..299) {
                val text = response.bodyAsText()
                val config = json.decodeFromString<ServerConfig>(text)
                val browserHost = kotlinx.browser.window.location.hostname
                val browserPort = kotlinx.browser.window.location.port.toIntOrNull()

                val resolvedHost = if (config.host == "0.0.0.0" || config.host.isBlank()) {
                    if (browserHost.isNotBlank()) browserHost else "127.0.0.1"
                } else {
                    config.host
                }
                val resolvedPort = if (config.port > 0) config.port else (browserPort ?: 9456)

                apiHost = resolvedHost
                apiPort = resolvedPort
                state.api = DockerApi(baseUrl = "http://$resolvedHost:$resolvedPort")
            }
        } catch (e: Exception) {
            println("Failed to load config.json: $e")
        } finally {
            configLoaded = true
        }
    }

    LaunchedEffect(configLoaded, apiHost, apiPort){
        if (!configLoaded) return@LaunchedEffect
        try{
            client.sse(
                host = apiHost,
                port = apiPort,
                path = "/events"
            ) {
                incoming.collect { event ->
                    val dataStr = event.data
                    if (dataStr.isNullOrBlank()) return@collect
                    try {
                        val payload = json.decodeFromString<UnifiedSsePayload>(dataStr)
                        payload.containers?.let { containers ->
                            state.containers.clear()
                            state.containers.addAll(containers)
                        }
                        payload.images?.let { images ->
                            state.images.clear()
                            state.images.addAll(images)
                        }
                        payload.volumes?.let { volumes ->
                            state.volumes.clear()
                            state.volumes.addAll(volumes)
                        }
                        payload.networks?.let { networks ->
                            state.networks.clear()
                            state.networks.addAll(networks)
                        }
                    } catch (e: Exception) {
                        println("Failed to parse unified SSE payload: $e")
                    }
                }
            }
        }
        catch(e: Exception){
            if (e is CancellationException) {
                ensureActive()
            }
            println("Exception in unified SSE: $e")
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(color = Color(11, 18, 27))) {
        Row(modifier = Modifier.fillMaxSize()) {
            leftPanel(0.15f, appState = state, font = font)
            rightPanelContainers(1f, font = font, appState = state, scope = scope)
        }
        ToastHost(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            alignment = Alignment.BottomCenter,
            hostState = state.toastState
        )
    }
}
