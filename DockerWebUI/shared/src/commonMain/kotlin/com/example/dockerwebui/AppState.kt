package com.example.dockerwebui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppState {

    var contentToDisplay by mutableStateOf(SelectedMode.CONTAINERS)

    val openShells = mutableStateListOf<String>()
    val toastState = ToastState()
    val containers = mutableStateListOf<ContainerData>()
    val images = mutableStateListOf<ImageData>()
    val volumes = mutableStateListOf<VolumeData>()
    val networks = mutableStateListOf<NetworkData>()

    var imageSearchQuery by mutableStateOf("")
    var isPullingImage by mutableStateOf(false)
    var pullStatusText by mutableStateOf("")
    var pullProgressPercentage by mutableStateOf(0f)
    var showPullSection by mutableStateOf(false)

    var volumeSearchQuery by mutableStateOf("")
    var showCreateVolumeSection by mutableStateOf(true)

    var networkSearchQuery by mutableStateOf("")
    var showCreateNetworkSection by mutableStateOf(true)

    val api = DockerApi()
}
enum class SelectedMode{CONTAINERS , IMAGES , VOLUMES , NETWORKS , COMPONENTSTACKS , TERMINAL , SETTINGS  }
