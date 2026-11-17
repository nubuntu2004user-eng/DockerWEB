import asyncio
import json
import sys
import unittest
from http.cookies import SimpleCookie
from unittest.mock import MagicMock, patch

if "BackendLogic.src.Client" not in sys.modules:
    with patch("docker.from_env", return_value=MagicMock()):
        from BackendLogic.src import main
else:
    from BackendLogic.src import main


async def _asgi_request(
    method: str,
    path: str,
    body: bytes = b"",
    cookies: dict[str, str] | None = None,
    headers: list[tuple[bytes, bytes]] | None = None,
) -> tuple[int, dict[str, str], list[str], bytes]:
    req_headers: list[tuple[bytes, bytes]] = list(headers or [])
    if body:
        req_headers.append((b"content-type", b"application/json"))
    if cookies:
        cookie_str = "; ".join(f"{k}={v}" for k, v in cookies.items())
        req_headers.append((b"cookie", cookie_str.encode("latin-1")))

    scope = {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": method.upper(),
        "scheme": "http",
        "path": path,
        "raw_path": path.encode("ascii"),
        "query_string": b"",
        "headers": req_headers,
        "client": ("127.0.0.1", 12345),
        "server": ("127.0.0.1", 9456),
    }

    sent_body = False

    async def receive():
        nonlocal sent_body
        if not sent_body:
            sent_body = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    status_code = 500
    resp_headers: dict[str, str] = {}
    set_cookies: list[str] = []
    chunks: list[bytes] = []

    async def send(message):
        nonlocal status_code
        if message["type"] == "http.response.start":
            status_code = message["status"]
            for k, v in message.get("headers", []):
                key = k.decode("latin-1").lower()
                val = v.decode("latin-1")
                if key == "set-cookie":
                    set_cookies.append(val)
                resp_headers[key] = val
        elif message["type"] == "http.response.body":
            chunks.append(message.get("body", b""))

    await main.app(scope, receive, send)
    return status_code, resp_headers, set_cookies, b"".join(chunks)


def _request(method: str, path: str, json_body: dict | None = None, cookies: dict[str, str] | None = None):
    raw = json.dumps(json_body).encode("utf-8") if json_body is not None else b""
    return asyncio.run(_asgi_request(method, path, body=raw, cookies=cookies))


