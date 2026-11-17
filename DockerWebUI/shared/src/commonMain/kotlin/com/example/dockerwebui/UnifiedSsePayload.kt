package com.example.dockerwebui

import kotlinx.serialization.Serializable

/**
 * Unified 2D SSE event payload partitioned by UI domain.
 * Fields omitted on delta ticks remain null so unchanged UI lists are preserved.
 */
@Serializable
data class UnifiedSsePayload(
    val containers: List<ContainerData>? = null,
    val images: List<ImageData>? = null,
    val volumes: List<VolumeData>? = null,
    val networks: List<NetworkData>? = null
)
