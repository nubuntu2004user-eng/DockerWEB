package com.example.dockerwebui.Views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun networksCreateSection(
    font: Font,
    appState: AppState,
    scope: CoroutineScope
) {
    var networkName by remember { mutableStateOf("") }
    var networkDriver by remember { mutableStateOf("bridge") }
    var networkSubnet by remember { mutableStateOf("") }
    var networkGateway by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color(20, 34, 52)
        ),
        border = BorderStroke(1.dp, Color(35, 58, 88))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Create a network",
                fontFamily = font.font,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = networkName,
                onValueChange = { networkName = it },
                placeholder = { Text("network name", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp) },
                modifier = Modifier.weight(0.28f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(14, 24, 38),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(14, 24, 38)
                )
            )

            OutlinedTextField(
                value = networkDriver,
                onValueChange = { networkDriver = it },
                placeholder = { Text("driver", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp) },
                modifier = Modifier.weight(0.12f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(14, 24, 38),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(14, 24, 38)
                )
            )

            OutlinedTextField(
                value = networkSubnet,
                onValueChange = { networkSubnet = it },
                placeholder = { Text("subnet, e.g. 172.30.0.0/16", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp) },
                modifier = Modifier.weight(0.30f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(14, 24, 38),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(14, 24, 38)
                )
            )

            OutlinedTextField(
                value = networkGateway,
                onValueChange = { networkGateway = it },
                placeholder = { Text("gateway (optional)", fontFamily = font.font, color = Color(140, 160, 185), fontSize = 13.sp) },
                modifier = Modifier.weight(0.24f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(14, 24, 38),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(14, 24, 38)
                )
            )

            ElevatedButton(
                onClick = {
                    val nameToCreate = networkName.trim()
                    if (nameToCreate.isBlank()) {
                        scope.launch { appState.toastState.showToast("Network name cannot be empty") }
                        return@ElevatedButton
                    }
                    isSubmitting = true
                    scope.launch {
                        try {
                            appState.api.createNetwork(
                                name = nameToCreate,
                                driver = networkDriver.trim().ifBlank { "bridge" },
                                subnet = networkSubnet.trim().ifBlank { null },
                                gateway = networkGateway.trim().ifBlank { null }
                            )
                            appState.toastState.showToast("Network '$nameToCreate' created successfully")
                            networkName = ""
                            networkSubnet = ""
                            networkGateway = ""
                        } catch (e: Exception) {
                            appState.toastState.showToast("Failed to create network: ${e.message}")
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(31, 111, 235),
                    contentColor = Color.White
                )
            ) {
                Text(if (isSubmitting) "Creating..." else "Create", fontFamily = font.font, fontSize = 14.sp)
            }
        }
    }
}
