"""Sidebar scoreboard, tab list and titles for Minescript scripts. Installed by the Minescript Addon mod
(overwritten on launch).

    import screen_info
    board = screen_info.sidebar()
    print(board["title"], board["lines"])               # top to bottom, as displayed
    for p in screen_info.tab_list()["players"]:
        print(p["name"], p["ping"], p["gamemode"])
    t = screen_info.title()                             # None when no title is on screen
    if t: print(t["title"], t["subtitle"], t["seconds_left"])

Every text comes as plain text plus a "formatted_" variant with § codes (like chat.py's text/formatted).
Each call is one snapshot read on the game thread; outside a world the values are empty.
Titles and subtitles are also recorded in chat.py with sources "title" and "subtitle", so
chat.wait_for(..., source="title") works for them.
"""

import json

from java import JavaClass

_Info = JavaClass("com.niklas2233.minescriptaddon.MinescriptScreenInfo")


def sidebar() -> dict:
    """{"title", "formatted_title", "lines", "formatted_lines", "scores"}: the sidebar scoreboard, lines top to
    bottom as displayed (at most 15); scores are the right-hand numbers as shown ("" when hidden)."""
    return json.loads(_Info.sidebar())  # ASCII-only JSON, see chat.py


def tab_list() -> dict:
    """{"header", "formatted_header", "footer", "formatted_footer", "players": [{"name", "display",
    "formatted_display", "ping", "gamemode"}, ...]}, players in tab-list order (at most 80, like the game)."""
    return json.loads(_Info.tabList())


def title():
    """{"title", "formatted_title", "subtitle", "formatted_subtitle", "seconds_left"} for the title on screen,
    or None."""
    return json.loads(_Info.title())
