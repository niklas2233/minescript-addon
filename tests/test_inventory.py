"""Tests inventory.py against a fake MinescriptInventory bridge. Run: python -B -m unittest discover tests"""
import json
import pathlib
import sys
import threading
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import inventory  # noqa: E402


def item(slot, id, count=1, name=None, lore=(), damage=0, max_damage=0):
    return {"slot": slot, "id": "minecraft:" + id, "count": count, "name": name or id.replace("_", " ").title(),
            "lore": list(lore), "custom_data": None, "damage": damage, "max_damage": max_damage}


SWORD = item(0, "diamond_sword", name="Shiny", lore=["Line one"], damage=12, max_damage=1561)
WHEAT_HOTBAR = item(4, "wheat", 64)
WHEAT_MAIN = item(20, "wheat", 30)
BREAD = item(35, "bread", 5, name="Enchanted Bread")
BOOTS = item(36, "iron_boots", max_damage=195)
HELMET = item(39, "diamond_helmet", name="Wheat Hat", max_damage=363)
SHIELD = item(40, "shield", max_damage=336)


class FakeInventory:
    def __init__(self, snapshot):
        self.snap = snapshot

    def snapshot(self):
        return json.dumps(self.snap)


def snapshot(items, selected=0):
    worn = {"feet": BOOTS, "head": HELMET, "offhand": SHIELD, "mainhand": next((i for i in items if i["slot"] == selected), None)}
    return {"selected": selected, "items": items, "equipment": {k: v for k, v in worn.items() if v in items}}


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.items = [SWORD, WHEAT_HOTBAR, WHEAT_MAIN, BREAD, BOOTS, HELMET, SHIELD]
        inventory._Inventory = FakeInventory(snapshot(self.items))
        inventory.POLL_SECONDS = 0.01

    def test_slot_numbering(self):
        self.assertEqual([i["slot"] for i in inventory.items()], [0, 4, 20, 35, 36, 39, 40])
        bar = inventory.hotbar()
        self.assertEqual(len(bar), 9)
        self.assertEqual([i and i["id"] for i in bar],
                         ["minecraft:diamond_sword", None, None, None, "minecraft:wheat", None, None, None, None])
        self.assertEqual(inventory.selected(), 0)

    def test_equipment_and_held(self):
        eq = inventory.equipment()
        self.assertEqual(list(eq), ["head", "chest", "legs", "feet", "mainhand", "offhand"])
        self.assertEqual((eq["head"], eq["chest"], eq["legs"], eq["feet"], eq["mainhand"], eq["offhand"]),
                         (HELMET, None, None, BOOTS, SWORD, SHIELD))
        self.assertEqual((inventory.held(), inventory.held("off")), (SWORD, SHIELD))
        inventory._Inventory = FakeInventory(snapshot(self.items, selected=1))
        self.assertIsNone(inventory.held("main"))
        with self.assertRaises(ValueError):
            inventory.held("left")

    def test_find_filters_and_where(self):
        self.assertEqual(inventory.find(id="wheat")["slot"], 4)                       # first by slot
        self.assertEqual(inventory.find(id="minecraft:wheat", where="main")["slot"], 20)
        self.assertEqual(inventory.find(name_regex="^Shiny$", lore_regex="one")["damage"], 12)
        self.assertIsNone(inventory.find(name_regex="Shiny", lore_regex="two"))
        self.assertEqual(inventory.find(name_regex="Wheat")["slot"], 4)                # "Wheat" item name first
        self.assertEqual(inventory.find(name_regex="Hat", where="armor"), HELMET)
        self.assertIsNone(inventory.find(id="shield", where="armor"))                  # offhand is only in "all"
        self.assertEqual(inventory.find(id="shield"), SHIELD)
        self.assertIsNone(inventory.find(id="iron_boots", where="hotbar"))
        for kwargs in ({}, {"id": "wheat", "where": "offhand"}):
            with self.assertRaises(ValueError, msg=kwargs):
                inventory.find(**kwargs)

    def test_count(self):
        self.assertEqual(inventory.count(id="wheat"), 94)
        self.assertEqual(inventory.count(name_regex="Bread"), 5)
        self.assertEqual(inventory.count(id="dirt"), 0)
        self.assertEqual(inventory.count(), 64 + 30 + 5 + 1 + 1 + 1 + 1)

    def test_wait_for(self):
        inventory._Inventory = fake = FakeInventory(snapshot([SWORD]))
        threading.Timer(0.05, lambda: setattr(fake, "snap", snapshot([SWORD, BREAD]))).start()
        self.assertEqual(inventory.wait_for(name_regex="Enchanted Bread", timeout=2), BREAD)
        self.assertIsNone(inventory.wait_for(id="dirt", timeout=0.03))
        for kwargs in ({"timeout": 1}, {"id": "dirt", "timeout": -1}, {"id": "dirt", "where": "x"}):
            with self.assertRaises(ValueError, msg=kwargs):
                inventory.wait_for(**kwargs)

    def test_no_player(self):
        inventory._Inventory = FakeInventory(None)
        self.assertEqual((inventory.items(), inventory.hotbar(), inventory.selected()), ([], [None] * 9, None))
        self.assertEqual(inventory.equipment(), dict.fromkeys(inventory.EQUIPMENT))
        self.assertEqual((inventory.held(), inventory.find(id="wheat"), inventory.count()), (None, None, 0))
        self.assertIsNone(inventory.wait_for(id="wheat", timeout=0))


if __name__ == "__main__":
    unittest.main()
