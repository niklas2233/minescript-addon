"""Tests keys.py against a fake MinescriptKeys bridge. Run: python -B -m unittest discover tests"""
import pathlib
import sys
import time
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import keys  # noqa: E402


class FakeKeys:
    """Mimics MinescriptKeys: 3 slots, take(slot, max, ttl) consumes queued presses."""

    def __init__(self, count=3):
        self.n, self.calls, self.queued = count, [], {}
        self.on_take = None

    def count(self):
        return self.n

    def claim(self, slot, ttl):
        self.calls.append(("claim", slot, ttl))

    def take(self, slot, max, ttl):
        assert type(max) is int and type(ttl) is float  # Java (int, int, double) overload
        self.calls.append(("take", slot, max, ttl))
        if self.on_take:
            self.on_take()
        n = self.queued.get(slot, 0)
        got = min(n, max) if max > 0 else n
        self.queued[slot] = n - got
        return got

    def release(self, slot):
        self.calls.append(("release", slot))

    def binding(self, slot):
        return {1: "R"}.get(slot, "")


class KeysTest(unittest.TestCase):
    def setUp(self):
        self.fake = keys._Keys = FakeKeys()
        keys._ttl.clear()
        keys.POLL_SECONDS = 0.01

    def test_claim_renewal_reuses_ttl(self):
        keys.claim(2, ttl=0.5)
        keys.pressed(2)
        keys.wait(2, timeout=0)
        keys.pressed(1)                       # never claimed: default ttl
        self.assertEqual(self.fake.calls, [("claim", 2, 0.5), ("take", 2, 0, 0.5), ("take", 2, 1, 0.5),
                                           ("take", 1, 0, 2.0)])
        keys.release(2)
        keys.pressed(2)                       # released: back to the default ttl
        self.assertEqual(self.fake.calls[-2:], [("release", 2), ("take", 2, 0, 2.0)])

    def test_claim_ttl_must_be_positive(self):
        for bad in (0, -1, None):
            with self.assertRaises(ValueError):
                keys.claim(1, ttl=bad)

    def test_pressed_counts_and_consumes(self):
        self.fake.queued[1] = 3
        self.assertEqual(keys.pressed(1), 3)
        self.assertEqual(keys.pressed(1), 0)

    def test_wait_takes_one_press_at_a_time(self):
        self.fake.queued[3] = 2
        self.assertTrue(keys.wait(3, timeout=0))
        self.assertTrue(keys.wait(3, timeout=0))
        self.assertFalse(keys.wait(3, timeout=0))

    def test_wait_returns_on_later_press(self):
        takes = []

        def on_take():
            takes.append(1)
            if len(takes) == 3:
                self.fake.queued[1] = 1
        self.fake.on_take = on_take
        self.assertTrue(keys.wait(1, timeout=2))
        self.assertEqual(len(takes), 3, "polls until the press arrives, renewing the claim each time")

    def test_wait_timeout(self):
        start = time.monotonic()
        self.assertFalse(keys.wait(1, timeout=0.1))
        took = time.monotonic() - start
        self.assertGreaterEqual(took, 0.1)
        self.assertLess(took, 1)
        self.assertGreater(sum(1 for c in self.fake.calls if c[0] == "take"), 2, "kept polling")

    def test_slot_range(self):
        for bad in (0, 4, -1, True, 1.0, "1"):
            with self.assertRaises(ValueError) as e:
                keys.pressed(bad)
            self.assertIn("count is 3", str(e.exception))
        for fn in (keys.claim, keys.release, keys.binding, keys.wait):
            with self.assertRaises(ValueError):
                fn(4)
        self.assertEqual(self.fake.calls, [], "nothing reaches Java for a bad slot")
        self.fake.n = 0
        with self.assertRaisesRegex(ValueError, r"outside 1\.\.0"):
            keys.claim(1)

    def test_count_and_binding(self):
        self.assertEqual(keys.count(), 3)
        self.assertEqual(keys.binding(1), "R")
        self.assertEqual(keys.binding(2), "")


if __name__ == "__main__":
    unittest.main()
