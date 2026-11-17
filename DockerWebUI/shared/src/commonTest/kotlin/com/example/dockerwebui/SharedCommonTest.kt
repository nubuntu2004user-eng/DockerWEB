package com.example.dockerwebui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedCommonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun decodesFullUnified2dSsePayload() {
        val raw = """
            {
              "containers": [
                {
                  "id": "c1",
                  "name": "web",
                  "status": "running",
                  "os_type": "linux",
                  "cpu_percent": 12.345,
                  "memory_usage": 104857600,
                  "memory_limit": 1048576000
                }
              ],
              "images": [
                {
                  "id": "sha256:1234567890abcdef1234",
                  "repository": "nginx",
                  "tag": "alpine",
                  "size": 10485760,
                  "created": "2026-01-01T12:00:00Z",
                  "dangling": false,
                  "used_by": [{"name": "web", "status": "running"}]
                }
              ],
              "volumes": [
                {
                  "name": "pgdata",
                  "driver": "local",
                  "mountpoint": "/var/lib/docker/volumes/pgdata/_data",
                  "created": "2026-01-01T12:00:00Z",
                  "labels": {"env": "prod"},
                  "size": 2097152,
                  "anonymous": false,
                  "used_by": [{"name": "web", "status": "running"}]
                }
              ],
              "networks": [
                {
                  "id": "net1",
                  "name": "bridge",
                  "driver": "bridge",
                  "scope": "local",
                  "is_system": true,
                  "subnet": "172.17.0.0/16",
                  "gateway": "172.17.0.1",
                  "containers": [
                    {"id": "c1", "name": "web", "status": "running", "ipv4": "172.17.0.2/16"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val payload = json.decodeFromString<UnifiedSsePayload>(raw)
        val container = assertNotNull(payload.containers).single()
        assertEquals(ContainerStatus.RUNNING, container.containerStatus())
        assertEquals(12.35, container.cpuPercentage())
        assertEquals("100.0 MiB", container.memoryUsageFormatted())
        assertEquals(10.0, container.memoryPercentage())

        val image = assertNotNull(payload.images).single()
        assertEquals("1234567890ab", image.shortId)
        assertTrue(image.isUsed())

        val volume = assertNotNull(payload.volumes).single()
        assertEquals("2.0 MB", volume.formattedSize())
        assertTrue(volume.isUsed())

        val network = assertNotNull(payload.networks).single()
        assertTrue(network.isSystem)
        assertEquals("172.17.0.0/16", network.formattedSubnet())
    }

    @Test
    fun decodesPartialDeltaPayloadLeavingOtherSectionsNull() {
        val raw = """{"containers":[{"id":"c2","name":"db","status":"exited","os_type":"linux","cpu_percent":0.0,"memory_usage":0,"memory_limit":0}]}"""
        val payload = json.decodeFromString<UnifiedSsePayload>(raw)
        val containers = assertNotNull(payload.containers)
        assertEquals(ContainerStatus.STOPPED, containers.single().containerStatus())
        assertNull(payload.images)
        assertNull(payload.volumes)
        assertNull(payload.networks)
    }
}