"""Tests screen_info.py against a fake MinescriptScreenInfo bridge. Run: python -B -m unittest discover tests"""
import pathlib
import sys
import types
import unittest

sys.modules["java"] = types.SimpleNamespace(JavaClass=lambda name: None)
sys.path.insert(0, str(pathlib.Path(__file__).parents[1] / "src/main/resources"))
import screen_info  # noqa: E402


class FakeInfo:
    """Returns exactly what the Java side returns: ASCII-only JSON strings (see ScreenInfoTest)."""
    SIDEBAR = (r'{"title":"Café Games","formatted_title":"§eCafé Games","lines":["Kills: 3",""],'
               r'"formatted_lines":["§aKills: 3",""],"scores":["",""]}')
    TAB = (r'{"header":"Welcome","formatted_header":"§6Welcome","footer":"","formatted_footer":"",'
           r'"players":[{"name":"Steve","display":"[VIP] Steve","formatted_display":"§a[VIP] Steve",'
           r'"ping":42,"gamemode":"survival"}]}')
    title_json = "null"

    def sidebar(self):
        return self.SIDEBAR

    def tabList(self):
        return self.TAB

    def title(self):
        return self.title_json


class ScreenInfoTest(unittest.TestCase):
    def setUp(self):
        self.fake = screen_info._Info = FakeInfo()

    def test_sidebar(self):
        board = screen_info.sidebar()
        self.assertEqual(board["title"], "Café Games")
        self.assertEqual(board["formatted_title"], "§eCafé Games")
        self.assertEqual(board["lines"], ["Kills: 3", ""])
        self.assertEqual(board["formatted_lines"], ["§aKills: 3", ""])

    def test_tab_list(self):
        tab = screen_info.tab_list()
        self.assertEqual(tab["formatted_header"], "§6Welcome")
        self.assertEqual(tab["players"], [{"name": "Steve", "display": "[VIP] Steve",
                                           "formatted_display": "§a[VIP] Steve", "ping": 42, "gamemode": "survival"}])

    def test_title_none_and_present(self):
        self.assertIsNone(screen_info.title())
        self.fake.title_json = (r'{"title":"Round 2","formatted_title":"§cRound 2","subtitle":"Go →",'
                                r'"formatted_subtitle":"Go →","seconds_left":3.5}')
        self.assertEqual(screen_info.title(), {"title": "Round 2", "formatted_title": "§cRound 2",
                                               "subtitle": "Go →", "formatted_subtitle": "Go →", "seconds_left": 3.5})


if __name__ == "__main__":
    unittest.main()
