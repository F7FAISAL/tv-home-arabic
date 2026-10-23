#!/usr/bin/env python3
"""The online wallpaper gallery: wallpapers/ plus wallpapers.json at the root of the repository.

Every TV running the launcher reads wallpapers.json from raw.githubusercontent.com (the theme's
"wallpaper_gallery_url") at boot and every six hours, downloads the photos it does not have yet and
deletes the ones that left the list. No key, no account: the files are public in the repository.

  python3 tools/gallery.py add ~/Pictures/photo.jpg [more.jpg ...]   # resize into wallpapers/, update the list
  python3 tools/gallery.py index                                     # rebuild wallpapers.json from wallpapers/

Then commit and push wallpapers/ and wallpapers.json. Photos are cropped to 1920x1080, saved as JPEG under
500 KB, and stripped of all metadata (camera, GPS, author fields). Needs Pillow:
tools/venv/bin/pip install Pillow
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDER = ROOT / "wallpapers"
INDEX = ROOT / "wallpapers.json"
WIDTH, HEIGHT, MAX_BYTES = 1920, 1080, 500 * 1024
NAME = re.compile(r"^[A-Za-z0-9_.-]+\.jpg$")


def resize(src: Path, out: Path) -> int:
    from PIL import Image, ImageOps

    with Image.open(src) as im:
        im = ImageOps.exif_transpose(im).convert("RGB")
        im = ImageOps.fit(im, (WIDTH, HEIGHT), Image.LANCZOS, centering=(0.5, 0.5))
        for quality in range(90, 40, -4):
            # A fresh image carries no EXIF, ICC or XMP: nothing about the camera, place or author.
            clean = Image.new("RGB", im.size)
            clean.putdata(list(im.getdata()))
            clean.save(out, "JPEG", quality=quality, optimize=True, progressive=True)
            if out.stat().st_size <= MAX_BYTES:
                return quality
    raise SystemExit(f"{src}: could not get it under 500 KB")


def next_name() -> str:
    taken = {p.stem for p in FOLDER.glob("*.jpg")}
    n = 1
    while f"{n:02d}" in taken:
        n += 1
    return f"{n:02d}.jpg"


def index() -> None:
    files = []
    for p in sorted(FOLDER.glob("*")):
        if not p.is_file() or p.name.startswith("."):
            continue
        if not NAME.match(p.name):
            raise SystemExit(f"{p.name}: use letters, digits, dot, dash or underscore, ending in .jpg")
        data = p.read_bytes()
        if len(data) > MAX_BYTES:
            raise SystemExit(f"{p.name} is over 500 KB: run tools/gallery.py add on the original instead")
        files.append({"file": f"wallpapers/{p.name}", "sha": hashlib.sha256(data).hexdigest()[:16],
                      "bytes": len(data)})
    INDEX.write_text(json.dumps({"version": 1, "files": files}, indent=2) + "\n", encoding="utf-8")
    print(f"wallpapers.json: {len(files)} photos")


def main(argv):
    if len(argv) >= 2 and argv[0] == "add":
        FOLDER.mkdir(exist_ok=True)
        for arg in argv[1:]:
            out = FOLDER / next_name()
            q = resize(Path(arg).expanduser(), out)
            print(f"{arg} -> wallpapers/{out.name} ({out.stat().st_size // 1024} KB, quality {q})")
        index()
    elif argv == ["index"]:
        index()
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
