"""Tests status.py against a fake MinescriptStatus bridge. Run: python -B -m unittest discover tests"""
import pathlib
import sys
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import status  # noqa: E402


class FakeStatus:
    def __init__(self):
        self.calls = []

    def set(self, name, text, color, ttl):
        assert type(color) is int and type(ttl) is float  # Java (String, String, int, double)
        self.calls.append(("set", name, text, color, ttl))

    def clear(self, name):
        self.calls.append(("clear", name))


class StatusTest(unittest.TestCase):
    def setUp(self):
        self.fake = status._Status = FakeStatus()

    def test_set_defaults_and_colors(self):
        status.set("farm", 123)
        status.set("mine", "ok", ttl=5, color=0x55FF55)
        status.set("warn", "low", color=0x80FF0000)
        status.clear("farm")
        self.assertEqual(self.fake.calls, [
            ("set", "farm", "123", -1, 2.0),                       # white, opaque (0xFFFFFFFF as signed int)
            ("set", "mine", "ok", 0xFF55FF55 - (1 << 32), 5.0),
            ("set", "warn", "low", 0x80FF0000 - (1 << 32), 2.0),   # alpha kept, like hud.text
            ("clear", "farm")])

    def test_validation(self):
        for name in ("", None, 3):
            with self.assertRaises(ValueError):
                status.set(name, "x")
        for ttl in (0, -1, None):
            with self.assertRaises(ValueError):
                status.set("farm", "x", ttl=ttl)
        self.assertEqual(self.fake.calls, [])


if __name__ == "__main__":
    unittest.main()
