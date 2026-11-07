"""Read every chat line from Minescript. Installed by the Minescript Addon mod (overwritten on launch).

    import chat, minescript
    seq = chat.latest_seq()                       # grab before sending, so a fast reply isn't missed
    minescript.execute("/list")
    line = chat.wait_for(r"players online", timeout=5, since=seq)
    print(line.text if line else "no reply")

    l = chat.listen(lambda line: print(line.source, line.text), pattern="Auto-sprint (on|off)")
    ...
    l.stop()

Sees server chat ("chat"), game messages ("game"), the action bar ("actionbar") and lines other client mods
add to chat ("client"). The mod keeps the last 500 lines (cleared when you leave a server); scripts poll it.
Patterns are Python regexes searched in the plain text, except hide()/unhide(), which use Java regexes.
"""

import re
import threading
import time
import traceback
import json
from dataclasses import dataclass

from java import JavaClass

_Chat = JavaClass("com.niklas2233.minescriptaddon.MinescriptChat")
# Text comes back as ASCII-only JSON (non-ASCII as \uXXXX): Minescript's Java->Python pipe mangles
# non-ASCII on Windows ("§" arrived as "Â§"), and json.loads restores it exactly.

POLL_SECONDS = 0.05


@dataclass(frozen=True)
class Line:
    seq: int
    time: int        # epoch milliseconds
    source: str      # "chat" | "game" | "actionbar" | "client"
    text: str        # plain text, formatting removed
    formatted: str   # text with § codes
    clicks: tuple    # ((action, value), ...) for run_command / suggest_command / open_url, in order
    screen: str      # title of the screen open when it arrived, "" if none


def latest_seq() -> int:
    """Seq of the newest recorded line; pass it as since= to see only lines after now."""
    return _Chat.latestSeq()


def lines(since: int = None, max: int = 500) -> list:
    """Lines after seq `since`, oldest first. None means "from now on": nothing yet."""
    if since is None:
        return []
    raw = json.loads(_Chat.since(int(since), int(max)))
    return [Line(d["seq"], d["time"], d["source"], d["text"], d["formatted"],
                 tuple((c["action"], c["value"]) for c in d["clicks"]), d["screen"]) for d in raw]


def _matches(line: Line, regex, source) -> bool:
    if source is not None and line.source not in ((source,) if isinstance(source, str) else source):
        return False
    return regex is None or regex.search(line.text) is not None


def wait_for(pattern: str, timeout: float = 10, source=None, since: int = None):
    """First line after `since` (default: now) whose plain text matches `pattern`, or None after `timeout` s.
    source: one source name or a tuple of them."""
    regex = re.compile(pattern)
    seq = latest_seq() if since is None else since
    deadline = time.monotonic() + timeout
    while True:
        for line in lines(seq):
            seq = line.seq
            if _matches(line, regex, source):
                return line
        if time.monotonic() >= deadline:
            return None
        time.sleep(POLL_SECONDS)


class Listener:
    """Calls callback(line) on a background thread for each new matching line until stop()."""

    def __init__(self, callback, pattern=None, source=None, since=None):
        self._callback = callback
        self._regex = re.compile(pattern) if pattern is not None else None
        self._source = source
        self._seq = latest_seq() if since is None else since
        self._stopped = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        while not self._stopped.is_set():
            for line in lines(self._seq):
                self._seq = line.seq
                if self._stopped.is_set():
                    return
                if _matches(line, self._regex, self._source):
                    try:
                        self._callback(line)
                    except Exception:
                        traceback.print_exc()
            self._stopped.wait(POLL_SECONDS)

    def stop(self):
        self._stopped.set()
        if threading.current_thread() is not self.thread:
            self.thread.join(1)


def listen(callback, pattern: str = None, source=None, since: int = None) -> Listener:
    return Listener(callback, pattern, source, since)


def action_bar() -> str:
    """Plain text of the latest action bar message, or "" (none yet, or left the server)."""
    return json.loads(_Chat.actionBar())


def hide(pattern: str, ttl: float = None):
    """Keep chat-box lines whose plain text matches the Java regex `pattern` out of the chat box. They are still
    recorded here, and nothing outside the chat box (e.g. the action bar) is hidden. ttl=seconds expires the rule
    (refresh it in a loop so a killed script's rules go away); 0/None keeps it until unhide()."""
    _Chat.hide(str(pattern), float(ttl or 0))


def unhide(pattern: str):
    _Chat.unhide(str(pattern))
