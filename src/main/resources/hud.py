"""Draw on the Minecraft HUD from Minescript. Installed by the Minescript HUD mod (overwritten on launch).

    import hud
    hud.rect("bg", 4, 4, 100, 14, 0x80000000)
    hud.text("hello", 7, 7, "Hello §aworld")
    hud.item("sword", -20, 4, "minecraft:diamond_sword")
    hud.remove("hello"); hud.clear()

Each element has an id; drawing the same id again replaces it. Later ids draw on top.
Negative x/y anchor to the right/bottom edge. Colors are 0xAARRGGBB; 0xRRGGBB is treated as opaque.
Elements stay until removed, hud.clear(), or you leave the world.
"""

from java import JavaClass

_Hud = JavaClass("com.niklas2233.minescripthud.MinescriptHud")


def _argb(color: int) -> int:
    if color <= 0xFFFFFF:
        color |= 0xFF000000
    return color - (1 << 32) if color >= (1 << 31) else color


def text(id: str, x: int, y: int, text: str, color: int = 0xFFFFFF, shadow: bool = True):
    _Hud.text(id, int(x), int(y), str(text), _argb(color), bool(shadow))


def rect(id: str, x: int, y: int, width: int, height: int, color: int = 0x80000000):
    _Hud.rect(id, int(x), int(y), int(width), int(height), _argb(color))


def item(id: str, x: int, y: int, item_id: str, count: int = 1):
    _Hud.item(id, int(x), int(y), item_id, int(count))


def remove(id: str):
    _Hud.remove(id)


def clear():
    _Hud.clear()


def text_width(text: str) -> int:
    return _Hud.textWidth(str(text))


def screen_size() -> tuple:
    return _Hud.screenWidth(), _Hud.screenHeight()

