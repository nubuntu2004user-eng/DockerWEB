package com.example.dockerwebui

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEBufferPolicy
import org.w3c.fetch.INCLUDE
import org.w3c.fetch.RequestCredentials
import web.navigator.navigator
import kotlin.time.Duration.Companion.seconds

class JsPlatform : Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
        ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
        ?: "Unknown"
}

actual fun getPlatform(): Platform = JsPlatform()

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