package com.example.dockerwebui

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform