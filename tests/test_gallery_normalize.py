"""tools/gallery.py normalize: what the Wallpapers workflow runs after a photo is uploaded to wallpapers/ on GitHub.

Run from the repository root: python3 -m unittest discover -s tests -v
Needs Pillow (tools/venv/bin/pip install Pillow); skipped without it.
"""
from pathlib import Path
import importlib.util
import json
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

try:
    from PIL import Image
except ImportError:
    Image = None


def load_gallery(root: Path):
    spec = importlib.util.spec_from_file_location("gallery_under_test", ROOT / "tools" / "gallery.py")
    gallery = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gallery)
    gallery.FOLDER = root / "wallpapers"
    gallery.INDEX = root / "wallpapers.json"
    return gallery


@unittest.skipIf(Image is None, "Pillow is not installed")
class GalleryNormalizeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "wallpapers").mkdir()
        self.gallery = load_gallery(self.root)

    def tearDown(self):
        self.tmp.cleanup()

    def photo(self, name, size=(4032, 3024), fmt="JPEG", **save):
        path = self.root / "wallpapers" / name
        Image.new("RGB", size, (40, 80, 120)).save(path, fmt, **save)
        return path

    def test_upload_is_resized_numbered_and_listed(self):
        clean = self.root / "wallpapers" / "01.jpg"
        self.gallery.resize(self.photo("seed.png", fmt="PNG"), clean)
        (self.root / "wallpapers" / "seed.png").unlink()
        before = clean.read_bytes()
        exif = Image.Exif()
        exif[0x010F] = "PhoneMaker"   # Make
        self.photo("IMG 1234.JPG", exif=exif.tobytes())

        self.assertEqual(self.gallery.main(["normalize"]), 0)

        names = sorted(p.name for p in (self.root / "wallpapers").iterdir())
        self.assertEqual(names, ["01.jpg", "02.jpg"])
        self.assertEqual(clean.read_bytes(), before, "a clean photo must stay byte for byte")
        new = self.root / "wallpapers" / "02.jpg"
        self.assertTrue(self.gallery.is_clean(new))
        with Image.open(new) as im:
            self.assertEqual(im.size, (1920, 1080))
            self.assertNotIn("exif", im.info)
        listed = json.loads((self.root / "wallpapers.json").read_text())["files"]
        self.assertEqual([f["file"] for f in listed], ["wallpapers/01.jpg", "wallpapers/02.jpg"])

    def test_right_size_with_metadata_is_redone(self):
        exif = Image.Exif()
        exif[0x010F] = "PhoneMaker"
        path = self.photo("photo.jpg", size=(1920, 1080), exif=exif.tobytes())
        self.assertFalse(self.gallery.is_clean(path))
        self.gallery.normalize()
        self.assertEqual([p.name for p in (self.root / "wallpapers").iterdir()], ["01.jpg"])

    def test_not_a_photo_stops_with_its_name(self):
        (self.root / "wallpapers" / "notes.txt").write_text("hello")
        with self.assertRaises(SystemExit) as stop:
            self.gallery.normalize()
        self.assertIn("notes.txt", str(stop.exception))
        self.assertFalse((self.root / "wallpapers" / ".normalize.jpg").exists())

    def test_repository_photos_are_already_clean(self):
        gallery = load_gallery(ROOT)
        unclean = [p.name for p in sorted(gallery.FOLDER.glob("*.jpg")) if not gallery.is_clean(p)]
        self.assertEqual(unclean, [], "the workflow would re-encode these and every TV would download them again")


if __name__ == "__main__":
    unittest.main()
