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
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
        }
    }

    LaunchedEffect(apiHost, apiPort){
        try{
            client.sse(
                host = apiHost,
                port = apiPort,
                path = "/stats",
                deserialize = { typeInfo, jsonString ->
                    val serializer = json.serializersModule.serializer(typeInfo.kotlinType!!)
                    json.decodeFromString(serializer, jsonString)!!
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { dataStr ->
                        val containers = deserialize<List<ContainerData>>(dataStr)
                        if (containers != null) {
                            state.containers.clear()
                            state.containers.addAll(containers)
                        }
                    }
                }
            }
        }
        catch(e: Exception){
            if (e is CancellationException) {
                ensureActive()
            }
            println("Exception in stats SSE:$e")
        }
    }
    LaunchedEffect(apiHost, apiPort){
        try{
            client.sse(
                host = apiHost,
                port = apiPort,
                path = "/imagestats",
                deserialize = { typeInfo, jsonString ->
                    val serializer = json.serializersModule.serializer(typeInfo.kotlinType!!)
                    json.decodeFromString(serializer, jsonString)!!
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { dataStr ->
                        val images = deserialize<List<ImageData>>(dataStr)
                        if (images != null) {
                            state.images.clear()
                            state.images.addAll(images)
                        }
                    }
                }
            }
        }
        catch(e: Exception){
            if (e is CancellationException) {
                ensureActive()
            }
            println("Exception in imagestats SSE:$e")
        }
    }
    LaunchedEffect(apiHost, apiPort){
        try{
            client.sse(
                host = apiHost,
                port = apiPort,
                path = "/volumestats",
                deserialize = { typeInfo, jsonString ->
                    val serializer = json.serializersModule.serializer(typeInfo.kotlinType!!)
                    json.decodeFromString(serializer, jsonString)!!
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { dataStr ->
                        val volumes = deserialize<List<VolumeData>>(dataStr)
                        if (volumes != null) {
                            state.volumes.clear()
                            state.volumes.addAll(volumes)
                        }
                    }
                }
            }
        }
        catch(e: Exception){
            if (e is CancellationException) {
                ensureActive()
            }
            println("Exception in volumestats SSE:$e")
        }
    }
    LaunchedEffect(apiHost, apiPort){
        try{
            client.sse(
                host = apiHost,
                port = apiPort,
                path = "/networkstats",
                deserialize = { typeInfo, jsonString ->
                    val serializer = json.serializersModule.serializer(typeInfo.kotlinType!!)
                    json.decodeFromString(serializer, jsonString)!!
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { dataStr ->
                        val networks = deserialize<List<NetworkData>>(dataStr)
                        if (networks != null) {
                            state.networks.clear()
                            state.networks.addAll(networks)
                        }
                    }
                }
            }
        }
        catch(e: Exception){
            if (e is CancellationException) {
                ensureActive()
            }
            println("Exception in networkstats SSE:$e")
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
