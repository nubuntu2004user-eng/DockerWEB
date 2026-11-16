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

@Composable
fun networksStatsCards(font: Font, appState: AppState) {
    val secondaryColor = Color(50, 74, 110)

    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.22f).padding(4.dp)) {
        val allNetworks = appState.networks

        // Total networks
        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Total networks", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("${allNetworks.size}", color = font.fontColor, fontFamily = font.font, fontSize = 40.sp)
            }
        }

        // User-defined
        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val userDefinedCount = allNetworks.count { !it.isSystem }
            Column(modifier = Modifier.padding(12.dp)) {
                Text("User-defined", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("$userDefinedCount", color = Color(88, 166, 255), fontFamily = font.font, fontSize = 40.sp)
            }
        }

        // Containers attached
        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val uniqueContainersAttached = allNetworks.flatMap { it.containers.map { c -> c.id.ifBlank { c.name } } }.distinct().size
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Containers attached", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("$uniqueContainersAttached", color = Color(40, 167, 69), fontFamily = font.font, fontSize = 40.sp)
            }
        }

        // Unused
        ElevatedCard(
            modifier = Modifier.weight(0.25f).fillMaxHeight().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = secondaryColor)
        ) {
            val unusedCount = allNetworks.count { !it.isSystem && it.containers.isEmpty() }
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Unused", color = font.fontColor, fontFamily = font.font, fontSize = 18.sp)
                Text("$unusedCount", color = if (unusedCount > 0) Color(255, 180, 0) else font.fontColor, fontFamily = font.font, fontSize = 40.sp)
            }
        }
    }
}
