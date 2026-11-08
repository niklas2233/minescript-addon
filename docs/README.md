## Minescript Addon v1.0.0 docs

Table of contents:

- [Installation](#installation)
- [In-game usage](#in-game-usage)
    - [Script basics](#script-basics)
    - [Minescript keys](#minescript-keys)
- [Configuration](#configuration)
- [Python API](#python-api)
    - [Conventions](#conventions)
    - [hud module](#hud-module)
    - [chat module](#chat-module)
    - [keys module](#keys-module)
    - [alert module](#alert-module)
    - [inputs module](#inputs-module)
    - [ticks module](#ticks-module)
    - [screen_info module](#screen_info-module)
    - [status module](#status-module)
    - [screens module](#screens-module)
    - [settings module](#settings-module)
    - [world_events module](#world_events-module)
    - [inventory module](#inventory-module)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Installation

Minescript Addon is a Fabric client mod for Minecraft **26.1 – 26.x**. It requires:

- Fabric Loader and **Fabric API**
- **Minescript 5.0** for your Minecraft version

Download `minescriptaddon-<version>.jar` from
[Releases](https://github.com/niklas2233/minescript-addon/releases) and put it in the **mods** folder
(within the **minecraft** folder). When upgrading, delete the old jar first. Very old builds were
named `minescripthud-*.jar`.

On every launch the mod writes twelve Python modules into the **minescript** folder, so scripts can
import them. Don't edit these files: they're overwritten on the next launch.

| File | Module |
|---|---|
| `minecraft/minescript/hud.py` | [hud module](#hud-module) |
| `minecraft/minescript/chat.py` | [chat module](#chat-module) |
| `minecraft/minescript/keys.py` | [keys module](#keys-module) |
| `minecraft/minescript/alert.py` | [alert module](#alert-module) |
| `minecraft/minescript/inputs.py` | [inputs module](#inputs-module) |
| `minecraft/minescript/ticks.py` | [ticks module](#ticks-module) |
| `minecraft/minescript/screen_info.py` | [screen_info module](#screen_info-module) |
| `minecraft/minescript/status.py` | [status module](#status-module) |
| `minecraft/minescript/screens.py` | [screens module](#screens-module) |
| `minecraft/minescript/settings.py` | [settings module](#settings-module) |
| `minecraft/minescript/world_events.py` | [world_events module](#world_events-module) |
| `minecraft/minescript/inventory.py` | [inventory module](#inventory-module) |

## In-game usage

### Script basics

Scripts that use this mod are ordinary Minescript scripts. Save a Python file in the **minescript**
folder and run it from chat with a backslash, e.g. `minecraft/minescript/coords.py` runs as
`\coords`:

```
import time
import hud, minescript

try:
  while True:
    x, y, z = minescript.player_position()
    hud.rect("coords.bg", 4, 4, 120, 14, ttl=1)
    hud.text("coords.pos", 7, 7, f"§eXYZ§r {x:.0f} {y:.0f} {z:.0f}", ttl=1)
    time.sleep(0.1)
finally:
  hud.clear("coords.")
```

Stop it with `\killjob`. The box disappears about 1 second later because of `ttl=1` (see
[Conventions](#conventions)).

### Minescript keys

The mod registers keybinds **Minescript key 1** … **Minescript key N** in their own **Minescript**
category under *Options → Controls*. They start unbound. Like vanilla keys, they do nothing while a
screen (inventory, chest, chat) is open or when you're not in a world.

When pressed, a key:

1. delivers the press to the script that has [claimed](#keysclaim) it, if any; otherwise
2. runs the slot's `command` from the [configuration](#configuration):
    - `\script args` runs a Minescript command, exactly as if typed in chat (it also appears in
      chat history)
    - `/command` sends a Minecraft command. Client-side commands from other Fabric mods work too.

If `show_press` is on, a command press is shown in the action bar, e.g. `Minescript key 1: \farm`.

## Configuration

Hotkeys are configured in `minecraft/config/minescriptaddon-keys.json`, which is created on first
launch:

```
{
  "count": 3,
  "show_press": true,
  "keys": {
    "1": {"label": "Farm", "command": "\\farm wheat"},
    "2": {"label": "Home", "command": "/home"},
    "3": {"label": "", "command": ""}
  }
}
```

| Field | Description | Applies |
|---|---|---|
| `count` | number of key slots to register (default 3, min 0, max 50) | after restart |
| `show_press` | show command presses in the action bar (default `true`) | next key press |
| `keys.N.label` | shown in Controls as `Minescript key N (label)` | after restart |
| `keys.N.command` | `\...` Minescript command, `/...` Minecraft command, or `""` for nothing | next key press |

Notes:

- In JSON a backslash must be doubled: `"\\farm"` means `\farm`.
- Raising `count` adds empty entries to the file. Lowering it keeps the extra entries in the file
  but doesn't register them.
- `command` and `show_press` are re-read on the first key press after the file is saved.
- A missing or broken file logs a warning and the defaults are used. A broken file is never
  overwritten.
- Bindings are saved by Minecraft in `options.txt` as `key.minescriptaddon.slot.N`, so changing a
  label keeps the binding.

### Status panel

The [status panel](#status-module) is configured in `minecraft/config/minescriptaddon-status.json`,
which is created on first launch:

```
{
  "enabled": true,
  "corner": "top_left",
  "x": 4,
  "y": 4,
  "opacity": 0.5
}
```

| Field | Description |
|---|---|
| `enabled` | `false` hides the panel |
| `corner` | `top_left`, `top_right`, `bottom_left` or `bottom_right` |
| `x`, `y` | offset in GUI pixels, counted inwards from `corner` |
| `opacity` | background opacity, 0–1 (`0.5` is the same translucent black as `hud.rect`'s default) |

Changes apply within a second after the file is saved. A broken file logs a warning, the defaults
are used, and the file is left alone so you can fix it.

## Python API

### Conventions

- **Ids and prefixes:** HUD elements are identified by string ids. Give each script its own
  prefix (`"farm."`, `"coords."`) so it can clean up only its own elements with
  [`hud.clear(prefix)`](#hudclear).
- **`ttl`:** HUD elements, chat hide rules, key claims and input holds all take `ttl`, the number of seconds
  until they expire unless renewed. Renew them in your script's loop.
  `\killjob` stops a script **without** running `finally:` blocks, so `ttl` is the cleanup that
  always works.
- **Polling:** scripts reach the mod through Minescript's Java bridge. [`chat.wait_for`](#chatwait_for),
  [`chat.listen`](#chatlisten) and [`keys.wait`](#keyswait) poll about every 50 ms
  (`chat.POLL_SECONDS`, `keys.POLL_SECONDS`).
- **Leaving a world or server** clears all HUD elements and the chat buffer, and releases all input holds. Chat hide rules and
  key claims are kept until they expire.

### hud module

*Usage:* `import hud  # from Python script`

Draws text, filled rectangles and item icons on the HUD.

- [`hud.clear`](#hudclear)
- [`hud.item`](#huditem)
- [`hud.rect`](#hudrect)
- [`hud.remove`](#hudremove)
- [`hud.screen_size`](#hudscreen_size)
- [`hud.text`](#hudtext)
- [`hud.text_width`](#hudtext_width)

Coordinates are GUI-scaled pixels from the top-left corner. **Negative `x` counts from the right
edge and negative `y` from the bottom**; the element's top-left corner is placed at that point.
Floats are rounded down.

Colors are `0xRRGGBB` (treated as opaque) or `0xAARRGGBB`. Pass `alpha=0..255` to override the
alpha; `alpha=0` is invisible.

Drawing an existing id replaces that element in place. Elements draw in the order their ids were
first created, so later ids draw on top.

#### hud.clear
*Usage:* <code>hud.clear(prefix: str = None)</code>

Removes HUD elements.

*Args:*

- `prefix`: remove only ids starting with this string; `None` or `""` removes **all** elements,
  including other scripts' elements

#### hud.item
*Usage:* <code>hud.item(id: str, x: int, y: int, item_id: str, count: int = 1, ttl: float = None)</code>

Draws a 16×16 item icon with its top-left corner at (`x`, `y`).

*Args:*

- `id`: element id
- `x`, `y`: position (negative = from right/bottom)
- `item_id`: namespaced item id, e.g. `"minecraft:diamond"`; an unknown id draws nothing, and a
  malformed id raises an error
- `count`: stack size; if more than 1, it's drawn on the icon
- `ttl`: seconds until the element disappears unless redrawn; `None`/`0` = never

#### hud.rect
*Usage:* <code>hud.rect(id: str, x: int, y: int, width: int, height: int, color: int = 0x80000000, alpha: int = None, ttl: float = None)</code>

Draws a filled rectangle. The default color is 50% transparent black, which works well as a
background panel.

*Args:*

- `id`: element id
- `x`, `y`: top-left position (negative = from right/bottom)
- `width`, `height`: size in GUI pixels
- `color`: `0xRRGGBB` or `0xAARRGGBB`
- `alpha`: 0–255, overrides the color's alpha (clamped)
- `ttl`: seconds until the element disappears unless redrawn; `None`/`0` = never

#### hud.remove
*Usage:* <code>hud.remove(id: str)</code>

Removes one element. Unknown ids are ignored.

#### hud.screen_size
*Usage:* <code>hud.screen_size() -> Tuple[int, int]</code>

Gets the HUD size, in the same GUI-scaled pixels as `x`/`y`.

*Returns:*

- `(width, height)`, which changes with the window size and the GUI Scale option

#### hud.text
*Usage:* <code>hud.text(id: str, x: int, y: int, text: str, color: int = 0xFFFFFF, shadow: bool = True, alpha: int = None, ttl: float = None)</code>

Draws one line of text with its top-left corner at (`x`, `y`).

*Args:*

- `id`: element id
- `x`, `y`: position (negative = from right/bottom)
- `text`: any value (`str()` is applied); `§` formatting codes work, e.g. `"§cred §lbold"`
- `color`: base color, `0xRRGGBB` or `0xAARRGGBB`; `§` codes in the text override it
- `shadow`: draw the text shadow
- `alpha`: 0–255, overrides the color's alpha (clamped)
- `ttl`: seconds until the element disappears unless redrawn; `None`/`0` = never

#### hud.text_width
*Usage:* <code>hud.text_width(text: str) -> int</code>

Gets the width of `text` in GUI pixels with the current font. `§` codes take no space. Useful for
right-aligning text, e.g. `x = -(hud.text_width(s) + 4)`.

### chat module

*Usage:* `import chat  # from Python script`

Reads every chat line that reaches the client: player chat, server messages, the action bar, and
lines that other client mods add (which Minescript's own chat listener misses), each with its
click actions.

- [`chat.action_bar`](#chataction_bar)
- [`chat.hide`](#chathide)
- [`chat.latest_seq`](#chatlatest_seq)
- [`chat.Line`](#chatline)
- [`chat.lines`](#chatlines)
- [`chat.listen`](#chatlisten)
- [`chat.Listener`](#chatlistener)
- [`chat.unhide`](#chatunhide)
- [`chat.wait_for`](#chatwait_for)

The mod keeps the **last 500 lines**, each with an increasing `seq` number, and scripts ask for
lines after a given seq. The buffer is cleared when you leave a world or server, but `seq` keeps
counting. A line that reaches the client by two paths is recorded once.

**Patterns** in `wait_for` and `listen` are Python regexes matched with
`re.search` against the line's **plain** `text`. [`chat.hide`](#chathide) uses Java regexes.

#### chat.Line

```
  seq: int          # increasing number, never reused
  time: int         # arrival time, epoch milliseconds
  source: str       # "chat" | "game" | "actionbar" | "title" | "subtitle" | "client"
  text: str         # plain text, formatting removed
  formatted: str    # text with § codes
  clicks: tuple     # ((action, value), ...) for run_command / suggest_command / open_url
  screen: str       # title of the screen open when it arrived, "" if none
```

Sources:

- `"chat"`: player chat
- `"game"`: system messages from the server (command feedback, join/leave, plugin messages)
- `"actionbar"`: the text above the hotbar, recorded only when it changes (see
  [`chat.action_bar`](#chataction_bar))
- `"title"` / `"subtitle"`: titles (`/title`); re-sending the one still on screen isn't recorded
  again (see [`screen_info.title`](#screen_infotitle))
- `"client"`: lines added on the client: other mods' messages, client command output,
  `minescript.echo`

In `formatted`, hex colors that aren't one of the 16 `§` colors are dropped. Non-ASCII text (`§`,
accents, arrows, emoji) arrives unchanged.

#### chat.action_bar
*Usage:* <code>chat.action_bar() -> str</code>

Gets the plain text of the latest action bar message.

*Returns:*

- the text, or `""` if there hasn't been one since joining. The value remains after the message
  fades on screen.

Every action bar message is captured, including `/title @s actionbar …` and messages from other
mods. Servers often re-send the same action bar text every second or so. `action_bar()` always
returns the latest, but an `"actionbar"` [`Line`](#chatline) is only recorded when the text changes.

#### chat.hide
*Usage:* <code>chat.hide(pattern: str, ttl: float = None)</code>

Keeps chat-box lines matching `pattern` out of the chat box. They are still recorded and returned
by `lines`/`wait_for`/`listen`. The action bar is never hidden.

*Args:*

- `pattern`: **Java** regex, matched with `find()` against the plain text; the exact string
  identifies the rule
- `ttl`: seconds until the rule expires unless `hide` is called again with the same pattern;
  `None`/`0` = until [`chat.unhide`](#chatunhide) or a restart

Hidden lines don't reach `logs/latest.log` either.

#### chat.latest_seq
*Usage:* <code>chat.latest_seq() -> int</code>

Gets the seq of the newest recorded line. Call it **before** sending a command, then pass the
result as `since=` so a fast reply isn't missed.

#### chat.lines
*Usage:* <code>chat.lines(since: int = None, max: int = 500) -> List[Line]</code>

Gets lines with `seq` greater than `since`, oldest first.

*Args:*

- `since`: seq to read after; `None` means "from now on" and returns `[]`
- `max`: maximum number of lines returned

*Returns:*

- list of [`Line`](#chatline)

```
seq = chat.latest_seq()
while True:
  for line in chat.lines(seq):
    seq = line.seq
    print(line.source, line.text)
  time.sleep(0.1)
```

#### chat.listen
*Usage:* <code>chat.listen(callback: Callable[[Line], None], pattern: str = None, source = None, since: int = None) -> Listener</code>

Calls `callback(line)` on a background thread for each new matching line until stopped.

*Args:*

- `callback`: function taking a [`Line`](#chatline); exceptions are printed and listening continues
- `pattern`: Python regex on the plain text; `None` = every line
- `source`: one source or a tuple of sources; `None` = any
- `since`: seq to start after; `None` = from now on

*Returns:*

- [`Listener`](#chatlistener)

The callback runs on another thread. Minescript calls made from that thread haven't been fully
checked in game, so if one misbehaves, poll [`chat.lines`](#chatlines) on the main loop instead.

#### chat.Listener

```
  thread: threading.Thread   # daemon thread running the callback
```

`Listener.stop()` stops listening and waits up to 1 s for the thread to end. It's safe to call from
inside the callback.

#### chat.unhide
*Usage:* <code>chat.unhide(pattern: str)</code>

Removes a hide rule. `pattern` must be the same string passed to [`chat.hide`](#chathide).

#### chat.wait_for
*Usage:* <code>chat.wait_for(pattern: str, timeout: float = 10, source = None, since: int = None) -> Line</code>

Waits for the first line after `since` whose plain text matches `pattern`.

*Args:*

- `pattern`: Python regex, matched with `re.search` against the plain text
- `timeout`: seconds to wait; `0` = check once
- `source`: one source (`"game"`) or a tuple (`("chat", "game")`); `None` = any
- `since`: seq to search after; `None` = lines arriving from now on

*Returns:*

- the matching [`Line`](#chatline), or `None` on timeout

```
seq = chat.latest_seq()
minescript.execute("/list")
reply = chat.wait_for(r"players online", timeout=5, since=seq)
```

### keys module

*Usage:* `import keys  # from Python script`

Lets a script take over [Minescript keys](#minescript-keys). While a script **claims** a slot,
presses go to the script **instead of** running the slot's command. A claim expires unless renewed
within its `ttl` (default 2 s), so a crashed or `\killjob`'d script stops swallowing the key.
[`claim`](#keysclaim), [`pressed`](#keyspressed) and [`wait`](#keyswait) all renew it.

- [`keys.binding`](#keysbinding)
- [`keys.claim`](#keysclaim)
- [`keys.count`](#keyscount)
- [`keys.pressed`](#keyspressed)
- [`keys.release`](#keysrelease)
- [`keys.wait`](#keyswait)

Slots are numbered from 1. A slot outside `1..count()` raises `ValueError`, and the message includes
the current count.

Presses made before a script claims a slot run its command; they aren't queued. Claims are per
slot, not per script, so give each script its own slot.

#### keys.binding
*Usage:* <code>keys.binding(slot: int) -> str</code>

Gets the display name of the key bound to `slot`.

*Returns:*

- e.g. `"R"` or `"Left Shift"`, or `""` if unbound

#### keys.claim
*Usage:* <code>keys.claim(slot: int, ttl: float = 2.0)</code>

Claims or renews `slot`, so its presses go to this script for `ttl` seconds.

*Args:*

- `slot`: 1..[`count()`](#keyscount)
- `ttl`: seconds, must be > 0; remembered and reused by `pressed`/`wait` on this slot

#### keys.count
*Usage:* <code>keys.count() -> int</code>

Gets the number of key slots registered at startup (`count` from the
[configuration](#configuration)).

#### keys.pressed
*Usage:* <code>keys.pressed(slot: int) -> int</code>

Gets the number of presses since the last call, consuming them. It also claims or renews the slot.
Call it at least once per `ttl`.

```
while running:
  for _ in range(keys.pressed(2)):
    toggle_something()
  time.sleep(0.05)
```

#### keys.release
*Usage:* <code>keys.release(slot: int)</code>

Releases `slot`: presses run its command again. Queued presses are dropped.

#### keys.wait
*Usage:* <code>keys.wait(slot: int, timeout: float = None) -> bool</code>

Waits for one press of `slot`, polling about every 50 ms and keeping the claim alive.

*Args:*

- `timeout`: seconds; `None` = forever, `0` = check once

*Returns:*

- `True` on a press (one press per call), `False` on timeout

```
try:
  while True:
    if keys.wait(1, timeout=30):
      minescript.echo(f"{keys.binding(1)} pressed")
finally:
  keys.release(1)
```

### alert module

*Usage:* `import alert  # from Python script`

Gets your attention when a script needs you: a sound, a toast in the corner, and a taskbar flash
when the game window isn't focused. Everything is client-side: the sound plays only for you and the
chat line is never sent to the server.

- [`alert.alert`](#alertalert)

#### alert.alert
*Usage:* <code>alert.alert(text: str, sound: bool = True, toast: bool = True, flash: bool = True, chat: bool = False, repeat: int = 1, title: str = "Minescript", sound_id: str = "minecraft:block.note_block.bell", volume: float = 1.0, pitch: float = 1.0)</code>

Alerts the player. Safe to call from any thread: the mod runs it on the game thread.

*Args:*

- `text`: toast text, and the chat line with `chat=True`
- `sound`: play `sound_id` at `volume` and `pitch`; any sound id works, including resource-pack
  sounds. An unknown id plays the bell instead and logs a warning.
- `toast`: show a toast titled `title`
- `flash`: flash the window in the taskbar if it isn't focused (does nothing when it is)
- `chat`: also add `text` to the chat box as a client-side line (source `"client"` in the
  [chat module](#chat-module)); skipped outside a world
- `repeat`: play the sound this many times, about 0.5 s apart (1–20)

Never raises back into the script: a part that can't run is skipped and logged in
`logs/latest.log`.

```
if inventory_full():
  alert.alert("Inventory full", repeat=3)
```

### inputs module

*Usage:* `import inputs  # from Python script`

Holds vanilla inputs (attack, use, movement…) **that let go when the script dies**. When a script
presses a key through Minescript and then crashes or is `\killjob`'d, the key stays down until you
press it yourself. A hold through `inputs` expires unless renewed within its `ttl` (default 1 s).

- [`inputs.held`](#inputsheld)
- [`inputs.hold`](#inputshold)
- [`inputs.holding`](#inputsholding)
- [`inputs.release_all`](#inputsrelease_all)

Keys: `attack`, `use`, `forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`, `pick`
(whatever they're bound to in Controls). Any other name raises `ValueError` listing these.

When a hold ends, the key goes back to its **physical** state: if you're holding it yourself, it
stays down. Expiry is checked every tick, and everything is released when you leave a server or
the world changes. Holds keep working while a screen is open.

#### inputs.hold
*Usage:* <code>inputs.hold(key: str, down: bool = True, ttl: float = 1.0, owner: str = "")</code>

Holds `key` down for `ttl` seconds, or renews the hold. `down=False` releases it at once.

*Args:*

- `ttl`: seconds, must be > 0; call `hold` again within it to keep the key down
- `owner`: tag for [`release_all`](#inputsrelease_all); the last caller of a key owns it

```
while mining():
  inputs.hold("attack", owner="miner")
  time.sleep(0.2)
inputs.hold("attack", False)
```

#### inputs.holding
*Usage:* <code>with inputs.holding(key: str, owner: str = "", ttl: float = 1.0):</code>

Holds `key` for the duration of a `with` block. A background thread renews the hold every
`ttl / 3` s, and the key is released when the block exits.

```
with inputs.holding("forward", owner="walker"):
  time.sleep(5)
```

#### inputs.held
*Usage:* <code>inputs.held() -> list</code>

Gets the keys held through this module, for debugging.

*Returns:*

- `[(key, owner, seconds_left), ...]`

#### inputs.release_all
*Usage:* <code>inputs.release_all(owner: str = None)</code>

Releases every key held through this module, or only those whose owner **starts with** `owner`.
Give each script its own owner (`"farm."`) so scripts don't release each other's keys.

### ticks module

*Usage:* `import ticks  # from Python script`

Counts **client ticks**: about 20 per second, fewer when the game lags. The counter starts when the
game starts and keeps counting outside a world (title screen, menus).

- [`ticks.count`](#tickscount)
- [`ticks.time_since`](#tickstime_since)
- [`ticks.wait`](#tickswait)

**Latency:** [`wait`](#tickswait) doesn't poll. The mod blocks the call in Java until the tick ends,
so it returns within about a millisecond of the tick, plus Minescript's own call overhead (usually a
few ms). The call runs on the script's own thread (Minescript's script loop), never on the game's,
and blocks for at most 0.1 s at a time, so a `\killjob`'d script lets go of the bridge within 0.1 s.

#### ticks.count
*Usage:* <code>ticks.count() -> int</code>

Gets the number of client ticks that have ended since the game started.

#### ticks.wait
*Usage:* <code>ticks.wait(n: int = 1, timeout: float = None) -> int</code>

Waits until `n` more client ticks have ended.

*Args:*

- `n`: ticks to wait, >= 1
- `timeout`: seconds; `None` = forever, `0` = check once

*Returns:*

- the new tick count, or `None` on timeout

```
while running:
  ticks.wait()          # once per tick
  update_hud()
```

#### ticks.time_since
*Usage:* <code>ticks.time_since(count: int) -> float</code>

Gets the seconds since tick `count` ended, measured from when the tick actually ended. Ticks more
than ~55 min old are estimated at 50 ms per tick. A tick that hasn't ended yet raises `ValueError`.

```
start = ticks.count()
do_something()
minescript.echo(f"{ticks.count() - start} ticks, {ticks.time_since(start):.2f} s")
```

### screen_info module

*Usage:* `import screen_info  # from Python script`

Reads the **sidebar scoreboard**, the **tab list** and the **title** on screen. Each call reads one
snapshot on the game thread. Outside a world, the values are empty (`title()` returns `None`).

Every text comes twice, like [`chat.Line`](#chatline)'s `text`/`formatted`: plain, and with a
`formatted_` prefix with `§` codes (hex colors that aren't one of the 16 `§` colors are dropped).

Titles and subtitles are also recorded in the [chat module](#chat-module) with sources `"title"` and
`"subtitle"`, so [`chat.wait_for`](#chatwait_for) works for them.

- [`screen_info.sidebar`](#screen_infosidebar)
- [`screen_info.tab_list`](#screen_infotab_list)
- [`screen_info.title`](#screen_infotitle)

#### screen_info.sidebar
*Usage:* <code>screen_info.sidebar() -> dict</code>

Gets the sidebar scoreboard as displayed.

*Returns:*

- `{"title": str, "formatted_title": str, "lines": [str, ...], "formatted_lines": [str, ...], "scores": [str, ...]}`
- `lines` top to bottom (at most 15, like the game), with team prefixes and suffixes; `scores` are
  the numbers on the right as shown (`""` when the server hides them)
- empty title and lists when no sidebar is shown

Only the main sidebar is read, not the per-team-color sidebars.

```
board = screen_info.sidebar()
coins = next((l for l in board["lines"] if l.startswith("Coins: ")), None)
```

#### screen_info.tab_list
*Usage:* <code>screen_info.tab_list() -> dict</code>

Gets the tab list (player list), whether or not it's open.

*Returns:*

- `{"header": str, "formatted_header": str, "footer": str, "formatted_footer": str, "players": [...]}`
- each player: `{"name": str, "display": str, "formatted_display": str, "ping": int, "gamemode": str}`;
  `name` is the account name, `display` the name as shown in the list, `ping` in ms, `gamemode`
  e.g. `"survival"`
- players in the game's own tab-list order, at most 80 (like the game)

#### screen_info.title
*Usage:* <code>screen_info.title()</code>

Gets the title currently on screen.

*Returns:*

- `{"title": str, "formatted_title": str, "subtitle": str, "formatted_subtitle": str, "seconds_left": float}`,
  or `None` when no title is shown; `seconds_left` includes the fade-out

### status module

*Usage:* `import status  # from Python script`

A **shared status panel**: each script shows one line, `name: text`, in a single panel that lists
every script's status, sorted by name. A line disappears unless renewed within its `ttl`, so a
crashed or `\killjob`'d script's line goes away. The panel is hidden with the rest of the HUD (F1)
and while the F3 debug screen is open (debug entries set to always on don't hide it). Its position,
background and on/off are in the [configuration](#status-panel).

- [`status.clear`](#statusclear)
- [`status.set`](#statusset)

#### status.set
*Usage:* <code>status.set(name: str, text: str, ttl: float = 2.0, color: int = None)</code>

Shows or updates the line `name: text` for `ttl` seconds. Call it again to renew it.

*Args:*

- `name`: non-empty; one line per name, so give each script its own
- `text`: any value, shown with `str()`; `§` codes work
- `ttl`: seconds, must be > 0
- `color`: `0xRRGGBB` or `0xAARRGGBB` like [`hud.text`](#hudtext); `None` = white

```
while farming():
  status.set("farm", f"{crops} crops", ttl=2)
  time.sleep(1)
```

#### status.clear
*Usage:* <code>status.clear(name: str)</code>

Removes the line `name` now.

### screens module

*Usage:* `import screens  # from Python script`

The open screen (chest, shop menu, inventory…) and its container slots. Clicks are the game's own
slot clicks, the same as clicking with the mouse, without moving the cursor. Names and lore are
plain text.

- [`screens.click`](#screensclick)
- [`screens.click_item`](#screensclick_item)
- [`screens.close`](#screensclose)
- [`screens.find`](#screensfind)
- [`screens.items`](#screensitems)
- [`screens.title`](#screenstitle)
- [`screens.wait_for`](#screenswait_for)

```
minescript.execute("/menu")
if screens.wait_for(r"Shop", timeout=5):
  if not screens.click_item(name_regex=r"^Diamond Sword$", button="shift"):
    minescript.echo("not for sale")
  screens.close()
```

#### screens.title
*Usage:* <code>screens.title() -> str</code>

Gets the open screen's title, or `""` if no screen is open.

#### screens.wait_for
*Usage:* <code>screens.wait_for(title_regex: str, timeout: float = 10) -> bool</code>

Waits until a screen whose title matches `title_regex` (Python `re.search`) is open and, for a
container, its contents have arrived from the server: a non-empty container slot, or 0.5 s after the
screen opened (for empty containers). Polls about every 50 ms (`screens.POLL_SECONDS`).

*Returns:*

- `True`, or `False` after `timeout` seconds

#### screens.items
*Usage:* <code>screens.items() -> list</code>

Gets the non-empty slots of the open container: first the container's own slots, then the player's
inventory (flagged `"player": True`).

*Returns:*

- `[{"slot": int, "id": str, "count": int, "name": str, "lore": [str, ...], "custom_data": dict or None, "damage": int, "max_damage": int, "player": bool}, ...]`
- `slot` is the number to pass to [`click`](#screensclick); `id` e.g. `"minecraft:diamond_sword"`;
  `custom_data` is the item's `minecraft:custom_data` as JSON (NBT number types are lost), or `None`;
  `damage`/`max_damage` are 0 for items without durability. The same dicts as
  [`inventory.items`](#inventoryitems).
- `[]` when no container screen is open

#### screens.find
*Usage:* <code>screens.find(name_regex: str = None, id: str = None, lore_regex: str = None)</code>

Gets the first slot from [`items`](#screensitems) that matches every given filter: `name_regex`
searched in the name, `id` equal to the item id (`"diamond"` means `"minecraft:diamond"`), and
`lore_regex` searched in any lore line.

*Returns:*

- the slot's dict, or `None`

#### screens.click
*Usage:* <code>screens.click(slot: int, button: str = "left") -> bool</code>

Clicks `slot` in the open container.

*Args:*

- `button`: `"left"` (pick up / put down), `"right"` (half / one), `"shift"` (quick move),
  `"middle"` (clone, creative only); anything else raises `ValueError`

*Returns:*

- `False` if no container is open or the slot doesn't exist

#### screens.click_item
*Usage:* <code>screens.click_item(name_regex: str = None, id: str = None, lore_regex: str = None, button: str = "left") -> bool</code>

[`find`](#screensfind) then [`click`](#screensclick).

*Returns:*

- `True` if a matching slot was clicked

#### screens.close
*Usage:* <code>screens.close()</code>

Closes the open screen like Escape. A container is closed on the server too.

### settings module

*Usage:* `import settings  # from Python script`

**Per-script settings** saved as JSON in `minecraft/config/minescriptaddon-settings/<script>.json`, and
editable in game with [`/msettings <script>`](#msettings-command).

- [`settings.load`](#settingsload)
- [`Settings`](#settings)
- [`/msettings`](#msettings-command)

```
s = settings.load("farm", defaults={"radius": 8, "speed": 1.5, "crops": ["wheat", "carrots"], "sneak": True})
while True:
  if s.changed():   # edited with /msettings farm
    s.reload()
  farm(s["radius"], s["crops"])
```

#### settings.load
*Usage:* <code>settings.load(name: str, defaults: dict = None) -> Settings</code>

Loads the settings of script `name` from `<name>.json`. On first use the file is created from `defaults`.
Keys missing from the file get their default (and are added to the file); keys only in the file are kept.

*Args:*

- `name`: letters, digits, `_`, `.` and `-`; anything else (like a path) raises `ValueError`
- `defaults`: values are `bool`, `int`, `float`, `str` or a list of those; anything else raises `TypeError`

A broken file (not JSON, or not a JSON object) doesn't raise: it prints a warning, gives the defaults and is
left as is so you can fix it. A value in the file of the wrong type also gives its default with a warning.

#### Settings
Dict-like settings of one script, returned by [`load`](#settingsload).

| | |
|---|---|
| `s["key"]` | the value (`KeyError` if missing); lists are copies |
| `s["key"] = value` | sets and **saves at once** |
| `s.get(key, default=None)`, `key in s` | like a dict |
| `s.as_dict()` | a copy of every setting |
| `s.changed()` | `True` if the file changed on disk since the last load, reload or save |
| `s.reload()` | re-reads the file |

A value must be `bool`, `int`, `float`, `str` or a list of those, and of its default's type, or setting it
raises `TypeError` (an `int` is fine where the default is a `float`, and is stored as one). If a list's
default holds one element type, the elements must have that type too. Saves are atomic (a temp file, then a
rename), so the editor or another script never reads half a file.

#### /msettings command
*Usage:* `/msettings <script>` (tab-completes scripts that have a settings file)

Opens an editor for `<script>.json`: an ON/OFF toggle for `bool`, a text field for `str`, a number field for
`int`/`float`, and a comma-separated field for lists. Invalid input turns the field red (hover it for why) and
disables **Save**. Saving writes the file atomically; a running script sees the change through
[`changed()`/`reload()`](#settings). Keys that aren't a setting type (kept from the file) are shown but can't
be edited. List items can't contain commas in the editor.

### world_events module

*Usage:* `import world_events  # from Python script`

The **sounds** the client plays (from the server, and the client's own: UI clicks, footsteps, music) and the
**particles** spawned by server packets. The mod keeps the last 500 of each; they're cleared when you leave a
server, but seq numbers keep counting. Use `since` and `latest_*_seq()` like [`chat`](#chat-module)'s.
Patterns are Python regexes searched in the id. `near` is an `(x, y, z)` tuple and `radius` is in blocks; give
both or neither (`ValueError` otherwise). The mod only watches: it never changes or cancels a sound or particle.

- [`world_events.latest_particle_seq`](#world_eventslatest_particle_seq)
- [`world_events.latest_sound_seq`](#world_eventslatest_sound_seq)
- [`world_events.listen_particles`](#world_eventslisten_particles)
- [`world_events.listen_sounds`](#world_eventslisten_sounds)
- [`world_events.particles`](#world_eventsparticles)
- [`world_events.sounds`](#world_eventssounds)
- [`world_events.wait_particle`](#world_eventswait_particle)
- [`world_events.wait_sound`](#world_eventswait_sound)
- [`world_events.Sound`](#world_eventssound)
- [`world_events.Particle`](#world_eventsparticle)

```
seq = world_events.latest_sound_seq()   # e.g. right after casting a fishing rod
if world_events.wait_sound(r"entity\.fishing_bobber\.splash", timeout=30, since=seq,
                           near=minescript.player_position(), radius=24):
  minescript.echo("bite!")
```

#### world_events.latest_sound_seq
*Usage:* <code>world_events.latest_sound_seq() -> int</code>

Seq of the newest recorded sound. Grab it before doing something, then pass it as `since=` so a fast sound
isn't missed.

#### world_events.latest_particle_seq
*Usage:* <code>world_events.latest_particle_seq() -> int</code>

Seq of the newest recorded particle packet.

#### world_events.sounds
*Usage:* <code>world_events.sounds(since: int = None) -> list</code>

Recorded [`Sound`](#world_eventssound)s after seq `since` (`None`: all of the last 500), oldest first.

#### world_events.particles
*Usage:* <code>world_events.particles(since: int = None) -> list</code>

Recorded [`Particle`](#world_eventsparticle)s after seq `since` (`None`: all of the last 500), oldest first.

#### world_events.wait_sound
*Usage:* <code>world_events.wait_sound(id_regex: str, timeout: float = None, near=None, radius: float = None, since: int = None)</code>

Waits for the first sound after `since` (default: now) whose id matches `id_regex`, within `radius` blocks of
`near` if given. Sounds positioned relative to you (UI sounds, music) are never near anything.

*Returns:*

- the [`Sound`](#world_eventssound), or `None` after `timeout` seconds (`None`: wait forever)

#### world_events.wait_particle
*Usage:* <code>world_events.wait_particle(id_regex: str, timeout: float = None, near=None, radius: float = None, since: int = None)</code>

Like [`wait_sound`](#world_eventswait_sound), for particle packets.

*Returns:*

- the [`Particle`](#world_eventsparticle), or `None` after `timeout` seconds

#### world_events.listen_sounds
*Usage:* <code>world_events.listen_sounds(callback, id_regex: str = None)</code>

Calls `callback(sound)` on a background thread for every new sound whose id matches `id_regex` (`None`: all).
Exceptions in the callback are printed and don't stop it.

*Returns:*

- a listener; call `.stop()` to end it

#### world_events.listen_particles
*Usage:* <code>world_events.listen_particles(callback, id_regex: str = None)</code>

Like [`listen_sounds`](#world_eventslisten_sounds), for particle packets.

#### world_events.Sound
A recorded sound:

| Field | |
|---|---|
| `seq` | int, never resets |
| `time` | epoch milliseconds |
| `id` | e.g. `"minecraft:entity.experience_orb.pickup"` |
| `category` | `"master"`, `"music"`, `"record"`, `"weather"`, `"block"`, `"hostile"`, `"neutral"`, `"player"`, `"ambient"`, … |
| `x`, `y`, `z` | where it plays |
| `volume`, `pitch` | as asked for, before the game's volume settings (muted sounds are recorded too) |
| `relative` | `True` for sounds positioned relative to you (UI sounds, music): `x`/`y`/`z` are offsets |

#### world_events.Particle
A recorded particle packet: `seq`, `time`, `id` (e.g. `"minecraft:flame"`), `x`, `y`, `z` and `count` (particles
in the packet; 0 means one).

### inventory module

*Usage:* `import inventory  # from Python script`

The player's **own inventory**, read at any time with no screen open. Read only: nothing here moves or changes
items. Items are the same dicts as [`screens.items`](#screensitems) (without `"player"`), with `slot` in the
inventory's own numbering:

| Slots | |
|---|---|
| 0–8 | hotbar |
| 9–35 | main inventory |
| 36, 37, 38, 39 | feet, legs, chest, head |
| 40 | offhand |

Without a player (main menu, loading) every function returns empty results or `None` instead of raising.

- [`inventory.count`](#inventorycount)
- [`inventory.equipment`](#inventoryequipment)
- [`inventory.find`](#inventoryfind)
- [`inventory.held`](#inventoryheld)
- [`inventory.hotbar`](#inventoryhotbar)
- [`inventory.items`](#inventoryitems)
- [`inventory.selected`](#inventoryselected)
- [`inventory.wait_for`](#inventorywait_for)

```
sword = inventory.find(id="diamond_sword", where="hotbar")
if sword and sword["max_damage"] - sword["damage"] < 50:
  minescript.echo("sword almost broken")
minescript.echo(f"{inventory.count(id='wheat')} wheat")
```

#### inventory.items
*Usage:* <code>inventory.items() -> list</code>

Every non-empty item, by slot.

*Returns:*

- `[{"slot": int, "id": str, "count": int, "name": str, "lore": [str, ...], "custom_data": dict or None, "damage": int, "max_damage": int}, ...]`
- `damage` and `max_damage` are 0 for items without durability; the item breaks when `damage` reaches `max_damage`
- `[]` without a player

#### inventory.hotbar
*Usage:* <code>inventory.hotbar() -> list</code>

9 entries, index = hotbar slot 0–8: the item dict or `None`.

#### inventory.selected
*Usage:* <code>inventory.selected() -> int</code>

The selected hotbar slot, 0–8, or `None` without a player.

#### inventory.held
*Usage:* <code>inventory.held(hand: str = "main")</code>

The item in the `"main"` or `"off"` hand, or `None`. Any other `hand` raises `ValueError`.

#### inventory.equipment
*Usage:* <code>inventory.equipment() -> dict</code>

What the game counts as worn and held, read through the player's equipment slots.

*Returns:*

- `{"head", "chest", "legs", "feet", "mainhand", "offhand"}`, each an item dict (its `slot` is where it sits in the
  inventory) or `None`

#### inventory.find
*Usage:* <code>inventory.find(name_regex: str = None, id: str = None, lore_regex: str = None, where: str = "all")</code>

The first item (by slot) matching every given filter, like [`screens.find`](#screensfind): `name_regex` and
`lore_regex` are searched in the plain text, `id` works with or without `"minecraft:"`.

*Args:*

- `where`: `"all"` (0–40), `"hotbar"` (0–8), `"main"` (9–35) or `"armor"` (36–39)
- at least one filter is required; none, or an unknown `where`, raises `ValueError`

*Returns:*

- the item dict, or `None`

#### inventory.count
*Usage:* <code>inventory.count(id: str = None, name_regex: str = None) -> int</code>

Total count of matching items across the whole inventory (no filter: every item).

#### inventory.wait_for
*Usage:* <code>inventory.wait_for(name_regex: str = None, id: str = None, lore_regex: str = None, timeout: float = None, where: str = "all")</code>

Waits until a matching item (see [`find`](#inventoryfind)) is in the inventory. Polls about every 50 ms.

*Returns:*

- the item dict, or `None` after `timeout` seconds (`None`: wait forever; negative raises `ValueError`)

## Examples

Each example uses its own id prefix and `ttl`, so it cleans up after itself, even after `\killjob`.

**Coordinates in the top-right corner**

```
import time
import hud, minescript

P = "coords."
try:
  while True:
    x, y, z = minescript.player_position()
    s = f"§eXYZ §f{x:.1f} {y:.1f} {z:.1f}"
    w = hud.text_width(s)
    hud.rect(P + "bg", -(w + 10), 4, w + 6, 14, ttl=1)
    hud.text(P + "txt", -(w + 7), 7, s, ttl=1)
    time.sleep(0.1)
finally:
  hud.clear(P)
```

**Toggle an overlay with Minescript key 1**

```
import time
import hud, keys

P = "overlay."
visible = True
try:
  while True:
    if keys.pressed(1) % 2:
      visible = not visible
    if visible:
      hud.text(P + "txt", 4, 40, "§aOverlay on", ttl=1)
    else:
      hud.clear(P)
    time.sleep(0.05)
finally:
  hud.clear(P)
  keys.release(1)
```

**Time a command's reply**

```
import time
import chat, minescript

seq = chat.latest_seq()
start = time.monotonic()
minescript.execute("/list")
line = chat.wait_for(r"players online", timeout=5, since=seq)
minescript.echo(f"{(time.monotonic() - start) * 1000:.0f} ms: {line.text}" if line else "no reply")
```

**Show matching chat lines on the HUD**

```
import re, time
import chat, hud

ALERT = re.compile(r"(?i)\b(boss|rare drop)\b")
seq = chat.latest_seq()
try:
  while True:
    for line in chat.lines(seq):
      seq = line.seq
      if line.source != "actionbar" and ALERT.search(line.text):
        w, h = hud.screen_size()
        hud.text("alerts.txt", (w - hud.text_width(line.text)) // 2, h // 3, line.formatted, ttl=4)
    time.sleep(0.1)
finally:
  hud.clear("alerts.")
```

**Run a clickable chat prompt**

```
import chat, minescript

line = chat.wait_for(r"(?i)click (here )?to accept", timeout=60, source=("chat", "game"))
for action, value in (line.clicks if line else ()):
  if action == "run_command":
    minescript.execute(value)
    break
```

**Hide chat spam while a script runs**

```
import time
import chat

SPAM = r"^\[(Ad|Shop|Tip)\]"
try:
  while True:
    chat.hide(SPAM, ttl=3)
    time.sleep(1)
finally:
  chat.unhide(SPAM)
```

## Troubleshooting

- **`No module named 'hud'`:** the mod isn't loaded, or the game hasn't been started since it was
  installed. Check that `minecraft/minescript/hud.py` exists.
- **HUD elements stay after `\killjob`:** they were drawn without `ttl`. Use `ttl` for anything a
  loop redraws, or clear them now with `\eval "import hud; hud.clear()"`.
- **A box covers its text:** create the box's id before the text's id (draw order is creation
  order).
- **`0x00FF00` isn't transparent:** `0xRRGGBB` is opaque. Use `alpha=0`.
- **`wait_for` misses a fast reply:** take `chat.latest_seq()` before sending and pass `since=`.
- **A pattern doesn't match colored text:** patterns match the plain `text`, not `formatted`.
- **`hide` behaves differently from `wait_for`:** `hide` uses Java regex syntax, `wait_for` uses Python's.
- **No Minescript category in Controls:** `count` is `0`, or the mod isn't loaded.
- **New key slots or labels don't appear:** restart the game. They're only read at startup.
- **A key does nothing:** check that it's bound, no screen is open, `command` starts with `/` or
  `\`, and no script is claiming it.
- **`\command` says "Minescript not found":** Minescript is missing, or its version no longer has
  the method this mod uses to run commands. Update both mods.
- **`ValueError: key slot 4 is outside 1..3`:** raise `count` in the config and restart.
- **No alert sound:** check the **Master** volume. An unknown `sound_id` plays the bell and logs a
  warning.
- **No taskbar flash:** it only flashes while the game window isn't focused.
- **A held key stops after a second:** renew it with `hold()` within `ttl`, or use
  [`inputs.holding`](#inputsholding).
- **`screens.wait_for` returns too early on an empty-looking menu:** contents count as arrived
  after 0.5 s even if empty. Wait for a specific item with `screens.find()` in a loop.
- **`/msettings` says there are no settings yet:** the file is created the first time a script
  calls `settings.load("<script>", ...)`.

Report bugs at <https://github.com/niklas2233/minescript-addon/issues> with your Minecraft,
Fabric API, Minescript and Minescript Addon versions, a minimal script, and the relevant part of
`logs/latest.log`.
