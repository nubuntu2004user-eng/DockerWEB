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
import com.example.dockerwebui.Font
import kotlin.math.roundToInt

@Composable
fun volumesStatsCards(font: Font, appState: AppState) {
    val secondaryColor = Color(50, 74, 110)

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.22f).padding(4.dp)) {
        val allVolumes = appState.volumes

        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Total volumes", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("${allVolumes.size}", color = font.fontColor, fontFamily = font.font, fontSize = 40.sp)
            }
        }

        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val inUseCount = allVolumes.count { it.isUsed() }
            Column(modifier = Modifier.padding(12.dp)) {
                Text("In use", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("$inUseCount", color = Color(40, 167, 69), fontFamily = font.font, fontSize = 40.sp)
            }
        }

        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val unusedCount = allVolumes.count { !it.isUsed() }
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Unused", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("$unusedCount", color = if (unusedCount > 0) Color(255, 180, 0) else font.fontColor, fontFamily = font.font, fontSize = 40.sp)
            }
        }

        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val totalBytes = allVolumes.sumOf { it.size }
            fun formatBytes(bytes: Long): String {
                val mib = bytes.toDouble() / (1024.0 * 1024.0)
                return if (mib >= 1024.0) {
                    val gib = mib / 1024.0
                    "${(gib * 10.0).roundToInt() / 10.0} GB"
                } else if (mib >= 1.0) {
                    "${(mib * 10.0).roundToInt() / 10.0} MB"
                } else if (bytes > 0) {
                    "${bytes / 1024} KB"
                } else {
                    "0 B"
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text("Disk used", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text(formatBytes(totalBytes), color = font.fontColor, fontFamily = font.font, fontSize = 40.sp)
            }
        }
    }
}
