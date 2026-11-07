# Minescript Addon

Fabric client mod for Minecraft 26.1–26.x that gives [Minescript](https://minescript.net) 5.0 scripts:

- **`hud`**: draw text, boxes and items on the HUD.
- **`chat`**: read every chat line, including the action bar and lines other client mods add, with click actions.
- **`keys`**: hotkeys. "Minescript key" slots run a command, or deliver presses to a script.
- **`alert`**: get the player's attention: a sound only you hear, a toast, a taskbar flash.
- **`inputs`**: hold attack/use/movement keys that let go when the script crashes or is killed.

**📖 Full scripting docs: [docs/README.md](docs/README.md)**: every function, the config file, examples and troubleshooting.

On launch the mod writes `hud.py`, `chat.py`, `keys.py`, `alert.py` and `inputs.py` into your `.minecraft/minescript/` folder (overwriting older copies), so scripts can just `import hud`, `import chat`, `import keys`, `import alert` or `import inputs`.

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

## Keys

The mod registers keybind slots **Minescript key 1 … N** under **Options → Controls → Minescript**. They are unbound at first, and like vanilla keys they do nothing while a screen (inventory, chest, chat) is open. Each slot is set up in `config/minescriptaddon-keys.json`, which is created on first launch:

```json
{
  "count": 3,
  "show_press": true,
  "keys": {
    "1": {"label": "Farm", "command": "\\farm"},
    "2": {"label": "Home", "command": "/home"},
    "3": {"label": "", "command": ""}
  }
}
```

| Field | |
|---|---|
| `count` | how many slots to register (default 3, 0–50). **Takes effect after a restart**, because Minecraft only accepts new keybinds at startup. Raising it adds empty entries to the file; lowering it keeps the extra entries but doesn't register them. |
| `label` | shown in Controls, e.g. "Minescript key 1 (Farm)". Read at startup only. |
| `command` | `\script args` runs a Minescript command, exactly as if typed in chat (inside JSON, write the backslash twice: `"\\farm"`). `/command` sends a game command, which also works for client-side commands. Empty: the slot does nothing unless a script claims it. |
| `show_press` | show presses in the action bar, e.g. "Minescript key 1: \farm" |

`command` and `show_press` are re-read on the next key press after you save the file, so no restart is needed. A missing or broken file is logged as a warning and the defaults are used. A broken file is left as it is, so you can fix it.

From a script, claim a slot and its presses go to the script instead of running the command:

```python
import keys

try:
    while True:
        if keys.wait(1, timeout=30):      # claims slot 1 and keeps renewing the claim while waiting
            print("pressed", keys.binding(1))
finally:
    keys.release(1)
```

| Function | |
|---|---|
| `count()` | how many slots are registered |
| `claim(slot, ttl=2.0)` | send this slot's presses to the script for `ttl` seconds; renew by calling it again, or through `pressed()`/`wait()` |
| `pressed(slot)` | presses since the last call (consumes them); claims/renews the slot |
| `wait(slot, timeout=None)` | `True` on a press, `False` after `timeout` s; polls every 50 ms and keeps the claim alive |
| `release(slot)` | give the slot back to its command |
| `binding(slot)` | the bound key's name, e.g. `"R"`, or `""` if unbound |

- The claim expires unless renewed within `ttl`, so a crashed or `\killjob`'d script stops swallowing the key, and the slot's command works again.
- Presses made before a script claims a slot run the command; they aren't queued for the script.
- A slot outside `1..count()` raises `ValueError`, and the message includes the current count.

## Alerts

```python
import alert
alert.alert("Inventory full")                       # bell, toast, taskbar flash if the window isn't focused
alert.alert("Farm stopped", repeat=3, chat=True)    # ring 3 times 0.5 s apart, plus a client-side chat line
```

`alert(text, sound=True, toast=True, flash=True, chat=False, repeat=1, title="Minescript", sound_id="minecraft:block.note_block.bell", volume=1.0, pitch=1.0)`

- Everything is client-side: the sound plays only for you, the chat line is never sent (`chat.py` records it with source `"client"`).
- `flash` only flashes the taskbar while the game window isn't focused.
- An unknown `sound_id` plays the bell and logs a warning. Nothing raises back into the script.

## Inputs

When a script holds a key through Minescript and then crashes or is `\killjob`'d, the key stays pressed. `inputs` holds expire unless renewed:

```python
import inputs, time
with inputs.holding("attack", owner="miner"):      # renewed in the background, released on exit
    time.sleep(10)

inputs.hold("forward", ttl=1.0, owner="miner")     # or renew it yourself within ttl
inputs.hold("forward", False)                      # release now
```

| Function | |
|---|---|
| `hold(key, down=True, ttl=1.0, owner="")` | hold `key` for `ttl` s (call again to renew); `down=False` releases |
| `holding(key, owner="", ttl=1.0)` | `with` block that renews from a daemon thread |
| `release_all(owner=None)` | everything, or only owners starting with `owner` |
| `held()` | `[(key, owner, seconds_left), ...]` |

- Keys: `attack`, `use`, `forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`, `pick`. Others raise `ValueError`.
- Letting go restores the physical state, so a key you hold yourself stays down.
- All holds are released on disconnect and when the world changes.

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

- `python -B -m unittest discover tests` tests `hud.py`, `chat.py`, `keys.py`, `alert.py` and `inputs.py` against a stubbed Java bridge.
- `./gradlew test` runs the JUnit tests: HUD element expiry and prefix removal; the chat ring buffer, dedup, hide rules and Component → text/§ codes/click actions/JSON; key config parsing (defaults, count changes, broken files) and key claims/expiry; input holds (renewal, expiry, owner-scoped release); and that the lookups that differ between 26.x versions resolve.
- CI runs both and builds against every supported 26.x version.
- To release, push a tag such as `git tag v1.4.0 && git push origin v1.4.0`. GitHub Actions then builds `minescriptaddon-1.4.0.jar` and attaches it to a GitHub release.
