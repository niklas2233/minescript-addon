# Minescript Addon

Fabric client mod for Minecraft 26.1–26.x that lets [Minescript](https://minescript.net) 5.0 scripts draw on the HUD.

On launch the mod writes `hud.py` into your `.minecraft/minescript/` folder, so scripts can just `import hud`.

```python
# minescript/coords.py  — run with \coords
import hud, minescript, time

try:
    while True:
        x, y, z = minescript.player_position()
        hud.rect("coords.bg", 4, 4, 120, 14, ttl=1)    # default color: translucent black
        hud.text("coords.pos", 7, 7, f"§eXYZ§r {x:.0f} {y:.0f} {z:.0f}", ttl=1)
        time.sleep(0.1)
finally:
    hud.clear("coords.")                              # remove only this script's elements
```

| Function | |
|---|---|
| `text(id, x, y, text, color=0xFFFFFF, shadow=True, alpha=None, ttl=None)` | `§` color codes work |
| `rect(id, x, y, width, height, color=0x80000000, alpha=None, ttl=None)` | |
| `item(id, x, y, item_id, count=1, ttl=None)` | e.g. `"minecraft:diamond"` |
| `remove(id)` | |
| `clear(prefix=None)` | no prefix: everything; otherwise only ids starting with `prefix` |
| `text_width(text)` / `screen_size()` | for layout |

- Drawing an existing id replaces it (keeping its place in the draw order); later ids draw on top.
- **Prefix your ids per script** (`"coords."`, `"farm."`) and call `hud.clear("coords.")` in a `finally:` block, so a script only cleans up its own elements and never another script's.
- Negative `x`/`y` anchor to the right/bottom edge.
- Colors are `0xAARRGGBB`; `0xRRGGBB` is treated as opaque. `alpha=0`–`255` overrides the color's alpha when given (`alpha=0` is fully transparent, values outside 0–255 are clamped).
- `ttl=` seconds (float) makes an element disappear unless it is redrawn within that time. `0`/`None` means it never expires, which is the default. Redraw with a short `ttl` in a loop, and the HUD cleans itself up if the script crashes or is `\killjob`'d.
- Elements without a `ttl` stay after the script ends until `remove`/`clear` or you leave the world.

## In-game check

Copy [`examples/hud_demo.py`](examples/hud_demo.py) to `.minecraft/minescript/` and run `\hud_demo`. For ~10 s it draws a small `demo.` panel (box, text, a diamond item) and reports in chat whether each kind of Java call worked. Then it clears the panel.

## Build

```
./gradlew build            # -> build/libs/minescriptaddon-<version>.jar  (Java 25)
./gradlew build -Pminecraft_version=26.3 -Pfabric_api_version=0.165.2+26.3   # check another version
```

## Tests & releases

- `python -B -m unittest discover tests` tests the `hud.py` wrapper.
- `./gradlew build` also runs `ElementsCheck`, a plain-Java check of expiry and prefix removal (`./gradlew elementsCheck` runs it alone).
- CI runs both and builds against every supported 26.x version.
- To release, push a tag such as `git tag v1.0.1 && git push origin v1.0.1`. GitHub Actions then builds `minescriptaddon-1.0.1.jar` and attaches it to a GitHub release.
