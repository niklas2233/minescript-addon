"""A shared status panel for Minescript scripts. Installed by the Minescript Addon mod (overwritten on launch).

    import status, time
    while farming():
        status.set("farm", f"{crops} crops", ttl=2)   # one line "farm: 123 crops" in the shared panel
        time.sleep(1)
    status.clear("farm")

Every script's line goes into one panel, sorted by name. A line disappears unless set() is called again within
ttl seconds, so a crashed or killed script's line goes away. The panel's corner, offset, background opacity and
on/off are in config/minescriptaddon-status.json (re-read within a second after you save it).
It's hidden with the rest of the HUD (F1) and while the debug screen (F3) is open.
"""

from java import JavaClass

from hud import _argb

_Status = JavaClass("com.niklas2233.minescriptaddon.MinescriptStatus")


def set(name: str, text: str, ttl: float = 2.0, color: int = None):
    """Show or update this script's line "name: text" for ttl seconds (renew it by calling again).
    color is 0xRRGGBB or 0xAARRGGBB like hud.text; None = white."""
    if type(name) is not str or not name:
        raise ValueError("name must be a non-empty string")
    if not ttl or ttl <= 0:
        raise ValueError("ttl must be > 0 seconds")
    _Status.set(name, str(text), _argb(0xFFFFFF if color is None else color), float(ttl))


def clear(name: str):
    """Remove the line now."""
    _Status.clear(str(name))
