"""Draw on the Minecraft HUD from Minescript. Installed by the Minescript HUD mod (overwritten on launch).

    import hud
    try:
        hud.rect("farm.bg", 4, 4, 100, 14, 0x80000000)
        hud.text("farm.title", 7, 7, "Hello §aworld", ttl=2)   # vanishes 2 s after the last redraw
        hud.item("farm.sword", -20, 4, "minecraft:diamond_sword")
    finally:
        hud.clear("farm.")                                     # only this script's elements

Each element has an id; drawing the same id again replaces it. Later ids draw on top.
Prefix ids per script ("farm.", "mine.") so clear(prefix) cleans up only your own elements.
Negative x/y anchor to the right/bottom edge.
Colors are 0xAARRGGBB; 0xRRGGBB is treated as opaque. alpha=0-255 overrides the color's alpha (0 = invisible).
ttl=seconds makes an element disappear unless redrawn in time (0/None = never), so elements of a script
that crashed or was killed go away. Otherwise elements stay until removed, cleared, or you leave the world.
"""

from java import JavaClass

_Hud = JavaClass("com.niklas2233.minescripthud.MinescriptHud")


def _argb(color: int, alpha: int = None) -> int:
    color = int(color) & 0xFFFFFFFF
    if alpha is not None:
        color = max(0, min(255, int(alpha))) << 24 | (color & 0xFFFFFF)
    elif color <= 0xFFFFFF:
        color |= 0xFF000000
    return color - (1 << 32) if color >= (1 << 31) else color


def _ttl(ttl) -> float:
    return float(ttl or 0)


def text(id: str, x: int, y: int, text: str, color: int = 0xFFFFFF, shadow: bool = True,
         alpha: int = None, ttl: float = None):
    _Hud.text(id, int(x), int(y), str(text), _argb(color, alpha), bool(shadow), _ttl(ttl))


def rect(id: str, x: int, y: int, width: int, height: int, color: int = 0x80000000,
         alpha: int = None, ttl: float = None):
    _Hud.rect(id, int(x), int(y), int(width), int(height), _argb(color, alpha), _ttl(ttl))


def item(id: str, x: int, y: int, item_id: str, count: int = 1, ttl: float = None):
    _Hud.item(id, int(x), int(y), item_id, int(count), _ttl(ttl))


def remove(id: str):
    _Hud.remove(id)


def clear(prefix: str = None):
    """No prefix: remove everything. With a prefix: remove only ids starting with it."""
    if prefix:
        _Hud.clear(str(prefix))
    else:
        _Hud.clear()


def text_width(text: str) -> int:
    return _Hud.textWidth(str(text))


def screen_size() -> tuple:
    return _Hud.screenWidth(), _Hud.screenHeight()