class TestAuthentication(unittest.TestCase):
    def test_config_endpoint_strips_credentials(self):
        with patch.object(main, "_refresh_config_if_changed", return_value={
            "host": "0.0.0.0",
            "port": 9456,
            "user": "admin",
            "password": "super-secret-password",
        }):
            status, _, _, body = _request("GET", "/config.json")
        self.assertEqual(status, 200)
        payload = json.loads(body.decode("utf-8"))
        self.assertEqual(payload, {"host": "0.0.0.0", "port": 9456})
        self.assertNotIn("user", payload)
        self.assertNotIn("username", payload)
        self.assertNotIn("password", payload)

    def test_auth_status_unauthenticated_then_authenticated(self):
        status, _, _, body = _request("GET", "/auth/status")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), {"authenticated": False})

        with patch.object(main, "_get_auth_credentials", return_value=("admin", "admin")):
            login_status, _, set_cookies, login_body = _request(
                "POST",
                "/auth/login",
                json_body={"user": "admin", "password": "admin"},
            )
        self.assertEqual(login_status, 200)
        self.assertEqual(json.loads(login_body), {"authenticated": True, "user": "admin"})
        self.assertEqual(len(set_cookies), 1)

        cookie_header = set_cookies[0]
        lower_cookie = cookie_header.lower()
        self.assertIn("httponly", lower_cookie)
        self.assertIn("samesite=lax", lower_cookie)
        self.assertIn("path=/", lower_cookie)
        # Must be a browser session cookie (no Max-Age or Expires)
        self.assertNotIn("max-age=", lower_cookie)
        self.assertNotIn("expires=", lower_cookie)

        parsed = SimpleCookie()
        parsed.load(cookie_header)
        token = parsed[main.SESSION_COOKIE_NAME].value

        status_ok, _, _, body_ok = _request(
            "GET",
            "/auth/status",
            cookies={main.SESSION_COOKIE_NAME: token},
        )
        self.assertEqual(status_ok, 200)
        self.assertEqual(json.loads(body_ok), {"authenticated": True, "user": "admin"})

    def test_invalid_login_returns_401_without_cookie(self):
        with patch.object(main, "_get_auth_credentials", return_value=("admin", "admin")):
            status, _, set_cookies, _ = _request(
                "POST",
                "/auth/login",
                json_body={"user": "admin", "password": "wrong"},
            )
        self.assertEqual(status, 401)
        self.assertEqual(set_cookies, [])

    def test_protected_routes_require_valid_session_cookie(self):
        for method, path in [
            ("GET", "/events"),
            ("GET", "/inspectcontainer?id=abc"),
            ("POST", "/stopcontainer?id=abc"),
            ("GET", "/shellhost?cmd=ls"),
        ]:
            clean_path = path.split("?", 1)[0]
            status, _, _, _ = _request(method, clean_path)
            self.assertEqual(status, 401, f"Expected 401 for unauthenticated {method} {clean_path}")

        # Tampered cookie is rejected
        bad_status, _, _, _ = _request(
            "POST",
            "/prunecontainers",
            cookies={main.SESSION_COOKIE_NAME: "tampered.token"},
        )
        self.assertEqual(bad_status, 401)

        # Valid session cookie allows access to protected endpoint
        with patch.object(main, "_get_auth_credentials", return_value=("admin", "admin")):
            _, _, set_cookies, _ = _request(
                "POST",
                "/auth/login",
                json_body={"user": "admin", "password": "admin"},
            )
        parsed = SimpleCookie()
        parsed.load(set_cookies[0])
        token = parsed[main.SESSION_COOKIE_NAME].value

        with patch.object(main.client, "prune_containers", return_value={"ContainersDeleted": []}):
            ok_status, _, _, ok_body = _request(
                "POST",
                "/prunecontainers",
                cookies={main.SESSION_COOKIE_NAME: token},
            )
        self.assertEqual(ok_status, 200)
        self.assertEqual(json.loads(ok_body), {"ContainersDeleted": []})

    def test_server_restart_new_secret_invalidates_cookie_and_logout_clears_cookie(self):
        with patch.object(main, "_get_auth_credentials", return_value=("admin", "admin")):
            _, _, set_cookies, _ = _request(
                "POST",
                "/auth/login",
                json_body={"user": "admin", "password": "admin"},
            )
        parsed = SimpleCookie()
        parsed.load(set_cookies[0])
        token = parsed[main.SESSION_COOKIE_NAME].value

        # Simulate next server session with a different signing secret
        with patch.object(main, "_SESSION_SECRET", b"different-secret-after-restart!!"):
            status_after_restart, _, _, body_after_restart = _request(
                "GET",
                "/auth/status",
                cookies={main.SESSION_COOKIE_NAME: token},
            )
        self.assertEqual(status_after_restart, 200)
        self.assertEqual(json.loads(body_after_restart), {"authenticated": False})

        # Logout clears cookie
        logout_status, _, logout_cookies, logout_body = _request(
            "POST",
            "/auth/logout",
            cookies={main.SESSION_COOKIE_NAME: token},
        )
        self.assertEqual(logout_status, 200)
        self.assertEqual(json.loads(logout_body), {"authenticated": False})
        self.assertTrue(any("max-age=0" in c.lower() for c in logout_cookies))

    def test_all_operational_routes_require_auth_dependency(self):
        from fastapi.routing import APIRoute

        public_paths = {
            "/",
            "/config.json",
            "/auth/status",
            "/auth/login",
            "/auth/logout",
            "/openapi.json",
            "/docs",
            "/docs/oauth2-redirect",
            "/redoc",
        }
        for route in main.app.routes:
            if isinstance(route, APIRoute) and route.path not in public_paths:
                dep_calls = [d.dependency for d in route.dependencies]
                self.assertIn(
                    main.require_auth,
                    dep_calls,
                    f"Route {route.path} is missing require_auth dependency",
                )


if __name__ == "__main__":
    unittest.main()
