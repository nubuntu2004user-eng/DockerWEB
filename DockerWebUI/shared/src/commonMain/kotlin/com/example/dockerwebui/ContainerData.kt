package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class ContainerStatus {
    RUNNING,
    PAUSED,
    STOPPED,
    RESTARTING,
    REMOVING,
    DEAD,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ContainerStatus {
            return when (value?.trim()?.lowercase()) {
                "running" -> RUNNING
                "paused" -> PAUSED
                "exited", "stopped", "created" -> STOPPED
                "restarting" -> RESTARTING
                "removing" -> REMOVING
                "dead" -> DEAD
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
data class ContainerData(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("os_type")
    val osType: String? = null,
    @SerialName("cpu_percent")
    val cpuPercent: Double = 0.0,
    @SerialName("memory_usage")
    val memoryUsage: Long = 0L,
    @SerialName("memory_limit")
    val memoryLimit: Long = 0L,
) {
    fun containerStatus(): ContainerStatus = ContainerStatus.fromString(status)

    fun cpuPercentage(): Double = (cpuPercent * 100.0).roundToInt() / 100.0

    fun memoryUsageBytes(): Long = memoryUsage.coerceAtLeast(0L)

    fun memoryUsageFormatted(): String {
        val bytes = memoryUsageBytes()
        val mib = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mib >= 1024.0) {
            val gib = mib / 1024.0
            "${(gib * 10.0).roundToInt() / 10.0} GiB"
        } else {
            "${(mib * 10.0).roundToInt() / 10.0} MiB"
        }
    }

    fun memoryPercentage(): Double {
        if (memoryLimit <= 0L) return 0.0
        val used = memoryUsageBytes()
        val pct = (used.toDouble() / memoryLimit.toDouble()) * 100.0
        return (pct * 100.0).roundToInt() / 100.0
    }
}
