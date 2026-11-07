"""The player's own inventory for Minescript scripts, with no screen open. Installed by the Minescript Addon mod
(overwritten on launch). Read only: nothing here moves or changes items.

    import inventory
    sword = inventory.find(id="diamond_sword", where="hotbar")
    if sword and sword["max_damage"] - sword["damage"] < 50:
        print("sword almost broken")
    print(inventory.count(id="wheat"), "wheat")
    if inventory.wait_for(name_regex=r"^Enchanted Bread$", timeout=30):
        ...

Items are the same dicts as screens.items(): {"slot", "id", "count", "name", "lore", "custom_data", "damage",
"max_damage"}, with "slot" in the inventory's own numbering: 0-8 hotbar, 9-35 main, 36-39 armor (feet, legs,
chest, head), 40 offhand. Without a player (main menu, loading) everything is empty or None.
"""

import json
import time

from java import JavaClass

from screens import matcher

_Inventory = JavaClass("com.niklas2233.minescriptaddon.MinescriptInventory")

POLL_SECONDS = 0.05
WHERE = {"all": range(0, 41), "hotbar": range(0, 9), "main": range(9, 36), "armor": range(36, 40)}
HANDS = {"main": "mainhand", "off": "offhand"}
EQUIPMENT = ("head", "chest", "legs", "feet", "mainhand", "offhand")


def _snapshot():
    """{"selected", "items", "equipment"} read on the render thread, or None without a player."""
    return json.loads(_Inventory.snapshot())  # ASCII-only JSON, see chat.py


def items() -> list:
    """Every non-empty item, by slot (0-8 hotbar, 9-35 main, 36-39 feet/legs/chest/head, 40 offhand)."""
    snap = _snapshot()
    return snap["items"] if snap else []


def hotbar() -> list:
    """9 entries, index = hotbar slot: the item dict or None."""
    slots = [None] * 9
    for item in items():
        if item["slot"] < 9:
            slots[item["slot"]] = item
    return slots


def selected():
    """The selected hotbar slot, 0-8, or None without a player."""
    snap = _snapshot()
    return snap["selected"] if snap else None


def equipment() -> dict:
    """{"head", "chest", "legs", "feet", "mainhand", "offhand"}: what the game counts as worn and held, each an
    item dict (its "slot" is where it sits in the inventory) or None."""
    snap = _snapshot()
    worn = snap["equipment"] if snap else {}
    return {key: worn.get(key) for key in EQUIPMENT}


def held(hand: str = "main"):
    """The item in the "main" or "off" hand, or None."""
    if hand not in HANDS:
        raise ValueError(f"hand must be 'main' or 'off', not {hand!r}")
    return equipment()[HANDS[hand]]


def _filter(name_regex, id, lore_regex, where):
    if where not in WHERE:
        raise ValueError(f"where must be one of {', '.join(WHERE)}, not {where!r}")
    if name_regex is None and id is None and lore_regex is None:
        raise ValueError("give at least one of name_regex, id, lore_regex")
    match, slots = matcher(name_regex, id, lore_regex), WHERE[where]
    return lambda item: item["slot"] in slots and match(item)


def find(name_regex: str = None, id: str = None, lore_regex: str = None, where: str = "all"):
    """The first item (by slot) in where ("all", "hotbar", "main" 9-35, "armor") matching every given filter, like
    screens.find: name_regex/lore_regex searched in the plain text, id with or without "minecraft:". None if none."""
    return next(filter(_filter(name_regex, id, lore_regex, where), items()), None)


def count(id: str = None, name_regex: str = None) -> int:
    """Total count of the items matching id and name_regex (none given: every item), across the whole inventory."""
    match = matcher(name_regex, id)
    return sum(item["count"] for item in items() if match(item))


def wait_for(name_regex: str = None, id: str = None, lore_regex: str = None, timeout: float = None,
             where: str = "all"):
    """The first matching item (see find()) once it is there, or None after timeout seconds (None: wait forever)."""
    if timeout is not None and timeout < 0:
        raise ValueError(f"timeout must be >= 0, not {timeout!r}")
    wanted = _filter(name_regex, id, lore_regex, where)
    deadline = None if timeout is None else time.monotonic() + timeout
    while True:
        item = next(filter(wanted, items()), None)
        if item is not None or (deadline is not None and time.monotonic() >= deadline):
            return item
        time.sleep(POLL_SECONDS)
