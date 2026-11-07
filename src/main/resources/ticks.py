"""Client ticks for Minescript scripts. Installed by the Minescript Addon mod (overwritten on launch).

    import ticks
    start = ticks.count()
    ticks.wait(20)                      # about 1 second at 20 ticks/s
    print(ticks.count() - start, "ticks,", ticks.time_since(start), "s")

The counter counts ended client ticks, also outside a world: about 20 per second, fewer when the game lags.
wait() wakes up within about a millisecond of the tick ending (plus Minescript's own call overhead), without
polling: the mod blocks the call until the tick, in chunks of at most 0.1 s, so a killed script never holds the
bridge for longer than that.
"""

from minescript_runtime import script_loop

from java import JavaClass

_Ticks = JavaClass("com.niklas2233.minescriptaddon.MinescriptTicks")

MAX_BLOCK_MS = 100  # longest single blocking call into Java


def count() -> int:
    """Number of client ticks that have ended since the game started."""
    with script_loop:  # answered on the script's own thread, not on the next rendered frame
        return _Ticks.count()


def wait(n: int = 1, timeout: float = None):
    """Wait until n more client ticks have ended. Returns the new count, or None after timeout seconds."""
    if type(n) is not int or n < 1:
        raise ValueError("n must be an int >= 1")
    target = count() + n
    budget_ms = None if timeout is None else max(0, int(timeout * 1000))
    while True:
        chunk = MAX_BLOCK_MS if budget_ms is None else min(MAX_BLOCK_MS, budget_ms)
        with script_loop:  # blocks the script's own thread in Java, never the game's
            now = _Ticks.waitFor(target, chunk)
        if now >= target:
            return now
        if budget_ms is not None:
            budget_ms -= chunk
            if budget_ms <= 0:
                return None


def time_since(count: int) -> float:
    """Seconds since tick `count` ended (as returned by count() or wait())."""
    with script_loop:
        seconds = _Ticks.timeSince(int(count))
    if seconds < 0:
        raise ValueError(f"tick {count} hasn't ended yet")
    return seconds
