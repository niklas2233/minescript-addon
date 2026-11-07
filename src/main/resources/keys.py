"""Hotkeys for Minescript scripts. Installed by the Minescript Addon mod (overwritten on launch).

    import keys
    try:
        while True:
            if keys.wait(1, timeout=10):      # claims slot 1 while waiting; press "Minescript key 1"
                print("pressed", keys.binding(1))
    finally:
        keys.release(1)

The mod registers "Minescript key 1..N" (Options -> Controls -> Minescript); N is "count" in
config/minescriptaddon-keys.json. While a script claims a slot, presses go to the script instead of running the
slot's command. A claim expires unless renewed within ttl seconds, so a crashed or killed script stops
swallowing the key; claim(), pressed() and wait() all renew it.
"""

import time

from java import JavaClass

_Keys = JavaClass("com.niklas2233.minescriptaddon.MinescriptKeys")

POLL_SECONDS = 0.05
DEFAULT_TTL = 2.0
_ttl = {}  # slot -> ttl of this script's last claim, reused when pressed()/wait() renew it


def count() -> int:
    """How many key slots the mod registered at startup."""
    return _Keys.count()


def _check(slot) -> int:
    n = count()
    if type(slot) is not int or not 1 <= slot <= n:
        raise ValueError(f"key slot {slot!r} is outside 1..{n} (count is {n}; change it in "
                         f"config/minescriptaddon-keys.json and restart)")
    return slot


def claim(slot: int, ttl: float = DEFAULT_TTL):
    """Send this slot's presses to this script instead of running its command, for ttl seconds (renewable)."""
    _check(slot)
    if not ttl or ttl <= 0:
        raise ValueError("ttl must be > 0 seconds")
    _ttl[slot] = float(ttl)
    _Keys.claim(slot, _ttl[slot])


def pressed(slot: int) -> int:
    """How many presses since the last call (consumes them). Claims or renews the slot."""
    _check(slot)
    return _Keys.take(slot, 0, _ttl.get(slot, DEFAULT_TTL))


def wait(slot: int, timeout: float = None) -> bool:
    """Wait for one press: True on a press, False after timeout seconds (None: forever).
    Claims the slot and keeps renewing it while waiting."""
    _check(slot)
    ttl = _ttl.get(slot, DEFAULT_TTL)
    deadline = None if timeout is None else time.monotonic() + timeout
    while True:
        if _Keys.take(slot, 1, ttl):
            return True
        if deadline is not None and time.monotonic() >= deadline:
            return False
        time.sleep(POLL_SECONDS)


def release(slot: int):
    """Give the slot back: presses run its command again."""
    _check(slot)
    _ttl.pop(slot, None)
    _Keys.release(slot)


def binding(slot: int) -> str:
    """The bound key's display name, e.g. "R", or "" if unbound."""
    _check(slot)
    return _Keys.binding(slot)
