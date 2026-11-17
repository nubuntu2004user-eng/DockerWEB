import asyncio
import json
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

if "BackendLogic.src.Client" not in sys.modules:
    with patch("docker.from_env", return_value=MagicMock()):
        from BackendLogic.src import Client, main
else:
    from BackendLogic.src import Client, main


class TestUnifiedSnapshotOptimization(unittest.TestCase):
    def setUp(self):
        with Client._prev_cpu_lock:
            Client._prev_cpu_samples.clear()
        Client.invalidate_volume_df_cache()

    def test_single_container_list_and_df_cache_and_slim_fields(self):
        running_container = SimpleNamespace(
            id="run1234567890",
            short_id="run123456789",
            name="api",
            status="running",
            image=SimpleNamespace(id="sha256:abcdef1234567890"),
            attrs={
                "Platform": "linux",
                "Mounts": [{"Name": "data_vol"}],
            },
        )
        stopped_container = SimpleNamespace(
            id="stop1234567890",
            short_id="stop12345678",
            name="worker",
            status="exited",
            image=SimpleNamespace(id="sha256:abcdef1234567890"),
            attrs={"Platform": "linux", "Mounts": []},
        )
        mock_image = SimpleNamespace(
            id="sha256:abcdef1234567890",
            tags=["myrepo/api:v1"],
            attrs={"Size": 123456, "Created": "2026-01-01T00:00:00Z"},
        )
        mock_volume = SimpleNamespace(
            name="data_vol",
            attrs={
                "Driver": "local",
                "Mountpoint": "/var/lib/docker/volumes/data_vol/_data",
                "CreatedAt": "2026-01-01T00:00:00Z",
                "Labels": {"app": "api"},
                "Scope": "local",
            },
        )
        mock_network = SimpleNamespace(
            id="net123",
            name="app-net",
            attrs={
                "Driver": "bridge",
                "Scope": "local",
                "Internal": False,
                "Attachable": True,
                "Ingress": False,
                "Created": "2026-01-01T00:00:00Z",
                "Labels": {"env": "test"},
                "IPAM": {"Config": [{"Subnet": "172.20.0.0/16", "Gateway": "172.20.0.1"}]},
                "Containers": {
                    "run1234567890": {
                        "Name": "api",
                        "IPv4Address": "172.20.0.2/16",
                        "IPv6Address": "::1",
                    }
                },
            },
        )

        mock_client = MagicMock()
        mock_client.containers.list.return_value = [running_container, stopped_container]
        mock_client.images.list.return_value = [mock_image]
        mock_client.volumes.list.return_value = [mock_volume]
        mock_client.networks.list.return_value = [mock_network]
        mock_client.df.return_value = {
            "Volumes": [{"Name": "data_vol", "UsageData": {"Size": 4096}}]
        }

        # Two consecutive one-shot stats payloads for running container
        mock_client.api.stats.side_effect = [
            {
                "os_type": "linux",
                "cpu_stats": {
                    "cpu_usage": {"total_usage": 1000},
                    "system_cpu_usage": 10000,
                    "online_cpus": 2,
                },
                "memory_stats": {
                    "usage": 5000,
                    "limit": 10000,
                    "stats": {"inactive_file": 1000},
                },
            },
            {
                "os_type": "linux",
                "cpu_stats": {
                    "cpu_usage": {"total_usage": 1500},
                    "system_cpu_usage": 12000,
                    "online_cpus": 2,
                },
                "memory_stats": {
                    "usage": 6000,
                    "limit": 10000,
                    "stats": {"inactive_file": 1000},
                },
            },
        ]

        with patch.object(Client, "client", mock_client):
            snap1 = Client.get_unified_snapshot()
            snap2 = Client.get_unified_snapshot()

        # containers.list(all=True) called once per snapshot (2 total, not 8!)
        self.assertEqual(mock_client.containers.list.call_count, 2)
        # client.df() called once and cached for snap2
        self.assertEqual(mock_client.df.call_count, 1)
        # api.stats called once per snapshot ONLY for running container (with one_shot=True)
        self.assertEqual(mock_client.api.stats.call_count, 2)
        mock_client.api.stats.assert_called_with("run1234567890", stream=False, one_shot=True)

        # Verify slimmed container fields and calculations
        self.assertEqual(
            set(snap1["containers"][0].keys()),
            {"id", "name", "status", "os_type", "cpu_percent", "memory_usage", "memory_limit"},
        )
        self.assertEqual(snap1["containers"][0]["cpu_percent"], 0.0)
        self.assertEqual(snap1["containers"][0]["memory_usage"], 4000)
        self.assertEqual(snap2["containers"][0]["cpu_percent"], 50.0)
        self.assertEqual(snap2["containers"][0]["memory_usage"], 5000)
        self.assertEqual(snap2["containers"][1]["cpu_percent"], 0.0)

        # Verify slimmed images, volumes, networks fields
        self.assertEqual(
            set(snap1["images"][0].keys()),
            {"id", "repository", "tag", "size", "created", "dangling", "used_by"},
        )
        self.assertEqual(set(snap1["images"][0]["used_by"][0].keys()), {"name", "status"})

        self.assertEqual(
            set(snap1["volumes"][0].keys()),
            {"name", "driver", "mountpoint", "created", "labels", "size", "anonymous", "used_by"},
        )
        self.assertEqual(snap1["volumes"][0]["size"], 4096)

        self.assertEqual(
            set(snap1["networks"][0].keys()),
            {"id", "name", "driver", "scope", "is_system", "subnet", "gateway", "containers"},
        )
        self.assertEqual(
            set(snap1["networks"][0]["containers"][0].keys()),
            {"id", "name", "status", "ipv4"},
        )


class TestUnifiedSSEBroadcaster(unittest.TestCase):
    def test_broadcaster_emits_full_then_delta_and_stops_when_idle(self):
        snapshots = [
            {
                "containers": [{"id": "c1", "cpu_percent": 1.0}],
                "images": [{"id": "img1"}],
                "volumes": [],
                "networks": [{"id": "n1"}],
            },
            {
                "containers": [{"id": "c1", "cpu_percent": 2.5}],
                "images": [{"id": "img1"}],
                "volumes": [],
                "networks": [{"id": "n1"}],
            },
        ]
        call_idx = {"i": 0}

        def fake_snapshot():
            idx = min(call_idx["i"], len(snapshots) - 1)
            call_idx["i"] += 1
            return snapshots[idx]

        async def run_test():
            b = main.UnifiedSSEBroadcaster(poll_interval=0.05, keepalive_interval=10.0)
            with patch.object(main.client, "get_unified_snapshot", side_effect=fake_snapshot):
                q = b.subscribe()
                first_raw = await asyncio.wait_for(q.get(), timeout=1.0)
                first = json.loads(first_raw)
                self.assertEqual(set(first.keys()), {"containers", "images", "volumes", "networks"})

                second_raw = await asyncio.wait_for(q.get(), timeout=1.0)
                second = json.loads(second_raw)
                self.assertEqual(set(second.keys()), {"containers"})
                self.assertEqual(second["containers"][0]["cpu_percent"], 2.5)

                b.unsubscribe(q)
                await asyncio.sleep(0.05)
                self.assertIsNone(b._task)

        asyncio.run(run_test())

    def test_legacy_sse_routes_removed(self):
        paths = {route.path for route in main.app.routes}
        self.assertIn("/events", paths)
        for legacy in ("/stats", "/imagestats", "/volumestats", "/networkstats"):
            self.assertNotIn(legacy, paths)


if __name__ == "__main__":
    unittest.main()
