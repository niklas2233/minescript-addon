"""Open screens and container slots for Minescript scripts. Installed by the Minescript Addon mod (overwritten
on launch).

    import screens, minescript
    minescript.execute("/menu")
    if screens.wait_for(r"Shop", timeout=5):            # open, and its contents have arrived
        if screens.click_item(name_regex=r"^Diamond Sword$", button="shift"):
            print("bought")
        screens.close()

items() lists the open container's non-empty slots, then the player's inventory slots (flagged "player": True).
"slot" is the number to pass to click(). Clicks are the game's own slot clicks (like clicking with the mouse,
without moving the cursor). Names and lore are plain text.
"""

import json
import re
import time

from java import JavaClass

_Screens = JavaClass("com.niklas2233.minescriptaddon.MinescriptScreens")

POLL_SECONDS = 0.05
BUTTONS = ("left", "right", "shift", "middle")


def title() -> str:
    """The open screen's title (plain text), or "" if none is open."""
    return json.loads(_Screens.title())  # ASCII-only JSON, see chat.py


def wait_for(title_regex: str, timeout: float = 10) -> bool:
    """True once a screen whose title matches title_regex (re.search) is open and, for a container, its
    contents have arrived (a non-empty slot, or 0.5 s after opening). False after timeout seconds."""
    pattern = re.compile(title_regex)
    deadline = time.monotonic() + timeout
    while True:
        state = json.loads(_Screens.state())
        if state["open"] and state["ready"] and pattern.search(state["title"]):
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(POLL_SECONDS)


def items() -> list:
    """[{"slot", "id", "count", "name", "lore": [str], "custom_data": dict or None, "player": bool}, ...] for the
    non-empty slots of the open container (container slots first); [] if no container is open."""
    return json.loads(_Screens.items())


def _id(item_id: str) -> str:
    return item_id if ":" in item_id else "minecraft:" + item_id


def find(name_regex: str = None, id: str = None, lore_regex: str = None):
    """The first slot (see items()) whose name matches name_regex, whose id is id ("diamond" means
    "minecraft:diamond") and with a lore line matching lore_regex. Omitted filters match anything. None if none."""
    name = name_regex and re.compile(name_regex)
    lore = lore_regex and re.compile(lore_regex)
    for item in items():
        if name and not name.search(item["name"]):
            continue
        if id and item["id"] != _id(id):
            continue
        if lore and not any(lore.search(line) for line in item["lore"]):
            continue
        return item
    return None


def click(slot: int, button: str = "left") -> bool:
    """Click slot with "left", "right", "shift" (quick move) or "middle" (clone, creative only).
    False if no container is open or the slot doesn't exist."""
    if button not in BUTTONS:
        raise ValueError(f"button must be one of {', '.join(BUTTONS)}, not {button!r}")
    if type(slot) is not int:
        raise ValueError(f"slot must be an int, not {slot!r}")
    return _Screens.click(slot, button)


def click_item(name_regex: str = None, id: str = None, lore_regex: str = None, button: str = "left") -> bool:
    """find() then click(); True if a matching slot was clicked."""
    if button not in BUTTONS:
        raise ValueError(f"button must be one of {', '.join(BUTTONS)}, not {button!r}")
    item = find(name_regex, id, lore_regex)
    return item is not None and click(item["slot"], button)


def close():
    """Close the open screen like Escape (a container is closed on the server too)."""
    _Screens.close()
