"""Tests screens.py against a fake MinescriptScreens bridge. Run: python -B -m unittest discover tests"""
import json
import pathlib
import sys
import time
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import screens  # noqa: E402

SWORD = {"slot": 0, "id": "minecraft:diamond_sword", "count": 1, "name": "Diamond Sword",
         "lore": ["Price: 5 coins", "Click to buy"], "custom_data": {"shop_id": "sword"}, "player": False}
CHEAP = {"slot": 4, "id": "minecraft:stone_sword", "count": 1, "name": "Stone Sword",
         "lore": ["Price: 1 coin"], "custom_data": None, "player": False}
MY_SWORD = {"slot": 31, "id": "minecraft:diamond_sword", "count": 1, "name": "Diamond Sword", "lore": [],
            "custom_data": None, "player": True}


class FakeScreens:
    """Mimics MinescriptScreens: ASCII-only JSON strings; state() follows a script of states."""

    def __init__(self):
        self.calls, self.states, self.slots = [], [], [SWORD, CHEAP, MY_SWORD]

    def title(self):
        return r'"Café Shop"'

    def state(self):
        s = self.states.pop(0) if len(self.states) > 1 else self.states[0]
        return json.dumps(s)

    def items(self):
        return json.dumps(self.slots)

    def click(self, slot, button):
        assert type(slot) is int and type(button) is str  # Java (int, String)
        self.calls.append(("click", slot, button))
        return True

    def close(self):
        self.calls.append(("close",))


def state(open=True, title="Shop", ready=True):
    return {"open": open, "title": title, "container": True, "ready": ready}


class ScreensTest(unittest.TestCase):
    def setUp(self):
        self.fake = screens._Screens = FakeScreens()
        screens.POLL_SECONDS = 0.01

    def test_title_decodes(self):
        self.assertEqual(screens.title(), "Café Shop")

    def test_find_filters(self):
        self.assertEqual(screens.find(), SWORD, "no filters: first slot")
        self.assertEqual(screens.find(name_regex="Stone"), CHEAP)
        self.assertEqual(screens.find(id="stone_sword"), CHEAP, "id without namespace means minecraft:")
        self.assertEqual(screens.find(id="minecraft:diamond_sword"), SWORD, "container slots come first")
        self.assertEqual(screens.find(lore_regex=r"Price: 1\b"), CHEAP, "any lore line")
        self.assertEqual(screens.find(name_regex="Sword", lore_regex="buy"), SWORD, "all filters must match")
        self.assertIsNone(screens.find(name_regex="Diamond", lore_regex="coin$"))
        self.assertIsNone(screens.find(id="dirt"))

    def test_click_and_click_item(self):
        self.assertTrue(screens.click(4, "right"))
        self.assertTrue(screens.click_item(name_regex="^Diamond Sword$", button="shift"))
        self.assertFalse(screens.click_item(id="dirt"))
        self.assertEqual(self.fake.calls, [("click", 4, "right"), ("click", 0, "shift")])
        for bad in ("double", None):
            with self.assertRaises(ValueError):
                screens.click(1, bad)
            with self.assertRaises(ValueError):
                screens.click_item(id="dirt", button=bad)
        with self.assertRaises(ValueError):
            screens.click("1")
        self.assertEqual(len(self.fake.calls), 2, "nothing reaches Java for bad arguments")

    def test_wait_for_needs_match_and_ready(self):
        self.fake.states = [state(open=False, title=""), state(title="Chest"), state(ready=False), state()]
        self.assertTrue(screens.wait_for("^Shop$", timeout=2))
        self.assertEqual(self.fake.states, [state()], "polled through every state")

    def test_wait_for_timeout(self):
        self.fake.states = [state(ready=False)]
        start = time.monotonic()
        self.assertFalse(screens.wait_for("Shop", timeout=0.1))
        self.assertGreaterEqual(time.monotonic() - start, 0.1)

    def test_items_and_close(self):
        self.assertEqual(screens.items()[0]["custom_data"], {"shop_id": "sword"})
        screens.close()
        self.assertEqual(self.fake.calls, [("close",)])


if __name__ == "__main__":
    unittest.main()
