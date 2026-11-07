"""Tests ticks.py against a fake MinescriptTicks bridge. Run: python -B -m unittest discover tests"""
import contextlib
import pathlib
import sys
import types
import unittest


class ScriptLoop:
    """Stub for minescript_runtime.script_loop that records whether a call ran inside it."""
    depth = 0

    def __enter__(self):
        ScriptLoop.depth += 1

    def __exit__(self, *exc):
        ScriptLoop.depth -= 1


sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.modules["minescript_runtime"] = types.SimpleNamespace(script_loop=ScriptLoop())
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import ticks  # noqa: E402


class FakeTicks:
    """Mimics MinescriptTicks: waitFor(target, ms) 'blocks' and the clock advances by ticks_per_call."""

    def __init__(self, ticks_per_call=0):
        self.n, self.per_call, self.calls = 10, ticks_per_call, []

    def count(self):
        assert ScriptLoop.depth == 1
        return self.n

    def waitFor(self, target, ms):
        assert ScriptLoop.depth == 1, "blocking calls must run in the script loop, not the render thread"
        assert type(target) is int and type(ms) is int  # Java (int, int) overload
        self.calls.append(ms)
        self.n += self.per_call
        return self.n

    def timeSince(self, count):
        assert type(count) is int
        return -1.0 if count > self.n else (self.n - count) * 0.05


class TicksTest(unittest.TestCase):
    def test_wait_returns_new_count(self):
        ticks._Ticks = fake = FakeTicks(ticks_per_call=1)
        self.assertEqual(ticks.wait(), 11)
        self.assertEqual(fake.calls, [100])
        self.assertEqual(ticks.wait(3), 14, "n > 1: keeps calling until the target tick")
        self.assertEqual(fake.calls, [100, 100, 100, 100])

    def test_wait_timeout_spends_budget_in_chunks(self):
        ticks._Ticks = fake = FakeTicks(ticks_per_call=0)
        self.assertIsNone(ticks.wait(1, timeout=0.25))
        self.assertEqual(fake.calls, [100, 100, 50])
        fake.calls.clear()
        self.assertIsNone(ticks.wait(1, timeout=0))
        self.assertEqual(fake.calls, [0], "timeout=0 checks once")

    def test_wait_validates_n(self):
        ticks._Ticks = FakeTicks()
        for bad in (0, -1, 1.5, "2", True):
            with self.assertRaises(ValueError):
                ticks.wait(bad)

    def test_count_and_time_since(self):
        ticks._Ticks = FakeTicks()
        self.assertEqual(ticks.count(), 10)
        self.assertAlmostEqual(ticks.time_since(6), 0.2)
        with self.assertRaisesRegex(ValueError, "hasn't ended"):
            ticks.time_since(11)


if __name__ == "__main__":
    unittest.main()
