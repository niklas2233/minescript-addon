"""Tests hud.py's Python->Java argument handling with a stub `java` module. Run: python -B -m unittest discover tests"""
import pathlib
import sys
import types
import unittest

calls = []


class _StubHud:
    def __getattr__(self, name):
        return lambda *args: calls.append((name, args)) or 0


sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: _StubHud())
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import hud  # noqa: E402


class HudTest(unittest.TestCase):
    def setUp(self):
        calls.clear()

    def test_rgb_becomes_opaque_signed_int(self):
        self.assertEqual(hud._argb(0xFFFFFF), -1)
        self.assertEqual(hud._argb(0x000000), 0xFF000000 - (1 << 32))

    def test_argb_kept_and_fits_java_int(self):
        self.assertEqual(hud._argb(0x7F00FF00), 0x7F00FF00)
        self.assertEqual(hud._argb(0x80000000), -(1 << 31))
        for c in (0, 0xFFFFFF, 0x80000000, 0xFFFFFFFF):
            self.assertTrue(-(1 << 31) <= hud._argb(c) < (1 << 31))

    def test_floats_coerced_to_int(self):
        hud.text("t", 1.7, 2.2, 5)
        hud.rect("r", 1.0, 2.0, 3.9, 4.1)
        hud.item("i", 0.5, 0.5, "minecraft:stone", 2.0)
        self.assertEqual(calls, [
            ("text", ("t", 1, 2, "5", -1, True)),
            ("rect", ("r", 1, 2, 3, 4, hud._argb(0x80000000))),
            ("item", ("i", 0, 0, "minecraft:stone", 2)),
        ])
        for _, args in calls:
            self.assertFalse(any(type(a) is float for a in args))


if __name__ == "__main__":
    unittest.main()
