"""Regression tests for binding, dynamic ports and preview identity (stdlib only)."""
import json
from pathlib import Path
import socket
import subprocess
import sys
import threading
import unittest
from urllib.request import urlopen

from serve_authoring import APPLICATION, LAB_ASSETS, PreviewHandler, asset_digest, create_server


class PreviewServerTests(unittest.TestCase):
    def test_fantasy_lab_identity_and_kernel(self):
        with create_server(assets=LAB_ASSETS["fantasy"], lab="fantasy") as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                base = f"http://127.0.0.1:{server.server_address[1]}"
                with urlopen(base + "/__cosmic__/health", timeout=5) as response:
                    record = json.load(response)
                    self.assertEqual(record["lab"], "fantasy")
                    self.assertEqual(record["assetDigest"], asset_digest(LAB_ASSETS["fantasy"]))
                    self.assertNotEqual(record["assetDigest"], asset_digest())
                with urlopen(base + "/material.glsl", timeout=5) as response:
                    self.assertIn(b"fantasyMaterial", response.read())
                with urlopen(base + "/cosmic/material.glsl", timeout=5) as response:
                    self.assertEqual(response.read(), (LAB_ASSETS["cosmic"] / "material.glsl").read_bytes())
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
            finally:
                server.shutdown()
                worker.join(timeout=5)

    def test_simultaneous_dynamic_ports(self):
        with create_server() as first, create_server() as second:
            self.assertNotEqual(first.server_address[1], second.server_address[1])
            self.assertEqual(first.server_address[0], "127.0.0.1")
            self.assertEqual(first.socket.getsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR), 0)
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                self.assertEqual(first.socket.getsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE), 1)

    def test_second_listener_rejected(self):
        with create_server() as first:
            with self.assertRaises(OSError):
                second = create_server(first.server_address[1])
                second.server_close()

    @unittest.skipUnless(hasattr(socket, "SO_EXCLUSIVEADDRUSE"), "Windows socket semantics")
    def test_reuseaddr_cannot_take_existing_listener(self):
        with create_server() as first, socket.socket() as intruder:
            intruder.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            with self.assertRaises(OSError):
                intruder.bind(first.server_address)

    def test_explicit_busy_port_exits_without_claiming_success(self):
        with create_server() as first:
            result = subprocess.run([sys.executable, "-X", "utf8", str(Path(__file__).with_name("serve_authoring.py")),
                                     "--port", str(first.server_address[1]), "--json"],
                                    capture_output=True, text=True, encoding="utf-8", timeout=10)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(result.stdout, "")
            self.assertIn("no existing server was stopped", result.stderr)

    def test_health_and_assets(self):
        with create_server() as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                base = f"http://127.0.0.1:{server.server_address[1]}"
                with urlopen(base + "/__cosmic__/health", timeout=5) as response:
                    record = json.load(response)
                    self.assertEqual(record, {"application": APPLICATION, "protocol": 1,
                                              "assetDigest": asset_digest()})
                    self.assertEqual(response.headers["Cache-Control"], "no-store")
                with urlopen(base + "/material.glsl", timeout=5) as response:
                    self.assertIn(b"cosmicMaterial", response.read())
            finally:
                server.shutdown()
                worker.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
