package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkContainerUsage(
    val id: String,
    val name: String,
    val status: String = "running",
    val ipv4: String = ""
)

@Serializable
data class NetworkData(
    val id: String,
    val name: String,
    val driver: String = "bridge",
    val scope: String = "local",
    @SerialName("is_system") val isSystem: Boolean = false,
    val subnet: String = "",
    val gateway: String = "",
    val containers: List<NetworkContainerUsage> = emptyList()
) {
    fun isUsed(): Boolean = containers.isNotEmpty()

    fun formattedSubnet(): String = if (subnet.isNotBlank()) subnet else "—"

    fun formattedGateway(): String = if (gateway.isNotBlank()) gateway else "—"
}
