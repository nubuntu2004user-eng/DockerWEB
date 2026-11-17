package com.example.dockerwebui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AuthStatus { CHECKING, UNAUTHENTICATED, AUTHENTICATED }

class AppState {

    var authStatus by mutableStateOf(AuthStatus.CHECKING)
    var authenticatedUser by mutableStateOf<String?>(null)
    var loginError by mutableStateOf<String?>(null)
    var isLoggingIn by mutableStateOf(false)

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

    var api = DockerApi()

    fun clearSessionData() {
        containers.clear()
        images.clear()
        volumes.clear()
        networks.clear()
        openShells.clear()
    }
}
enum class SelectedMode{CONTAINERS , IMAGES , VOLUMES , NETWORKS , COMPONENTSTACKS , TERMINAL , SETTINGS  }
