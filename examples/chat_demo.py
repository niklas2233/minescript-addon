r"""In-game check for chat.py. Not installed by the mod: copy to .minecraft/minescript/ and run \chat_demo.

    \chat_demo                       sends /list and waits for its "players online" reply
    \chat_demo /help "help"          sends your own harmless command and waits for a reply matching the regex

Each step is reported in chat: OK, or the exception it raised.
"""
import sys
import time

import minescript

try:
    import chat
except Exception as e:  # mod missing, or the java bridge failed
    minescript.echo(f"[chat_demo] import chat FAILED: {type(e).__name__}: {e}")
    raise

COMMAND = sys.argv[1] if len(sys.argv) > 1 else "/list"
REPLY = sys.argv[2] if len(sys.argv) > 2 else r"players online|Unknown or incomplete command|Unknown command"


def step(name, fn):
    try:
        fn()
        minescript.echo(f"[chat_demo] {name}: OK")
    except Exception as e:
        minescript.echo(f"[chat_demo] {name}: FAILED: {type(e).__name__}: {e}")


def last_five():
    latest = chat.latest_seq()
    minescript.echo(f"[chat_demo] latest_seq() = {latest!r}")
    for line in chat.lines(max(0, latest - 5)):
        clicks = ", ".join(f"{a}={v}" for a, v in line.clicks) or "-"
        screen = f" screen={line.screen!r}" if line.screen else ""
        minescript.echo(f"  #{line.seq} [{line.source}] {line.text!r} clicks: {clicks}{screen}")


def command_reply():
    seq = chat.latest_seq()                  # before sending, so a fast reply isn't missed
    start = time.monotonic()
    minescript.execute(COMMAND)
    line = chat.wait_for(REPLY, timeout=5, since=seq)
    took = (time.monotonic() - start) * 1000
    if line is None:
        raise TimeoutError(f"no line matching {REPLY!r} within 5 s after {COMMAND}")
    minescript.echo(f"[chat_demo] {COMMAND} reply after {took:.0f} ms [{line.source}]: {line.text!r}")


def action_bar():
    for i in range(3):
        minescript.echo(f"[chat_demo] action_bar() #{i + 1}: {chat.action_bar()!r}")
        if i < 2:
            time.sleep(1)


step("last 5 lines", last_five)
step(f"wait_for reply to {COMMAND}", command_reply)
step("action bar x3", action_bar)
minescript.echo("[chat_demo] done")
