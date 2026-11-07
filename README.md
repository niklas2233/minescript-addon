# Minescript HUD

Fabric client mod for Minecraft 26.1–26.x that lets [Minescript](https://minescript.net) 5.0 scripts draw on the HUD.

On launch the mod writes `hud.py` into your `.minecraft/minescript/` folder, so scripts can just `import hud`.

```python
# minescript/coords.py  — run with \coords
import hud, minescript, time

hud.rect("bg", 4, 4, 120, 14)                     # default color: translucent black
while True:
    x, y, z = minescript.player_position()
    hud.text("pos", 7, 7, f"§eXYZ§r {x:.0f} {y:.0f} {z:.0f}")
    time.sleep(0.1)
```

| Function | |
|---|---|
| `text(id, x, y, text, color=0xFFFFFF, shadow=True)` | `§` color codes work |
| `rect(id, x, y, width, height, color=0x80000000)` | |
| `item(id, x, y, item_id, count=1)` | e.g. `"minecraft:diamond"` |
| `remove(id)` / `clear()` | |
| `text_width(text)` / `screen_size()` | for layout |

- Drawing an existing id replaces it; later ids draw on top.
- Negative `x`/`y` anchor to the right/bottom edge.
- Colors are `0xAARRGGBB`; `0xRRGGBB` is treated as opaque.
- Elements stay after the script ends until `remove`/`clear` or you leave the world.

## Build

```
./gradlew build            # -> build/libs/minescripthud-<version>.jar  (Java 25)
./gradlew build -Pminecraft_version=26.3 -Pfabric_api_version=0.165.2+26.3   # check another version
```

## Tests & releases

- `python -B -m unittest discover tests` tests the `hud.py` wrapper. CI also builds against every supported 26.x version.
- To release, push a tag such as `git tag v1.0.1 && git push origin v1.0.1`. GitHub Actions then builds `minescripthud-1.0.1.jar` and attaches it to a GitHub release.
