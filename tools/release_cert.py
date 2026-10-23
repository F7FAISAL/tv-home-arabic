#!/usr/bin/env python3
"""Fingerprints of the launcher's release certificate (launcher/release-cert.pem, public; the key itself stays in
~/.tv-home-arabic/release.jks, see CLAUDE.md).

    python3 tools/release_cert.py            # sha256 and the short hash dumpsys package prints
    python3 tools/release_cert.py --sha256   # only the SHA-256 (what apksigner --print-certs shows)
    python3 tools/release_cert.py --dumpsys  # only the hash `dumpsys package` shows as signatures:[........]

The short hash is java.util.Arrays.hashCode of the certificate's DER bytes, which is what Android prints. It is a
32-bit check, enough to tell two keys apart before an install, not a security check.
"""
import base64
import hashlib
import sys
from pathlib import Path

PEM = Path(__file__).resolve().parent.parent / "launcher" / "release-cert.pem"


def der(path: Path = PEM) -> bytes:
    lines = path.read_text().splitlines()
    return base64.b64decode("".join(l for l in lines if l and not l.startswith("-----")))


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dumpsys_hash(data: bytes) -> str:
    h = 1
    for b in data:
        h = (31 * h + (b - 256 if b > 127 else b)) & 0xFFFFFFFF
    return f"{h:x}"


if __name__ == "__main__":
    d = der()
    if "--sha256" in sys.argv[1:]:
        print(sha256(d))
    elif "--dumpsys" in sys.argv[1:]:
        print(dumpsys_hash(d))
    else:
        print(f"sha256  {sha256(d)}\ndumpsys {dumpsys_hash(d)}")
