package com.example.dockerwebui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

@Serializable
data class ContainerData(
    val id: String? = null,
    val name: String? = null,
    @SerialName("os_type")
    val osType: String? = null,
    val read: String? = null,
    val preread: String? = null,
    @SerialName("num_procs")
    val numProcs: Long? = null,
    @SerialName("cpu_stats")
    val cpuStats: CpuStats? = null,
    @SerialName("precpu_stats")
    val preCpuStats: CpuStats? = null,
    @SerialName("memory_stats")
    val memoryStats: MemoryStats? = null,
    @SerialName("pids_stats")
    val pidsStats: PidsStats? = null,
    @SerialName("blkio_stats")
    val blkioStats: BlkioStats? = null,
    @SerialName("storage_stats")
    val storageStats: JsonObject? = null,
    val networks: Map<String, NetworkStats>? = null,
) {
    fun cpuPercentage(): Double {
        val currentCpu = cpuStats ?: return 0.0
        val prevCpu = preCpuStats ?: return 0.0

        val cpuDelta = (currentCpu.cpuUsage?.totalUsage ?: 0L) - (prevCpu.cpuUsage?.totalUsage ?: 0L)
        val systemDelta = (currentCpu.systemCpuUsage ?: 0L) - (prevCpu.systemCpuUsage ?: 0L)
        val cores = currentCpu.onlineCpus ?: currentCpu.cpuUsage?.percpuUsage?.size?.toLong() ?: 1L

        if (systemDelta > 0L && cpuDelta > 0L) {
            val pct = (cpuDelta.toDouble() / systemDelta.toDouble()) * cores * 100.0
            return (pct * 100.0).roundToInt() / 100.0
        }
        return 0.0
    }

    fun memoryUsageBytes(): Long {
        val mem = memoryStats ?: return 0L
        val usage = mem.usage ?: return 0L

        val statsObj = mem.stats
        val cache = statsObj?.get("inactive_file")?.toString()?.toLongOrNull()
            ?: statsObj?.get("total_inactive_file")?.toString()?.toLongOrNull()
            ?: statsObj?.get("cache")?.toString()?.toLongOrNull()
            ?: 0L

        return (usage - cache).coerceAtLeast(0L)
    }

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
        val limit = memoryStats?.limit ?: return 0.0
        if (limit <= 0L) return 0.0
        val used = memoryUsageBytes()
        val pct = (used.toDouble() / limit.toDouble()) * 100.0
        return (pct * 100.0).roundToInt() / 100.0
    }
}

@Serializable
data class CpuStats(
    @SerialName("cpu_usage")
    val cpuUsage: CpuUsage? = null,
    @SerialName("system_cpu_usage")
    val systemCpuUsage: Long? = null,
    @SerialName("online_cpus")
    val onlineCpus: Long? = null,
    @SerialName("throttling_data")
    val throttlingData: ThrottlingData? = null,
)

@Serializable
data class CpuUsage(
    @SerialName("total_usage")
    val totalUsage: Long? = null,
    @SerialName("usage_in_kernelmode")
    val usageInKernelmode: Long? = null,
    @SerialName("usage_in_usermode")
    val usageInUsermode: Long? = null,
    @SerialName("percpu_usage")
    val percpuUsage: List<Long>? = null,
)

@Serializable
data class ThrottlingData(
    val periods: Long? = null,
    @SerialName("throttled_periods")
    val throttledPeriods: Long? = null,
    @SerialName("throttled_time")
    val throttledTime: Long? = null,
)

@Serializable
data class MemoryStats(
    val usage: Long? = null,
    @SerialName("max_usage")
    val maxUsage: Long? = null,
    val limit: Long? = null,
    val stats: JsonObject? = null,
    @SerialName("failcnt")
    val failcnt: Long? = null,
)

@Serializable
data class PidsStats(
    val current: Long? = null,
    val limit: Long? = null,
)

@Serializable
data class BlkioStats(
    @SerialName("io_service_bytes_recursive")
    val ioServiceBytesRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_serviced_recursive")
    val ioServicedRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_queue_recursive")
    val ioQueueRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_service_time_recursive")
    val ioServiceTimeRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_wait_time_recursive")
    val ioWaitTimeRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_merged_recursive")
    val ioMergedRecursive: List<BlkioStatEntry>? = null,
    @SerialName("io_time_recursive")
    val ioTimeRecursive: List<BlkioStatEntry>? = null,
    @SerialName("sectors_recursive")
    val sectorsRecursive: List<BlkioStatEntry>? = null,
)

@Serializable
data class BlkioStatEntry(
    val major: Long? = null,
    val minor: Long? = null,
    val op: String? = null,
    val value: Long? = null,
)

@Serializable
data class NetworkStats(
    @SerialName("rx_bytes")
    val rxBytes: Long? = null,
    @SerialName("rx_packets")
    val rxPackets: Long? = null,
    @SerialName("rx_errors")
    val rxErrors: Long? = null,
    @SerialName("rx_dropped")
    val rxDropped: Long? = null,
    @SerialName("tx_bytes")
    val txBytes: Long? = null,
    @SerialName("tx_packets")
    val txPackets: Long? = null,
    @SerialName("tx_errors")
    val txErrors: Long? = null,
    @SerialName("tx_dropped")
    val txDropped: Long? = null,
)
