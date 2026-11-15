package com.example.dockerwebui

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class DockerApi(
    private val client: HttpClient = HttpClient(Js) {
        install(SSE)
    },
    private val baseUrl: String = "http://127.0.0.1:8081"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun stopContainer(id: String): String {
        val response = client.post("$baseUrl/stopcontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun restartContainer(id: String): String {
        val response = client.post("$baseUrl/restartcontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun pauseContainer(id: String): String {
        val response = client.post("$baseUrl/pausecontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun unpauseContainer(id: String): String {
        val response = client.post("$baseUrl/unpausecontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun removeContainer(id: String): String {
        val response = client.post("$baseUrl/removecontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun getLogs(id: String): String {
        val response = client.get("$baseUrl/getlogs") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        val text = response.bodyAsText()
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun streamShell(id: String, cmd: String, onChunk: (String) -> Unit) {
        client.sse(
            urlString = "$baseUrl/shell",
            request = {
                parameter("id", id)
                parameter("cmd", cmd)
            }
        ) {
            incoming.collect { event ->
                event.data?.let { chunk ->
                    onChunk(chunk)
                }
            }
        }
    }

    suspend fun runShell(id: String, cmd: String): String {
        val sb = StringBuilder()
        streamShell(id, cmd) { chunk ->
            sb.append(chunk)
        }
        return sb.toString()
    }

    suspend fun inspectContainer(id: String): String {
        val response = client.get("$baseUrl/inspectcontainer") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        val text = response.bodyAsText()
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun pruneStopped(): String {
        val response = client.post("$baseUrl/prunecontainers")
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun addContainer(name: String): String {
        val response = client.post("$baseUrl/addcontainer") {
            parameter("name", name)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }
}
