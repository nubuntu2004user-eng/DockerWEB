package com.example.dockerwebui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.dockerwebui.Views.leftPanel
import com.example.dockerwebui.Views.rightPanelContainers

@Composable
@Preview
fun App() {
    val state = AppState()
    val font = Font()
    Row(modifier = Modifier.fillMaxSize().background(color = Color(11, 18, 27))){
        leftPanel(0.15f , appState = state, font = font)
        rightPanelContainers(1f , font = font)
    }
}