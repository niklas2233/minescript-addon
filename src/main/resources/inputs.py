"""Hold vanilla inputs (attack, use, movement...) so they let go when the script dies.
Installed by the Minescript Addon mod (overwritten on launch).

    import inputs, time
    with inputs.holding("attack", owner="miner"):    # held, renewed in the background, released on exit
        time.sleep(10)

    while mining():                                  # or renew it yourself, at least once per ttl
        inputs.hold("forward", ttl=1.0, owner="miner")
        time.sleep(0.2)
    inputs.release_all("miner")

A hold expires unless hold() is called again for that key within ttl seconds, so a crashed or killed script
stops attacking/walking within ttl. Letting go restores the key's physical state: if you hold it yourself, it
stays down. Everything is also released on disconnect and when the world changes.
"""

import contextlib
import json
import threading

from java import JavaClass

_Inputs = JavaClass("com.niklas2233.minescriptaddon.MinescriptInputs")

KEYS = ("attack", "use", "forward", "back", "left", "right", "jump", "sneak", "sprint", "pick")


def _check(key) -> str:
    if key not in KEYS:
        raise ValueError(f"unknown key {key!r}, valid keys: {', '.join(KEYS)}")
    return key


def hold(key: str, down: bool = True, ttl: float = 1.0, owner: str = ""):
    """Hold key down for ttl seconds (call again to renew), or release it at once with down=False.
    owner tags the hold for release_all(); the last caller of a key owns it."""
    _check(key)
    if not down:
        _Inputs.release(key)
        return
    if not ttl or ttl <= 0:
        raise ValueError("ttl must be > 0 seconds")
    _Inputs.hold(key, str(owner), float(ttl))


def release_all(owner: str = None):
    """Release every key held through this module, or only those whose owner starts with owner."""
    _Inputs.releaseAll("" if owner is None else str(owner))


def held() -> list:
    """[(key, owner, seconds_left), ...] for every key held right now."""
    return [tuple(row) for row in json.loads(_Inputs.held())]


@contextlib.contextmanager
def holding(key: str, owner: str = "", ttl: float = 1.0):
    """Hold key for the duration of a with block, renewing from a daemon thread; released on exit."""
    hold(key, ttl=ttl, owner=owner)  # validates before the thread starts
    stop = threading.Event()

    def renew():
        while not stop.wait(ttl / 3):
            hold(key, ttl=ttl, owner=owner)
    thread = threading.Thread(target=renew, daemon=True)
    thread.start()
    try:
        yield
    finally:
        stop.set()
        thread.join()
        hold(key, False)
