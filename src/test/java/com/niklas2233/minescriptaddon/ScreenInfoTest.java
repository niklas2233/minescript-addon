package com.niklas2233.minescriptaddon;

import com.google.gson.JsonParser;
import com.niklas2233.minescriptaddon.ScreenInfo.Text;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenInfoTest {
	private static ScreenInfo.SidebarEntry entry(String owner, int value) {
		return new ScreenInfo.SidebarEntry(owner, value, new Text(owner, "§a" + owner), String.valueOf(value));
	}

	@Test
	void sidebarIsTopToBottomAsDisplayed() {
		var json = ScreenInfo.sidebarJson(new Text("Café", "§eCafé"),
				List.of(entry("b", 1), entry("Kills", 3), entry("a", 1), entry("C", 1)));
		assertTrue(json.chars().allMatch(c -> c < 0x80), json);
		var o = JsonParser.parseString(json).getAsJsonObject();
		assertEquals("Café", o.get("title").getAsString());
		assertEquals("§eCafé", o.get("formatted_title").getAsString());
		assertEquals("[\"Kills\",\"a\",\"b\",\"C\"]", o.get("lines").toString(), "score desc, then name ignoring case");
		assertEquals("§aKills", o.getAsJsonArray("formatted_lines").get(0).getAsString());
		assertEquals("[\"3\",\"1\",\"1\",\"1\"]", o.get("scores").toString());
	}

	@Test
	void sidebarShowsAtMost15() {
		var many = new ArrayList<ScreenInfo.SidebarEntry>();
		IntStream.range(0, 20).forEach(i -> many.add(entry("line" + i, i)));
		var lines = JsonParser.parseString(ScreenInfo.sidebarJson(Text.EMPTY, many)).getAsJsonObject().getAsJsonArray("lines");
		assertEquals(15, lines.size());
		assertEquals("line19", lines.get(0).getAsString());
	}

	@Test
	void tabListKeepsDisplayOrder() {
		var json = ScreenInfo.tabListJson(new Text("Welcome", "§6Welcome"), Text.EMPTY, List.of(
				new ScreenInfo.Player("Zed", new Text("Zed", "Zed"), 5, "creative"),
				new ScreenInfo.Player("Steve", new Text("[VIP] Steve", "§a[VIP] Steve"), 42, "survival")));
		var o = JsonParser.parseString(json).getAsJsonObject();
		assertEquals("§6Welcome", o.get("formatted_header").getAsString());
		assertEquals("", o.get("footer").getAsString());
		var steve = o.getAsJsonArray("players").get(1).getAsJsonObject();
		assertEquals("Steve", steve.get("name").getAsString());
		assertEquals("[VIP] Steve", steve.get("display").getAsString());
		assertEquals("§a[VIP] Steve", steve.get("formatted_display").getAsString());
		assertEquals(42, steve.get("ping").getAsInt());
		assertEquals("survival", steve.get("gamemode").getAsString());
		assertEquals("Zed", o.getAsJsonArray("players").get(0).getAsJsonObject().get("name").getAsString());
	}

	@Test
	void titleOnlyWhileShown() {
		assertEquals("null", ScreenInfo.titleJson(null, Text.EMPTY, 40));
		assertEquals("null", ScreenInfo.titleJson(new Text("Go", "Go"), Text.EMPTY, 0), "faded out");
		var o = JsonParser.parseString(ScreenInfo.titleJson(new Text("Round 2", "§cRound 2"), new Text("Go", "Go"), 70)).getAsJsonObject();
		assertEquals("Round 2", o.get("title").getAsString());
		assertEquals("Go", o.get("subtitle").getAsString());
		assertEquals(3.5, o.get("seconds_left").getAsDouble());
	}

	@Test
	void titlesAreRecordedInChatButNotWhileTheSameIsShown() {
		long seq = MinescriptChat.LOG.latestSeq();
		var round = Component.literal("Round 2");
		MinescriptChat.onTitle("subtitle", Component.literal("Go"), null, false);
		MinescriptChat.onTitle("title", round, null, false);
		MinescriptChat.onTitle("title", Component.literal("Round 2"), round, true);   // re-sent while shown
		MinescriptChat.onTitle("title", Component.literal("Round 3"), round, true);   // changed
		MinescriptChat.onTitle("title", Component.literal("Round 3"), Component.literal("Round 3"), false); // shown again after fading
		var lines = MinescriptChat.LOG.since(seq, 0).stream().map(l -> l.source() + ":" + l.text()).toList();
		assertEquals(List.of("subtitle:Go", "title:Round 2", "title:Round 3", "title:Round 3"), lines);
	}
}
