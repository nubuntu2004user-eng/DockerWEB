package com.example.dockerwebui

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LoginRequest(
    val user: String,
    val password: String
)

@Serializable
data class AuthStatusResponse(
    val authenticated: Boolean,
    val user: String? = null
)

@Serializable
private data class ErrorDetail(
    val detail: String? = null
)

class UnauthorizedException(message: String = "Session expired. Please sign in again.") : Exception(message)

class DockerApi(
    private val client: HttpClient = createAppHttpClient(configureSse = true),
    private val baseUrl: String = "http://127.0.0.1:9456",
    private val onUnauthorized: () -> Unit = {}
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private suspend fun requireSuccess(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.value == 401) {
            onUnauthorized()
            throw UnauthorizedException(parseErrorDetail(body) ?: "Authentication required")
        }
        if (response.status.value !in 200..299) {
            throw Exception(parseErrorDetail(body) ?: body)
        }
        return body
    }

    private fun parseErrorDetail(raw: String): String? {
        return try {
            json.decodeFromString<ErrorDetail>(raw).detail
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun notifyIfUnauthorized(e: Exception) {
        val msg = e.message.orEmpty()
        if (msg.contains("401")) {
            onUnauthorized()
            return
        }
        try {
            if (!checkAuthStatus().authenticated) {
                onUnauthorized()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun checkAuthStatus(): AuthStatusResponse {
        val response = client.get("$baseUrl/auth/status")
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw Exception(parseErrorDetail(body) ?: "Failed to check authentication status")
        }
        return json.decodeFromString<AuthStatusResponse>(body)
    }

    suspend fun login(user: String, password: String): AuthStatusResponse {
        val response = client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user = user, password = password)))
        }
        val body = response.bodyAsText()
        if (response.status.value == 401) {
            throw Exception(parseErrorDetail(body) ?: "Invalid username or password")
        }
        if (response.status.value !in 200..299) {
            throw Exception(parseErrorDetail(body) ?: body.ifBlank { "Login failed (${response.status.value})" })
        }
        return json.decodeFromString<AuthStatusResponse>(body)
    }

    suspend fun logout(): AuthStatusResponse {
        val response = client.post("$baseUrl/auth/logout")
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw Exception(parseErrorDetail(body) ?: body)
        }
        return json.decodeFromString<AuthStatusResponse>(body)
    }

    suspend fun stopContainer(id: String): String {
        val response = client.post("$baseUrl/stopcontainer") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun restartContainer(id: String): String {
        val response = client.post("$baseUrl/restartcontainer") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun pauseContainer(id: String): String {
        val response = client.post("$baseUrl/pausecontainer") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun unpauseContainer(id: String): String {
        val response = client.post("$baseUrl/unpausecontainer") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun removeContainer(id: String): String {
        val response = client.post("$baseUrl/removecontainer") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun getLogs(id: String): String {
        val response = client.get("$baseUrl/getlogs") {
            parameter("id", id)
        }
        val text = requireSuccess(response)
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun streamShell(id: String, cmd: String, onChunk: (String) -> Unit) {
        try {
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
        } catch (e: Exception) {
            notifyIfUnauthorized(e)
            throw e
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
        val text = requireSuccess(response)
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun pruneStopped(): String {
        val response = client.post("$baseUrl/prunecontainers")
        return requireSuccess(response)
    }

    suspend fun addContainer(name: String): String {
        val response = client.post("$baseUrl/addcontainer") {
            parameter("name", name)
        }
        return requireSuccess(response)
    }

    suspend fun closeShell(id: String): String {
        val response = client.post("$baseUrl/closeshell") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun shellOnHost(cmd: String, onChunk: (String) -> Unit) {
        try {
            client.sse(
                urlString = "$baseUrl/shellhost",
                request = {
                    parameter("cmd", cmd)
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { chunk ->
                        onChunk(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            notifyIfUnauthorized(e)
            throw e
        }
    }

    suspend fun tagImage(id: String, repo: String, tag: String = "latest"): String {
        val response = client.post("$baseUrl/tagimage") {
            parameter("id", id)
            parameter("repo", repo)
            parameter("tag", tag)
        }
        return requireSuccess(response)
    }

    suspend fun removeImage(id: String, force: Boolean = false): String {
        val response = client.post("$baseUrl/removeimage") {
            parameter("id", id)
            parameter("force", force)
        }
        return requireSuccess(response)
    }

    suspend fun pruneImages(danglingOnly: Boolean = false): String {
        val response = client.post("$baseUrl/pruneimages") {
            parameter("dangling_only", danglingOnly)
        }
        return requireSuccess(response)
    }

    suspend fun inspectImage(id: String): String {
        val response = client.get("$baseUrl/inspectimage") {
            parameter("id", id)
        }
        val text = requireSuccess(response)
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun getImageHistory(id: String): List<ImageHistoryLayer> {
        val response = client.get("$baseUrl/imagehistory") {
            parameter("id", id)
        }
        val text = requireSuccess(response)
        val parsedJson = try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
        return try {
            json.decodeFromString<List<ImageHistoryLayer>>(parsedJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun importImage(path: String): String {
        val response = client.post("$baseUrl/importimage") {
            parameter("path", path)
        }
        return requireSuccess(response)
    }

    suspend fun runImage(image: String, name: String? = null, command: String? = null): String {
        val response = client.post("$baseUrl/runimage") {
            parameter("image", image)
            if (!name.isNullOrBlank()) parameter("name", name)
            if (!command.isNullOrBlank()) parameter("command", command)
        }
        return requireSuccess(response)
    }

    suspend fun streamPullImage(image: String, onProgress: (ImagePullProgress) -> Unit) {
        try {
            client.sse(
                urlString = "$baseUrl/pullimage",
                request = {
                    parameter("image", image)
                }
            ) {
                incoming.collect { event ->
                    event.data?.let { rawJson ->
                        try {
                            val progressObj = json.decodeFromString<ImagePullProgress>(rawJson)
                            onProgress(progressObj)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            notifyIfUnauthorized(e)
            throw e
        }
    }

    suspend fun createVolume(name: String, driver: String = "local", labels: String? = null): String {
        val response = client.post("$baseUrl/createvolume") {
            parameter("name", name)
            parameter("driver", driver)
            if (!labels.isNullOrBlank()) parameter("labels", labels)
        }
        return requireSuccess(response)
    }

    suspend fun removeVolume(name: String, force: Boolean = false): String {
        val response = client.post("$baseUrl/removevolume") {
            parameter("name", name)
            parameter("force", force)
        }
        return requireSuccess(response)
    }

    suspend fun pruneVolumes(): String {
        val response = client.post("$baseUrl/prunevolumes")
        return requireSuccess(response)
    }

    suspend fun inspectVolume(name: String): String {
        val response = client.get("$baseUrl/inspectvolume") {
            parameter("name", name)
        }
        val text = requireSuccess(response)
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun browseVolume(name: String, path: String = "/"): VolumeBrowseResponse {
        val response = client.get("$baseUrl/browsevolume") {
            parameter("name", name)
            parameter("path", path)
        }
        val text = requireSuccess(response)
        val parsedJson = try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
        return try {
            json.decodeFromString<VolumeBrowseResponse>(parsedJson)
        } catch (_: Exception) {
            VolumeBrowseResponse(path = path, raw = text)
        }
    }

    suspend fun cloneVolume(sourceName: String, targetName: String): String {
        val response = client.post("$baseUrl/clonevolume") {
            parameter("source_name", sourceName)
            parameter("target_name", targetName)
        }
        return requireSuccess(response)
    }

    suspend fun backupVolume(name: String, destDir: String? = null): String {
        val response = client.post("$baseUrl/backupvolume") {
            parameter("name", name)
            if (!destDir.isNullOrBlank()) parameter("dest_dir", destDir)
        }
        return requireSuccess(response)
    }

    suspend fun createNetwork(
        name: String,
        driver: String = "bridge",
        subnet: String? = null,
        gateway: String? = null,
        labels: String? = null
    ): String {
        val response = client.post("$baseUrl/createnetwork") {
            parameter("name", name)
            parameter("driver", driver)
            if (!subnet.isNullOrBlank()) parameter("subnet", subnet)
            if (!gateway.isNullOrBlank()) parameter("gateway", gateway)
            if (!labels.isNullOrBlank()) parameter("labels", labels)
        }
        return requireSuccess(response)
    }

    suspend fun removeNetwork(id: String): String {
        val response = client.post("$baseUrl/removenetwork") {
            parameter("id", id)
        }
        return requireSuccess(response)
    }

    suspend fun pruneNetworks(): String {
        val response = client.post("$baseUrl/prunenetworks")
        return requireSuccess(response)
    }

    suspend fun inspectNetwork(id: String): String {
        val response = client.get("$baseUrl/inspectnetwork") {
            parameter("id", id)
        }
        val text = requireSuccess(response)
        return try {
            json.decodeFromString<String>(text)
        } catch (_: Exception) {
            text
        }
    }

    suspend fun connectNetwork(
        networkId: String,
        containerId: String,
        ipv4: String? = null,
        ipv6: String? = null
    ): String {
        val response = client.post("$baseUrl/connectnetwork") {
            parameter("network_id", networkId)
            parameter("container_id", containerId)
            if (!ipv4.isNullOrBlank()) parameter("ipv4_address", ipv4)
            if (!ipv6.isNullOrBlank()) parameter("ipv6_address", ipv6)
        }
        return requireSuccess(response)
    }

    suspend fun disconnectNetwork(
        networkId: String,
        containerId: String,
        force: Boolean = false
    ): String {
        val response = client.post("$baseUrl/disconnectnetwork") {
            parameter("network_id", networkId)
            parameter("container_id", containerId)
            parameter("force", force)
        }
        return requireSuccess(response)
    }
}
