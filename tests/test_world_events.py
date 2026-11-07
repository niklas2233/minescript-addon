"""Tests world_events.py against a fake MinescriptWorldEvents bridge. Run: python -B -m unittest discover tests"""
import json
import math
import pathlib
import sys
import threading
import time
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import world_events  # noqa: E402


class FakeEvents:
    """Mirrors EventLog: seq after, radius < 0 = no filter, relative sounds never near."""

    def __init__(self):
        self.sound_log, self.particle_log, self.calls = [], [], []
        self.lock = threading.Lock()

    def sound(self, id, x=0.0, y=64.0, z=0.0, relative=False):
        with self.lock:
            self.sound_log.append({"seq": len(self.sound_log) + 1, "time": 1, "id": id, "category": "record",
                                   "x": x, "y": y, "z": z, "volume": 1.0, "pitch": 1.0, "relative": relative})

    def particle(self, id, x=0.0, y=64.0, z=0.0):
        with self.lock:
            self.particle_log.append({"seq": len(self.particle_log) + 1, "time": 1, "id": id,
                                      "x": x, "y": y, "z": z, "count": 3})

    def _page(self, log, after, x, y, z, radius):
        assert type(after) is int and all(type(v) is float for v in (x, y, z, radius))  # Java (int, double...)
        self.calls.append((after, x, y, z, radius))
        with self.lock:
            out = [e for e in log if e["seq"] > after and (radius < 0 or (
                not e.get("relative") and math.dist((e["x"], e["y"], e["z"]), (x, y, z)) <= radius))]
            return json.dumps({"latest": len(log), "events": out})

    def latestSoundSeq(self):
        return len(self.sound_log)

    def latestParticleSeq(self):
        return len(self.particle_log)

    def sounds(self, after, x, y, z, radius):
        return self._page(self.sound_log, after, x, y, z, radius)

    def particles(self, after, x, y, z, radius):
        return self._page(self.particle_log, after, x, y, z, radius)


class WorldEventsTest(unittest.TestCase):
    def setUp(self):
        self.fake = world_events._Events = FakeEvents()
        world_events.POLL_SECONDS = 0.01

    def later(self, fn, delay=0.05):
        threading.Timer(delay, fn).start()

    def test_lists_and_seqs(self):
        self.fake.sound("minecraft:ui.button.click", relative=True)
        self.fake.sound("minecraft:block.note_block.bell", x=1.5)
        self.fake.particle("minecraft:flame")
        self.assertEqual((world_events.latest_sound_seq(), world_events.latest_particle_seq()), (2, 1))
        all_sounds = world_events.sounds()
        self.assertEqual([s.id for s in all_sounds], ["minecraft:ui.button.click", "minecraft:block.note_block.bell"])
        self.assertEqual((all_sounds[0].relative, all_sounds[1].x, all_sounds[1].category), (True, 1.5, "record"))
        self.assertEqual([s.seq for s in world_events.sounds(since=1)], [2])
        p = world_events.particles()[0]
        self.assertEqual((p.id, p.count), ("minecraft:flame", 3))
        self.assertEqual(world_events.particles(since=1), [])

    def test_wait_sound_matches_id_regex_after_now(self):
        self.fake.sound("minecraft:block.note_block.bell")  # before the wait: ignored
        self.later(lambda: (self.fake.sound("minecraft:entity.pig.ambient"), self.fake.sound("minecraft:block.note_block.bell")))
        s = world_events.wait_sound(r"note_block\.bell", timeout=2)
        self.assertEqual(s.seq, 3)
        self.assertIsNone(world_events.wait_sound("nothing", timeout=0.05))

    def test_wait_with_since_and_near(self):
        self.fake.sound("minecraft:block.note_block.bell", x=50)
        self.fake.sound("minecraft:block.note_block.bell", x=2)
        s = world_events.wait_sound("bell", timeout=1, since=0, near=(0, 64, 0), radius=5)
        self.assertEqual(s.seq, 2)
        self.assertEqual(self.fake.calls[-1], (0, 0.0, 64.0, 0.0, 5.0))
        self.later(lambda: self.fake.particle("minecraft:crit", x=1))
        p = world_events.wait_particle(r"^minecraft:crit$", timeout=2, near=(0, 64, 0), radius=2)
        self.assertEqual(p.id, "minecraft:crit")

    def test_wait_skips_past_non_matching_events(self):
        for _ in range(3):
            self.fake.sound("minecraft:entity.pig.ambient")
        self.assertIsNone(world_events.wait_sound("bell", timeout=0.05, since=0))
        self.assertEqual(self.fake.calls[-1][0], 3)  # polled from latest, not from 0 again

    def test_near_validation(self):
        for kwargs in ({"near": (0, 0, 0)}, {"radius": 3}, {"near": (0, 0), "radius": 3}, {"near": (0, 0, 0), "radius": -1}):
            with self.assertRaises(ValueError, msg=kwargs):
                world_events.wait_sound("x", timeout=0, **kwargs)
        with self.assertRaises(ValueError):
            world_events.wait_particle("x", timeout=0, radius=1)

    def test_listen_and_stop(self):
        self.fake.sound("minecraft:block.note_block.bell")  # before listening: not delivered
        got, particles = [], []
        sl = world_events.listen_sounds(got.append, id_regex="bell")
        pl = world_events.listen_particles(particles.append)
        self.fake.sound("minecraft:entity.pig.ambient")
        self.fake.sound("minecraft:block.note_block.bell")
        self.fake.particle("minecraft:flame")
        deadline = time.monotonic() + 2
        while (len(got) < 1 or len(particles) < 1) and time.monotonic() < deadline:
            time.sleep(0.01)
        sl.stop()
        pl.stop()
        self.fake.sound("minecraft:block.note_block.bell")
        time.sleep(0.05)
        self.assertEqual([s.seq for s in got], [3])
        self.assertEqual([p.id for p in particles], ["minecraft:flame"])
        self.assertFalse(sl.thread.is_alive() or pl.thread.is_alive())


if __name__ == "__main__":
    unittest.main()
