"""Tests alert.py and inputs.py against fake bridges. Run: python -B -m unittest discover tests"""
import json
import pathlib
import sys
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import alert  # noqa: E402
import inputs  # noqa: E402


class Recorder:
    """Records every bridge call as (method, *args)."""

    def __init__(self, returns=None):
        self.calls, self.returns = [], returns or {}

    def __getattr__(self, name):
        def call(*args):
            self.calls.append((name, *args))
            return self.returns.get(name)
        return call


class AlertTest(unittest.TestCase):
    def setUp(self):
        self.fake = alert._Alert = Recorder()

    def test_defaults(self):
        alert.alert("Inventory full")
        self.assertEqual(self.fake.calls, [("alert", "Minescript", "Inventory full", True,
                                            "minecraft:block.note_block.bell", 1.0, 1.0, 1, True, True, False)])

    def test_arguments_and_java_types(self):
        alert.alert("Done", sound=False, toast=False, flash=False, chat=True, repeat=3, title="Farm",
                    sound_id="minecraft:entity.experience_orb.pickup", volume=1, pitch=2)
        call = self.fake.calls[0]
        self.assertEqual(call, ("alert", "Farm", "Done", False, "minecraft:entity.experience_orb.pickup",
                                1.0, 2.0, 3, False, False, True))
        # Java (String, String, boolean, String, double, double, int, boolean, boolean, boolean) overload
        self.assertEqual([type(a) for a in call[1:]], [str, str, bool, str, float, float, int, bool, bool, bool])


class InputsTest(unittest.TestCase):
    def setUp(self):
        self.fake = inputs._Inputs = Recorder()

    def test_hold_defaults_and_release(self):
        inputs.hold("attack")
        inputs.hold("forward", ttl=2, owner="walker")
        inputs.hold("attack", False)
        self.assertEqual(self.fake.calls, [("hold", "attack", "", 1.0), ("hold", "forward", "walker", 2.0),
                                           ("release", "attack")])
        self.assertIs(type(self.fake.calls[1][3]), float)

    def test_unknown_key_lists_valid_ones(self):
        for bad in ("jumpp", "Attack", None, 1):
            with self.assertRaises(ValueError) as e:
                inputs.hold(bad)
            self.assertIn("attack, use, forward, back, left, right, jump, sneak, sprint, pick", str(e.exception))
        with self.assertRaises(ValueError):
            inputs.hold("nope", False)
        with self.assertRaises(ValueError):
            inputs.holding("nope").__enter__()
        self.assertEqual(self.fake.calls, [], "nothing reaches Java for a bad key")

    def test_ttl_must_be_positive(self):
        for bad in (0, -1, None):
            with self.assertRaises(ValueError):
                inputs.hold("use", ttl=bad)
        inputs.hold("use", False, ttl=0)  # releasing ignores ttl
        self.assertEqual(self.fake.calls, [("release", "use")])

    def test_release_all_and_held(self):
        inputs.release_all()
        inputs.release_all("farm.")
        self.assertEqual(self.fake.calls, [("releaseAll", ""), ("releaseAll", "farm.")])
        self.fake.returns["held"] = json.dumps([["attack", "farm.left", 0.75]])
        self.assertEqual(inputs.held(), [("attack", "farm.left", 0.75)])

    def test_holding_renews_then_releases(self):
        with inputs.holding("sneak", owner="x", ttl=0.03):
            import time
            time.sleep(0.1)
        holds = [c for c in self.fake.calls if c[0] == "hold"]
        self.assertGreater(len(holds), 1, "renewed from the background thread")
        self.assertTrue(all(c == ("hold", "sneak", "x", 0.03) for c in holds))
        self.assertEqual(self.fake.calls[-1], ("release", "sneak"), "released on exit, after the last renewal")


if __name__ == "__main__":
    unittest.main()
