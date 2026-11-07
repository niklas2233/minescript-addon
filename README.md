# Minescript Addon

Fabric client mod for Minecraft 26.1–26.x that gives [Minescript](https://minescript.net) 5.0 scripts:

- **`hud`**: draw text, boxes and items on the HUD.
- **`chat`**: read every chat line, including the action bar and lines other client mods add, with click actions.

On launch the mod writes `hud.py` and `chat.py` into your `.minecraft/minescript/` folder (overwriting older copies), so scripts can just `import hud` / `import chat`.

## HUD

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

## Chat

Minescript's own chat listener misses lines that other client mods add (e.g. a mod's "Auto-sprint on" notice), can't see the action bar, and doesn't expose click actions. `chat` covers all three, so there's no need to poll `logs/latest.log` or reflect into `ChatComponent`.

```python
import chat, minescript

seq = chat.latest_seq()                    # grab BEFORE sending, so a fast reply isn't missed
minescript.execute("/list")
reply = chat.wait_for(r"players online", timeout=5, since=seq)
print(reply.text if reply else "no reply")

def on_toggle(line):
    minescript.echo(f"{line.source}: {line.text}")
listener = chat.listen(on_toggle, pattern=r"Auto-sprint (on|off)")
try:
    ...                                    # your script
finally:
    listener.stop()
```

| Function | |
|---|---|
| `latest_seq()` | seq of the newest line |
| `lines(since=None, max=500)` | lines after seq `since`, oldest first; `None` = from now on (nothing yet) |
| `wait_for(pattern, timeout=10, source=None, since=None)` | first matching line after `since` (default: now), or `None` on timeout; polls every 50 ms |
| `listen(callback, pattern=None, source=None, since=None)` | calls `callback(line)` on a background thread for new matching lines; returns a listener with `.stop()` |
| `action_bar()` | plain text of the latest action bar message, or `""` |
| `hide(pattern, ttl=None)` / `unhide(pattern)` | keep matching lines out of the chat box (they are still recorded) |

Each line is a `chat.Line` with:

| Field | |
|---|---|
| `seq` | increasing number, never reused |
| `time` | epoch milliseconds |
| `source` | `"chat"` (player chat), `"game"` (server system message), `"actionbar"`, or `"client"` (added on the client, e.g. by another mod or a client command) |
| `text` / `formatted` | plain text / text with `§` codes (hex colors that aren't one of the 16 legacy colors are dropped) |
| `clicks` | `(action, value)` pairs for `run_command`, `suggest_command` and `open_url`, in text order |
| `screen` | title of the screen that was open when the line arrived (e.g. a chest menu), `""` if none |

- `pattern` is a Python regex searched in the plain `text`. `source` is one source name or a tuple of them.
- The mod keeps the last 500 lines and clears them when you leave a server. Seq keeps counting, so a saved seq stays valid.
- `hide()` uses a **Java** regex (`find()` on the plain text). It only affects the chat box: the action bar is never hidden, and hidden lines still show up in `lines()`/`wait_for()`/`listen()`. They don't reach `logs/latest.log` either, because they never enter the chat. With `ttl=` seconds the rule expires; refresh it in a loop, and a killed script's rules go away. `0`/`None` keeps it until `unhide()` or restart.
- Each line is recorded once, even when both Fabric's message event and the chat box see it.

## In-game checks

Copy these from [`examples/`](examples) into `.minecraft/minescript/`. They aren't installed by the mod.

- **`\hud_demo`**: for ~10 s draws a small `demo.` panel (box, text, a diamond item), then clears it.
- **`\chat_demo`**: prints the last 5 lines with source and click actions, sends `/list` and reports how long the reply took (`\chat_demo /help "help"` picks another command and reply regex), then prints the action bar 3 times, 1 s apart.

Both report in chat whether each step worked, or the exception it raised.

## Build

```
./gradlew build            # -> build/libs/minescriptaddon-<version>.jar  (Java 25), runs the JUnit tests
./gradlew build -Pminecraft_version=26.3 -Pfabric_api_version=0.165.2+26.3   # check another version
```

## Tests & releases

- `python -B -m unittest discover tests` tests `hud.py` and `chat.py` against a stubbed Java bridge.
- `./gradlew test` runs the JUnit tests: HUD element expiry and prefix removal, the chat ring buffer, dedup and hide rules, and Component → text/§ codes/click actions/JSON.
- CI runs both and builds against every supported 26.x version.
- To release, push a tag such as `git tag v1.2.0 && git push origin v1.2.0`. GitHub Actions then builds `minescriptaddon-1.2.0.jar` and attaches it to a GitHub release.
