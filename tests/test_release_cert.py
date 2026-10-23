"""tools/release_cert.py: the release certificate's fingerprints, as apksigner and `dumpsys package` show them."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import release_cert  # noqa: E402


class ReleaseCertTest(unittest.TestCase):
    def test_matches_what_the_tvs_report(self):
        # Read from a TCL G10 on 2026-10-24: apksigner on its installed APK, and dumpsys package's signatures:[...].
        d = release_cert.der()
        self.assertEqual(release_cert.sha256(d), "a192353f41b6f6899d53ce823a6f6cfb4cfe327b1582cb010727c44f80e07356")
        self.assertEqual(release_cert.dumpsys_hash(d), "f551a09d")

    def test_java_hash_of_signed_bytes(self):
        # java.util.Arrays.hashCode(new byte[]{1, -1}) == (31 * (31 + 1)) - 1 == 991
        self.assertEqual(release_cert.dumpsys_hash(bytes([1, 255])), f"{991:x}")

    def test_cli(self):
        out = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "release_cert.py"), "--dumpsys"],
                             capture_output=True, text=True, stdin=subprocess.DEVNULL, check=True).stdout.strip()
        self.assertEqual(out, "f551a09d")

    def test_only_the_certificate_is_in_the_repository(self):
        pem = (ROOT / "launcher" / "release-cert.pem").read_text()
        self.assertIn("BEGIN CERTIFICATE", pem)
        self.assertNotIn("PRIVATE KEY", pem)


if __name__ == "__main__":
    unittest.main()
