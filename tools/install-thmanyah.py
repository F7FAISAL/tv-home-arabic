#!/usr/bin/env python3
"""Put your own copy of the Thmanyah font (خط ثمانية) into your build of the launcher.

The font is free to use but its licence forbids redistributing, renaming or modifying it, so it is not in this
repository. Download it yourself from Thmanyah's official font page (you get a .zip by email), then:

  python3 tools/install-thmanyah.py ~/Downloads/Thmanyah-Font-Family.zip [themes/madinah]

The script takes thmanyahsans-Regular.otf and thmanyahsans-Bold.otf out of the .zip (or a folder) and copies them
byte for byte, under their own names, into launcher/fonts-local/thmanyah/ (gitignored, never committed). It then
writes "font": "thmanyah" into the theme's config.local.json (also gitignored). Rebuild and reinstall the launcher
and push the theme afterwards; without these files the launcher uses IBM Plex Sans Arabic.
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "launcher" / "fonts-local" / "thmanyah"
NAMES = ("thmanyahsans-Regular.otf", "thmanyahsans-Bold.otf")


def from_zip(path: Path) -> dict:
    found = {}
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            name = info.filename.rsplit("/", 1)[-1]
            # macOS archives carry ._ resource forks under __MACOSX/: not fonts.
            if name in NAMES and "__MACOSX" not in info.filename and not info.is_dir():
                found[name] = z.read(info)
    return found


def from_folder(path: Path) -> dict:
    found = {}
    for name in NAMES:
        for p in path.rglob(name):
            if "__MACOSX" not in p.parts:
                found[name] = p.read_bytes()
                break
    return found


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 2
    source = Path(argv[0]).expanduser()
    theme = ROOT / (argv[1] if len(argv) > 1 else "themes/madinah")
    if not source.exists():
        sys.exit(f"{source} not found")
    found = from_zip(source) if source.is_file() else from_folder(source)
    missing = [n for n in NAMES if n not in found]
    if missing:
        sys.exit("not found in " + str(source) + ": " + ", ".join(missing))
    for name, data in found.items():
        if not data.startswith((b"OTTO", b"\x00\x01\x00\x00")):
            sys.exit(f"{name} is not an OpenType font")
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, data in found.items():
        (TARGET / name).write_bytes(data)   # unchanged bytes, unchanged name: the licence allows nothing else
        print(f"copied {name} ({len(data) // 1024} KB) -> {TARGET.relative_to(ROOT)}/")
    if not (theme / "config.json").is_file():
        sys.exit(f"{theme} has no config.json; the font is in place, set \"font\": \"thmanyah\" yourself")
    local = theme / "config.local.json"
    config = json.loads(local.read_text(encoding="utf-8")) if local.is_file() else {}
    config["font"] = "thmanyah"
    local.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f'"font": "thmanyah" set in {local.relative_to(ROOT)}')
    print("Next: rebuild and install the launcher, then push the theme (README.md, step 5).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
