"""Tests settings.py against a temp directory. Run: python -B -m unittest discover tests"""
import contextlib
import io
import json
import os
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import settings  # noqa: E402

DEFAULTS = {"radius": 8, "speed": 1.5, "name": "farm", "sneak": True, "crops": ["wheat", "carrots"], "ys": [1.0]}


class SettingsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        patcher = mock.patch.object(settings, "DIR", os.path.join(self.tmp.name, "minescriptaddon-settings"))
        patcher.start()
        self.addCleanup(patcher.stop)
        self.path = os.path.join(settings.DIR, "farm.json")

    def file(self):
        with open(self.path, encoding="utf-8") as f:
            return json.load(f)

    def write(self, text):
        os.makedirs(settings.DIR, exist_ok=True)
        with open(self.path, "w", encoding="utf-8") as f:
            f.write(text)

    def test_first_load_creates_file_from_defaults(self):
        s = settings.load("farm", DEFAULTS)
        self.assertEqual(self.file(), DEFAULTS)
        self.assertEqual(s.as_dict(), DEFAULTS)
        self.assertEqual((s["radius"], s.get("speed"), s.get("nope", 3), "sneak" in s, "nope" in s), (8, 1.5, 3, True, False))
        self.assertFalse(s.changed())

    def test_missing_keys_get_defaults_and_extra_keys_are_kept(self):
        self.write('{"radius": 20, "extra": {"anything": [1, 2]}}')
        s = settings.load("farm", DEFAULTS)
        self.assertEqual((s["radius"], s["speed"], s["extra"]), (20, 1.5, {"anything": [1, 2]}))
        self.assertEqual(self.file(), {"radius": 20, "extra": {"anything": [1, 2]}, **{k: v for k, v in DEFAULTS.items() if k != "radius"}})

    def test_set_saves_atomically_at_once(self):
        s = settings.load("farm", DEFAULTS)
        with mock.patch.object(settings.os, "replace", wraps=os.replace) as replace:
            s["radius"] = 12
            s["speed"] = 2          # int accepted for a float, stored as float
            s["crops"] = ["beets"]
            s["new"] = "é"
        self.assertEqual(replace.call_count, 4)
        self.assertTrue(all(c.args[1] == self.path and c.args[0].endswith(".tmp") for c in replace.call_args_list))
        self.assertEqual(os.listdir(settings.DIR), ["farm.json"])  # no temp files left
        self.assertEqual(self.file(), {**DEFAULTS, "radius": 12, "speed": 2.0, "crops": ["beets"], "new": "é"})
        self.assertIs(type(s["speed"]), float)
        self.assertFalse(s.changed())

    def test_failed_write_leaves_file_and_no_temp(self):
        s = settings.load("farm", DEFAULTS)
        with mock.patch.object(settings.os, "replace", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                s["radius"] = 1
        self.assertEqual(os.listdir(settings.DIR), ["farm.json"])
        self.assertEqual(self.file()["radius"], 8)

    def test_type_checks(self):
        s = settings.load("farm", DEFAULTS)
        for key, value in [("radius", "8"), ("radius", 8.5), ("radius", True), ("sneak", 1), ("name", 3),
                           ("crops", "wheat"), ("crops", [1]), ("ys", ["a"]), ("speed", None),
                           ("new", {"a": 1}), ("new", (1, 2)), ("new", [[1]]), ("new", None)]:
            with self.assertRaises(TypeError, msg=(key, value)):
                s[key] = value
        with self.assertRaises(ValueError):
            s["speed"] = float("nan")
        with self.assertRaises(TypeError):
            settings.load("other", {"bad": {"nested": 1}})
        for name in ("", "../x", "a/b", ".hidden", None):
            with self.assertRaises(ValueError, msg=name):
                settings.load(name, {})
        self.assertEqual(self.file(), DEFAULTS)

    def test_wrong_type_in_file_uses_default_and_leaves_file(self):
        self.write('{"radius": "big"}')
        with contextlib.redirect_stderr(io.StringIO()) as err:
            s = settings.load("farm", DEFAULTS)
        self.assertEqual(s["radius"], 8)
        self.assertIn("radius", err.getvalue())
        self.assertEqual(self.file(), {"radius": "big"})

    def test_broken_file_gives_defaults_and_is_left_as_is(self):
        self.write("{not json")
        with contextlib.redirect_stderr(io.StringIO()) as err:
            s = settings.load("farm", DEFAULTS)
        self.assertEqual(s.as_dict(), DEFAULTS)
        self.assertIn("broken", err.getvalue())
        with open(self.path, encoding="utf-8") as f:
            self.assertEqual(f.read(), "{not json")
        self.assertFalse(s.changed())

    def test_changed_and_reload(self):
        s = settings.load("farm", DEFAULTS)
        data = self.file()
        data["radius"] = 30
        self.write(json.dumps(data) + "\n\n")  # a different size, whatever the clock resolution
        self.assertTrue(s.changed())
        self.assertEqual(s["radius"], 8)
        s.reload()
        self.assertEqual(s["radius"], 30)
        self.assertFalse(s.changed())

    def test_returned_lists_are_copies(self):
        s = settings.load("farm", DEFAULTS)
        s["crops"].append("x")
        self.assertEqual(s["crops"], ["wheat", "carrots"])


if __name__ == "__main__":
    unittest.main()
