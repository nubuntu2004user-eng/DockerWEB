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
    private val baseUrl: String = "http://127.0.0.1:9456"
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

    suspend fun closeShell(id: String): String{
        val response = client.post("$baseUrl/closeshell"){
            parameter("id" , id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }
    suspend fun shellOnHost(cmd: String , onChunk: (String) -> Unit){
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
    }

    suspend fun tagImage(id: String, repo: String, tag: String = "latest"): String {
        val response = client.post("$baseUrl/tagimage") {
            parameter("id", id)
            parameter("repo", repo)
            parameter("tag", tag)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun removeImage(id: String, force: Boolean = false): String {
        val response = client.post("$baseUrl/removeimage") {
            parameter("id", id)
            parameter("force", force)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun pruneImages(danglingOnly: Boolean = false): String {
        val response = client.post("$baseUrl/pruneimages") {
            parameter("dangling_only", danglingOnly)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun inspectImage(id: String): String {
        val response = client.get("$baseUrl/inspectimage") {
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

    suspend fun getImageHistory(id: String): List<ImageHistoryLayer> {
        val response = client.get("$baseUrl/imagehistory") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        val text = response.bodyAsText()
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
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun runImage(image: String, name: String? = null, command: String? = null): String {
        val response = client.post("$baseUrl/runimage") {
            parameter("image", image)
            if (!name.isNullOrBlank()) parameter("name", name)
            if (!command.isNullOrBlank()) parameter("command", command)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun streamPullImage(image: String, onProgress: (ImagePullProgress) -> Unit) {
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
    }

    suspend fun createVolume(name: String, driver: String = "local", labels: String? = null): String {
        val response = client.post("$baseUrl/createvolume") {
            parameter("name", name)
            parameter("driver", driver)
            if (!labels.isNullOrBlank()) parameter("labels", labels)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun removeVolume(name: String, force: Boolean = false): String {
        val response = client.post("$baseUrl/removevolume") {
            parameter("name", name)
            parameter("force", force)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun pruneVolumes(): String {
        val response = client.post("$baseUrl/prunevolumes")
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun inspectVolume(name: String): String {
        val response = client.get("$baseUrl/inspectvolume") {
            parameter("name", name)
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

    suspend fun browseVolume(name: String, path: String = "/"): VolumeBrowseResponse {
        val response = client.get("$baseUrl/browsevolume") {
            parameter("name", name)
            parameter("path", path)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        val text = response.bodyAsText()
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
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun backupVolume(name: String, destDir: String? = null): String {
        val response = client.post("$baseUrl/backupvolume") {
            parameter("name", name)
            if (!destDir.isNullOrBlank()) parameter("dest_dir", destDir)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
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
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun removeNetwork(id: String): String {
        val response = client.post("$baseUrl/removenetwork") {
            parameter("id", id)
        }
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun pruneNetworks(): String {
        val response = client.post("$baseUrl/prunenetworks")
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }

    suspend fun inspectNetwork(id: String): String {
        val response = client.get("$baseUrl/inspectnetwork") {
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
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
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
        if (response.status.value !in 200..299) {
            throw Exception(response.bodyAsText())
        }
        return response.bodyAsText()
    }
}
