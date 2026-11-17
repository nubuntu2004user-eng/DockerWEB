package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ContainerUsage(
    val name: String,
    val status: String
)

@Serializable
data class ImageData(
    val id: String,
    val repository: String,
    val tag: String,
    val size: Long,
    val created: String,
    val dangling: Boolean = false,
    @SerialName("used_by") val usedBy: List<ContainerUsage> = emptyList()
) {
    val shortId: String
        get() = id.removePrefix("sha256:").take(12)

    fun isUsed(): Boolean = usedBy.isNotEmpty()

    fun formattedSize(): String {
        val bytes = size
        val kib = bytes.toDouble() / 1024.0
        val mib = kib / 1024.0
        val gib = mib / 1024.0
        return when {
            gib >= 1.0 -> "${(gib * 100.0).roundToInt() / 100.0} GB"
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
data class ImagePullProgress(
    val status: String = "",
    val id: String? = null,
    val progress: String? = null,
    @SerialName("progressDetail") val progressDetail: PullProgressDetail? = null,
    val error: String? = null
)

@Serializable
data class PullProgressDetail(
    val current: Long? = null,
    val total: Long? = null
)

@Serializable
data class ImageHistoryLayer(
    @SerialName("Id") val id: String? = null,
    @SerialName("Created") val created: Long? = null,
    @SerialName("CreatedBy") val createdBy: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Comment") val comment: String? = null,
    @SerialName("Tags") val tags: List<String>? = null
) {
    fun formattedSize(): String {
        val s = size ?: 0L
        val kib = s.toDouble() / 1024.0
        val mib = kib / 1024.0
        val gib = mib / 1024.0
        return when {
            gib >= 1.0 -> "${(gib * 10.0).roundToInt() / 10.0} GB"
            mib >= 1.0 -> "${(mib * 10.0).roundToInt() / 10.0} MB"
            kib >= 1.0 -> "${(kib * 10.0).roundToInt() / 10.0} KB"
            s > 0L -> "$s B"
            else -> "0 B"
        }
    }
}
