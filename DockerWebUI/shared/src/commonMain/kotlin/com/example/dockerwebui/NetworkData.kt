package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkContainerUsage(
    val id: String,
    val name: String,
    val status: String = "running",
    val ipv4: String = "",
    val ipv6: String = ""
)

@Serializable
data class NetworkData(
    val id: String,
    @SerialName("short_id") val shortId: String = "",
    val name: String,
    val driver: String = "bridge",
    val scope: String = "local",
    val internal: Boolean = false,
    val attachable: Boolean = false,
    val ingress: Boolean = false,
    val created: String = "",
    @SerialName("is_system") val isSystem: Boolean = false,
    val subnet: String = "",
    val gateway: String = "",
    val containers: List<NetworkContainerUsage> = emptyList(),
    val labels: Map<String, String> = emptyMap()
) {
    fun isUsed(): Boolean = containers.isNotEmpty()

    fun formattedSubnet(): String = if (subnet.isNotBlank()) subnet else "—"

    fun formattedGateway(): String = if (gateway.isNotBlank()) gateway else "—"

    fun formattedCreated(): String {
        if (created.isBlank() || created.startsWith("1970")) return "Unknown"
        return try {
            val datePart = created.substringBefore("T")
            val timePart = created.substringAfter("T").substringBefore(".").substringBefore("Z").substringBefore("+")
            if (datePart.isNotBlank() && timePart.isNotBlank()) "$datePart $timePart" else created
        } catch (_: Exception) {
            created
        }
    }
}
