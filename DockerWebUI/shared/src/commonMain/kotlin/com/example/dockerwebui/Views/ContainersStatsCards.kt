package com.example.dockerwebui.Views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.ContainerStatus
import com.example.dockerwebui.Font
import kotlin.math.roundToInt

@Composable
fun containersStatsCards(font: Font, appState: AppState) {
    val secondaryColor = Color(50, 74, 110)

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.25f).padding(4.dp)) {
        val runningContainers = appState.containers.filter { it.containerStatus() == ContainerStatus.RUNNING }

        ElevatedCard(
            modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val runningCount = runningContainers.size
            Text("Running\n$runningCount", color = font.fontColor, fontFamily = font.font, fontSize = 48.sp, modifier = Modifier.padding(8.dp))
        }

        ElevatedCard(
            modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val stoppedCount = appState.containers.count { it.containerStatus() == ContainerStatus.STOPPED }
            Text("Stopped\n$stoppedCount", color = font.fontColor, fontFamily = font.font, fontSize = 48.sp, modifier = Modifier.padding(8.dp))
        }

        ElevatedCard(
            modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val totalCpu = runningContainers.sumOf { it.cpuPercentage() }
            val totalCpuRounded = (totalCpu * 10.0).roundToInt() / 10.0
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Host CPU", color = font.fontColor, fontFamily = font.font, fontSize = 20.sp)
                Text("$totalCpuRounded%", color = font.fontColor, fontFamily = font.font, fontSize = 36.sp)
            }
        }

        ElevatedCard(
            modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val totalUsedBytes = runningContainers.sumOf { it.memoryUsageBytes() }
            val hostLimitBytes = appState.containers.maxOfOrNull { it.memoryLimit } ?: 0L
            val ramPercentage = if (hostLimitBytes > 0L) {
                ((totalUsedBytes.toDouble() / hostLimitBytes.toDouble()) * 1000.0).roundToInt() / 10.0
            } else 0.0

            fun formatBytes(bytes: Long): String {
                val mib = bytes.toDouble() / (1024.0 * 1024.0)
                return if (mib >= 1024.0) {
                    val gib = mib / 1024.0
                    "${(gib * 10.0).roundToInt() / 10.0} GiB"
                } else {
                    "${(mib * 10.0).roundToInt() / 10.0} MiB"
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text("Host RAM", color = font.fontColor, fontFamily = font.font, fontSize = 20.sp)
                Text("$ramPercentage%", color = font.fontColor, fontFamily = font.font, fontSize = 36.sp)
                if (hostLimitBytes > 0L) {
                    Text(
                        "${formatBytes(totalUsedBytes)} / ${formatBytes(hostLimitBytes)}",
                        color = Color.LightGray,
                        fontFamily = font.font,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
