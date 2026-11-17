package com.example.dockerwebui

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEBufferPolicy
import org.w3c.fetch.INCLUDE
import org.w3c.fetch.RequestCredentials
import kotlin.time.Duration.Companion.seconds

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun createAppHttpClient(configureSse: Boolean): HttpClient = HttpClient(Js) {
    engine {
        configureRequest {
            credentials = RequestCredentials.INCLUDE
        }
    }
    if (configureSse) {
        install(SSE) {
            bufferPolicy = SSEBufferPolicy.LastEvents(10)
            maxReconnectionAttempts = 5
            reconnectionTime = 2.seconds
        }
    }
}