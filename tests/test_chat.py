"""Tests chat.py against a fake MinescriptChat bridge. Run: python -B -m unittest discover tests"""
import json
import pathlib
import sys
import threading
import time
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import chat  # noqa: E402


class FakeChat:
    """Mimics MinescriptChat: int seq, since() returns one JSON string."""

    def __init__(self):
        self.lines, self.calls, self.lock = [], [], threading.Lock()
        self.on_poll = None  # called on each since(), to add lines "while" a script waits

    def add(self, text, source="game", clicks=()):
        with self.lock:
            seq = len(self.lines) + 1
            self.lines.append({"seq": seq, "time": 1700000000000 + seq, "source": source, "text": text,
                               "formatted": "§a" + text, "screen": "",
                               "clicks": [{"action": a, "value": v} for a, v in clicks]})
            return seq

    def latestSeq(self):
        with self.lock:
            return len(self.lines)

    def since(self, seq, max):
        assert type(seq) is int and type(max) is int  # Java int overload
        if self.on_poll:
            self.on_poll()
        with self.lock:
            return json.dumps([l for l in self.lines if l["seq"] > seq][:max])

    def actionBar(self):
        return "You may not rest now, there are monsters nearby"

    def hide(self, regex, ttl):
        self.calls.append(("hide", regex, ttl))

    def unhide(self, regex):
        self.calls.append(("unhide", regex))


class ChatTest(unittest.TestCase):
    def setUp(self):
        self.fake = chat._Chat = FakeChat()
        chat.POLL_SECONDS = 0.01

    def test_lines_parses_since(self):
        self.fake.add("old")
        self.fake.add("Click here", "chat", [("run_command", "/help"), ("open_url", "https://x.y")])
        self.assertEqual(chat.lines(None), [], "None = from now on")
        got = chat.lines(1)
        self.assertEqual(got, [chat.Line(2, 1700000000002, "chat", "Click here", "§aClick here",
                                         (("run_command", "/help"), ("open_url", "https://x.y")), "")])
        self.assertEqual([l.text for l in chat.lines(0, max=1)], ["old"])
        self.assertEqual(chat.lines(2), [])

    def test_wait_for_matches_later_line_with_source(self):
        polls = []

        def on_poll():
            polls.append(1)
            if len(polls) == 2:
                self.fake.add("Sprinting", "actionbar")         # doesn't match: skipped
                self.fake.add("[MyMod] Auto-sprint on", "client")
        self.fake.on_poll = on_poll
        line = chat.wait_for(r"Auto-sprint (on|off)", timeout=2)
        self.assertEqual((line.text, line.source), ("[MyMod] Auto-sprint on", "client"))
        self.fake.add("Auto-sprint off", "actionbar")
        self.assertIsNone(chat.wait_for("Auto-sprint", timeout=0.05, source="chat"))

    def test_wait_for_source_tuple(self):
        self.fake.add("x", "game")
        self.fake.add("x", "client")
        self.assertEqual(chat.wait_for("x", timeout=0, source=("chat", "client"), since=0).seq, 2)

    def test_wait_for_timeout(self):
        self.fake.add("nothing interesting")
        start = time.monotonic()
        self.assertIsNone(chat.wait_for("reply", timeout=0.1, since=0))
        self.assertGreaterEqual(time.monotonic() - start, 0.1)
        self.assertLess(time.monotonic() - start, 1)

    def test_wait_for_since_guard(self):
        seq = chat.latest_seq()                     # grab before "sending the command"
        self.fake.add("There are 1 of a max of 20 players online")  # reply arrives before wait_for starts
        self.assertIsNone(chat.wait_for("players online", timeout=0.05), "without since the fast reply is missed")
        self.assertEqual(chat.wait_for("players online", timeout=0.05, since=seq).text,
                         "There are 1 of a max of 20 players online")
        self.fake.add("players online, old")
        self.assertIsNone(chat.wait_for("players online", timeout=0.05, since=chat.latest_seq()), "since excludes <= seq")

    def test_listen_and_stop(self):
        self.fake.add("before listen")
        got, seen = [], threading.Event()

        def callback(line):
            got.append(line.text)
            seen.set()
        listener = chat.listen(callback, pattern="^hit")
        self.fake.add("hit 1")
        self.fake.add("miss")
        self.assertTrue(seen.wait(2))
        listener.stop()
        self.assertFalse(listener.thread.is_alive())
        self.fake.add("hit 2")
        time.sleep(0.05)
        self.assertEqual(got, ["hit 1"])

    def test_listen_survives_callback_error_and_stop_from_callback(self):
        got, errors = [], []
        chat.traceback = types.SimpleNamespace(print_exc=lambda: errors.append(sys.exc_info()[1]))
        self.addCleanup(setattr, chat, "traceback", __import__("traceback"))

        def callback(line):
            got.append(line.text)
            if line.text == "boom":
                raise ValueError("boom")
            listener.stop()  # stopping from inside the callback must not deadlock
        listener = chat.listen(callback, source="chat")
        self.fake.add("boom", "chat")
        self.fake.add("ignored", "game")
        self.fake.add("second", "chat")
        listener.thread.join(2)
        self.assertFalse(listener.thread.is_alive())
        self.assertEqual(got, ["boom", "second"])
        self.assertEqual([str(e) for e in errors], ["boom"], "callback errors are printed, not fatal")

    def test_action_bar_and_hide(self):
        self.assertEqual(chat.action_bar(), "You may not rest now, there are monsters nearby")
        chat.hide("Auto-sprint")
        chat.hide(r"^\[Ad\]", ttl=2)
        chat.unhide("Auto-sprint")
        self.assertEqual(self.fake.calls, [("hide", "Auto-sprint", 0.0), ("hide", r"^\[Ad\]", 2.0),
                                           ("unhide", "Auto-sprint")])
        self.assertTrue(all(type(c[2]) is float for c in self.fake.calls if c[0] == "hide"))


if __name__ == "__main__":
    unittest.main()
