"""Per-script settings saved as JSON, editable in game with /msettings <script>. Installed by the Minescript
Addon mod (overwritten on launch).

    import settings
    s = settings.load("farm", defaults={"radius": 8, "speed": 1.5, "crops": ["wheat", "carrots"], "sneak": True})
    radius = s["radius"]
    s["radius"] = 12                      # saved at once
    ...
    if s.changed():                       # edited on disk, e.g. with /msettings farm
        s.reload()

The file is minecraft/config/minescriptaddon-settings/<script>.json. Values are bool, int, float, str or lists
of those; a value must have its default's type (an int is fine for a float). Saves are atomic (temp file +
rename), so a reader never sees half a file.
"""

import json
import math
import os
import re
import sys
import tempfile

DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config", "minescriptaddon-settings")
NAME = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.-]*\Z")  # same as Settings.NAME in Java
_TYPES = (bool, int, float, str)
_NO_DEFAULT = object()


def _fits(value, want):
    """value converted to type want (int -> float), or None if it doesn't fit."""
    if type(value) is want:
        return value
    if want is float and type(value) is int:
        return float(value)
    return None


def _check(key, value, default=_NO_DEFAULT):
    """The value to store for key (copied, ints made floats where the default is a float); TypeError if it can't be."""
    if type(value) is list:
        if not all(type(v) in _TYPES for v in value):
            raise TypeError(f"setting {key!r}: lists may only hold bool, int, float or str, not {value!r}")
        value = list(value)
    elif type(value) not in _TYPES:
        raise TypeError(f"setting {key!r}: {value!r} is a {type(value).__name__}; settings are bool, int, float, "
                        "str or a list of those")
    if any(type(v) is float and not math.isfinite(v) for v in (value if type(value) is list else [value])):
        raise ValueError(f"setting {key!r}: {value!r} isn't a finite number")
    if default is _NO_DEFAULT:
        return value
    fitted = _fits(value, type(default))
    if fitted is None:
        raise TypeError(f"setting {key!r} must be a {type(default).__name__} like its default {default!r}, "
                        f"not {type(value).__name__} {value!r}")
    if type(default) is list and default and len({type(v) for v in default}) == 1:
        want = type(default[0])  # a list default of one element type fixes the elements' type too
        items = [_fits(v, want) for v in fitted]
        if None in items:
            raise TypeError(f"setting {key!r}: every element must be a {want.__name__} like its default {default!r}, "
                            f"not {value!r}")
        fitted = items
    return fitted


def _stamp(path):
    try:
        st = os.stat(path)
        return st.st_mtime_ns, st.st_size
    except OSError:
        return None


def _warn(message):
    print(f"settings: {message}", file=sys.stderr)


class Settings:
    """Dict-like settings of one script; see load()."""

    def __init__(self, name: str, defaults: dict):
        self.name = name
        self.path = os.path.join(DIR, name + ".json")
        self._defaults = {k: _check(k, v) for k, v in defaults.items()}
        self.reload()

    def reload(self):
        """Re-read the file. A broken file logs a warning and gives the defaults; the file is left as is."""
        values = {k: list(v) if type(v) is list else v for k, v in self._defaults.items()}
        try:
            with open(self.path, encoding="utf-8") as f:
                data = json.load(f)
            if type(data) is not dict:
                raise ValueError("not a JSON object")
        except FileNotFoundError:
            self._values = values
            self._save()  # first use: create it from the defaults
            return
        except (OSError, ValueError) as e:  # json.JSONDecodeError and UnicodeDecodeError are ValueErrors
            _warn(f"{self.path} is broken ({e}); using the defaults and leaving the file as is")
            self._values, self._stamp = values, _stamp(self.path)
            return
        bad = False
        for k, v in data.items():
            if k not in self._defaults:
                values[k] = v  # not ours: kept as is
                continue
            try:
                values[k] = _check(k, v, self._defaults[k])
            except (TypeError, ValueError) as e:
                _warn(f"{self.path}: {e}; using the default")
                bad = True
        self._values, self._stamp = values, _stamp(self.path)
        if not bad and any(k not in data for k in self._defaults):
            self._save()  # add new defaults to the file, so /msettings lists them

    def changed(self) -> bool:
        """True if the file changed on disk since the last load, reload or save."""
        return _stamp(self.path) != self._stamp

    def _save(self):
        os.makedirs(DIR, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=DIR, prefix=self.name + ".json.", suffix=".tmp")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(self._values, f, indent=2, ensure_ascii=False)
                f.write("\n")
            os.replace(tmp, self.path)
        except BaseException:
            try:
                os.remove(tmp)
            except OSError:
                pass
            raise
        self._stamp = _stamp(self.path)

    def __getitem__(self, key):
        value = self._values[key]
        return list(value) if type(value) is list else value

    def __setitem__(self, key, value):
        """Set and save at once. TypeError if value isn't a setting type or not its default's type."""
        if type(key) is not str:
            raise TypeError(f"setting names are str, not {key!r}")
        self._values[key] = _check(key, value, self._defaults.get(key, _NO_DEFAULT))
        self._save()

    def get(self, key, default=None):
        return self[key] if key in self._values else default

    def __contains__(self, key):
        return key in self._values

    def as_dict(self) -> dict:
        """A copy of every setting."""
        return {k: self[k] for k in self._values}

    def __repr__(self):
        return f"Settings({self.name!r}, {self._values!r})"


def load(name: str, defaults: dict = None) -> Settings:
    """The settings of script name, backed by config/minescriptaddon-settings/<name>.json (created from defaults).
    Keys missing from the file get their default; keys only in the file are kept."""
    if type(name) is not str or not NAME.match(name):
        raise ValueError(f"settings name must be letters, digits, '_', '.' or '-' (no path), not {name!r}")
    return Settings(name, defaults or {})
