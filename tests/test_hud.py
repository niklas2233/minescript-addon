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
            ("text", ("t", 1, 2, "5", -1, True, 0.0)),
            ("rect", ("r", 1, 2, 3, 4, hud._argb(0x80000000), 0.0)),
            ("item", ("i", 0, 0, "minecraft:stone", 2, 0.0)),
        ])
        for _, args in calls:
            self.assertFalse(any(type(a) is float for a in args[:-1]))

    def test_ttl_passed_as_float_seconds(self):
        hud.text("t", 0, 0, "x", ttl=2)
        hud.rect("r", 0, 0, 1, 1, ttl=0.5)
        hud.item("i", 0, 0, "minecraft:stone", ttl=None)
        hud.text("t0", 0, 0, "x", ttl=0)
        self.assertEqual([args[-1] for _, args in calls], [2.0, 0.5, 0.0, 0.0])
        self.assertTrue(all(type(args[-1]) is float for _, args in calls))  # Java double overload

    def test_clear_prefix(self):
        hud.clear()
        hud.clear("farm.")
        hud.clear("")
        self.assertEqual(calls, [("clear", ()), ("clear", ("farm.",)), ("clear", ())])

    def test_alpha_keyword(self):
        self.assertEqual(hud._argb(0xFFFFFF, alpha=0), 0x00FFFFFF)      # fully transparent now possible
        self.assertEqual(hud._argb(0xFFFFFF, alpha=255), -1)
        self.assertEqual(hud._argb(0x00FF00, alpha=128), 0x8000FF00 - (1 << 32))
        self.assertEqual(hud._argb(0xFF123456, alpha=0x10), 0x10123456)  # alpha wins over color's own
        self.assertEqual(hud._argb(0x123456, alpha=300), hud._argb(0x123456, alpha=255))
        self.assertEqual(hud._argb(0x123456, alpha=-5), 0x00123456)
        self.assertEqual(hud._argb(0x123456, alpha=127.9), 0x7F123456)
        hud.text("t", 0, 0, "x", 0xFFFFFF, alpha=0)
        hud.rect("r", 0, 0, 1, 1, 0, alpha=255)
        self.assertEqual(calls[0][1][4], 0x00FFFFFF)
        self.assertEqual(calls[1][1][5], 0xFF000000 - (1 << 32))

    def test_colors_stay_in_java_int_range(self):
        for c in (-1, 0, 0xFFFFFF, 0x80000000, 0xFFFFFFFF, 0x1FFFFFFFF):
            for a in (None, 0, 128, 255):
                self.assertTrue(-(1 << 31) <= hud._argb(c, a) < (1 << 31), (c, a))


if __name__ == "__main__":
    unittest.main()
