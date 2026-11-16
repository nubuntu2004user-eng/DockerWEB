package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class VolumeData(
    val name: String,
    val driver: String = "local",
    val mountpoint: String = "",
    val created: String = "",
    val labels: Map<String, String> = emptyMap(),
    val scope: String = "local",
    val size: Long = 0L,
    val anonymous: Boolean = false,
    @SerialName("used_by") val usedBy: List<ContainerUsage> = emptyList()
) {
    fun isUsed(): Boolean = usedBy.isNotEmpty()

    fun displayName(): String {
        return if (anonymous && name.length >= 24) {
            "${name.take(12)}...${name.takeLast(4)}"
        } else {
            name
        }
    }

    fun formattedSize(): String {
        val bytes = size
        if (bytes <= 0L) return "—"
        val kib = bytes.toDouble() / 1024.0
        val mib = kib / 1024.0
        val gib = mib / 1024.0
        return when {
            gib >= 1.0 -> "${(gib * 10.0).roundToInt() / 10.0} GB"
            mib >= 1.0 -> "${(mib * 10.0).roundToInt() / 10.0} MB"
            kib >= 1.0 -> "${(kib * 10.0).roundToInt() / 10.0} KB"
            else -> "$bytes B"
        }
    }

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

@Serializable
data class VolumeBrowseEntry(
    val name: String,
    val type: String = "file",
    val size: String = "",
    val permissions: String = "",
    val modified: String = ""
)

@Serializable
data class VolumeBrowseResponse(
    val path: String = "/",
    val entries: List<VolumeBrowseEntry> = emptyList(),
    val error: String? = null,
    val raw: String? = null
)
