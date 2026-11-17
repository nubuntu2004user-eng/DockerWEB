package com.example.dockerwebui

import io.ktor.client.HttpClient

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun createAppHttpClient(configureSse: Boolean = true): HttpClient