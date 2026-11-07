"""Sounds the client plays and particles the server sends, for Minescript scripts. Installed by the Minescript
Addon mod (overwritten on launch).

    import world_events
    seq = world_events.latest_sound_seq()          # grab before acting, so a fast sound isn't missed
    ...
    s = world_events.wait_sound(r"entity\\.experience_orb\\.pickup", timeout=5, since=seq)
    p = world_events.wait_particle(r"^minecraft:(flame|crit)$", near=(100, 64, -20), radius=8)

    l = world_events.listen_sounds(lambda s: print(s.id, s.x, s.y, s.z), id_regex=r"note_block")
    ...
    l.stop()

The mod keeps the last 500 of each (cleared when you leave a server; seq numbers keep counting). Sounds are
every sound the client plays: from the server, and the client's own (UI clicks, footsteps, music). Particles
are those spawned by server packets. Patterns are Python regexes searched in the id, e.g.
"minecraft:block.note_block.bell". near is an (x, y, z) tuple and radius is in blocks; give both or neither.
"""

import json
import re
import threading
import time
import traceback
from dataclasses import dataclass

from java import JavaClass

_Events = JavaClass("com.niklas2233.minescriptaddon.MinescriptWorldEvents")

POLL_SECONDS = 0.05


@dataclass(frozen=True)
class Sound:
    seq: int
    time: int        # epoch milliseconds
    id: str          # e.g. "minecraft:entity.experience_orb.pickup"
    category: str    # "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", ...
    x: float
    y: float
    z: float
    volume: float    # as asked for (before the game's volume settings)
    pitch: float
    relative: bool   # positioned relative to you (UI sounds, music; x/y/z are offsets): never "near" anything


@dataclass(frozen=True)
class Particle:
    seq: int
    time: int        # epoch milliseconds
    id: str          # e.g. "minecraft:flame"
    x: float
    y: float
    z: float
    count: int       # particles in the packet (0 means one, with exact speed)


_KINDS = {"sound": (Sound, "sounds"), "particle": (Particle, "particles")}


def latest_sound_seq() -> int:
    """Seq of the newest recorded sound; pass it as since= to see only sounds after now."""
    return _Events.latestSoundSeq()


def latest_particle_seq() -> int:
    """Seq of the newest recorded particle packet; pass it as since= to see only particles after now."""
    return _Events.latestParticleSeq()


def _near(near, radius):
    """(x, y, z, radius) for the Java side; radius -1 means no filter."""
    if near is None and radius is None:
        return 0.0, 0.0, 0.0, -1.0
    if near is None or radius is None:
        raise ValueError("near and radius go together: near=(x, y, z), radius=blocks")
    if len(near) != 3:
        raise ValueError(f"near must be (x, y, z), not {near!r}")
    if radius < 0:
        raise ValueError(f"radius must be >= 0, not {radius!r}")
    x, y, z = near
    return float(x), float(y), float(z), float(radius)


def _page(kind, since, near=None, radius=None):
    """(latest seq, [events after since, near near]) from the Java ring buffer."""
    cls, method = _KINDS[kind]
    page = json.loads(getattr(_Events, method)(int(since), *_near(near, radius)))
    return page["latest"], [cls(**e) for e in page["events"]]


def sounds(since: int = None) -> list:
    """Recorded sounds after seq `since` (None: all of the last 500), oldest first."""
    return _page("sound", since or 0)[1]


def particles(since: int = None) -> list:
    """Recorded particle packets after seq `since` (None: all of the last 500), oldest first."""
    return _page("particle", since or 0)[1]


def _wait(kind, id_regex, timeout, near, radius, since):
    regex = re.compile(id_regex)
    _near(near, radius)  # validate before waiting
    seq = (latest_sound_seq() if kind == "sound" else latest_particle_seq()) if since is None else since
    deadline = None if timeout is None else time.monotonic() + timeout
    while True:
        latest, events = _page(kind, seq, near, radius)
        for e in events:
            if regex.search(e.id):
                return e
        seq = latest
        if deadline is not None and time.monotonic() >= deadline:
            return None
        time.sleep(POLL_SECONDS)


def wait_sound(id_regex: str, timeout: float = None, near=None, radius: float = None, since: int = None):
    """First sound after `since` (default: now) whose id matches id_regex, within radius blocks of near if given;
    None after timeout seconds (None: wait forever)."""
    return _wait("sound", id_regex, timeout, near, radius, since)


def wait_particle(id_regex: str, timeout: float = None, near=None, radius: float = None, since: int = None):
    """First particle packet after `since` (default: now) whose id matches id_regex, within radius blocks of near
    if given; None after timeout seconds (None: wait forever)."""
    return _wait("particle", id_regex, timeout, near, radius, since)


class Listener:
    """Calls callback(event) on a background thread for each new matching event until stop()."""

    def __init__(self, kind, callback, id_regex=None):
        self._kind = kind
        self._callback = callback
        self._regex = re.compile(id_regex) if id_regex is not None else None
        self._seq = latest_sound_seq() if kind == "sound" else latest_particle_seq()
        self._stopped = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        while not self._stopped.is_set():
            latest, events = _page(self._kind, self._seq)
            for e in events:
                if self._stopped.is_set():
                    return
                if self._regex is None or self._regex.search(e.id):
                    try:
                        self._callback(e)
                    except Exception:
                        traceback.print_exc()
            self._seq = latest
            self._stopped.wait(POLL_SECONDS)

    def stop(self):
        self._stopped.set()
        if threading.current_thread() is not self.thread:
            self.thread.join(1)


def listen_sounds(callback, id_regex: str = None) -> Listener:
    """callback(sound) for every new sound whose id matches id_regex (None: all) until .stop()."""
    return Listener("sound", callback, id_regex)


def listen_particles(callback, id_regex: str = None) -> Listener:
    """callback(particle) for every new particle packet whose id matches id_regex (None: all) until .stop()."""
    return Listener("particle", callback, id_regex)
