package com.example.dockerwebui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppState {
    val toastState = ToastState()
    var selectedButton by mutableStateOf(1)
    val containers = mutableStateListOf<ContainerData>()
    val api = DockerApi()
}
