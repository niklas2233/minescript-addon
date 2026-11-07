package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Shapes sidebar, tab list and title snapshots into the ASCII-only JSON screen_info.py reads. No Minecraft
 * classes (tested by ScreenInfoTest); MinescriptScreenInfo converts the game's objects into these records.
 */
final class ScreenInfo {
	private static final Gson GSON = new Gson();
	/** Vanilla's sidebar: highest score first, ties by holder name (case-insensitive), at most 15 lines. */
	static final Comparator<SidebarEntry> SIDEBAR_ORDER = Comparator.comparingInt(SidebarEntry::value).reversed()
			.thenComparing(SidebarEntry::owner, String.CASE_INSENSITIVE_ORDER);
	static final int SIDEBAR_LINES = 15;

	/** Plain and §-formatted variants of one piece of text, like chat.py's text/formatted. */
	record Text(String text, String formatted) {
		static final Text EMPTY = new Text("", "");
	}

	record SidebarEntry(String owner, int value, Text name, String score) {}

	record Player(String name, Text display, int ping, String gamemode) {}

	static String sidebarJson(Text title, List<SidebarEntry> entries) {
		var shown = entries.stream().sorted(SIDEBAR_ORDER).limit(SIDEBAR_LINES).toList();
		var out = new LinkedHashMap<String, Object>();
		out.put("title", title.text());
		out.put("formatted_title", title.formatted());
		out.put("lines", shown.stream().map(e -> e.name().text()).toList());
		out.put("formatted_lines", shown.stream().map(e -> e.name().formatted()).toList());
		out.put("scores", shown.stream().map(SidebarEntry::score).toList());
		return json(out);
	}

	/** players: already in display order. */
	static String tabListJson(Text header, Text footer, List<Player> players) {
		var out = new LinkedHashMap<String, Object>();
		out.put("header", header.text());
		out.put("formatted_header", header.formatted());
		out.put("footer", footer.text());
		out.put("formatted_footer", footer.formatted());
		out.put("players", players.stream().map(p -> {
			var player = new LinkedHashMap<String, Object>();
			player.put("name", p.name());
			player.put("display", p.display().text());
			player.put("formatted_display", p.display().formatted());
			player.put("ping", p.ping());
			player.put("gamemode", p.gamemode());
			return player;
		}).toList());
		return json(out);
	}

	/** title null or ticksLeft <= 0: nothing on screen, JSON null. */
	static String titleJson(Text title, Text subtitle, int ticksLeft) {
		if (title == null || ticksLeft <= 0) return "null";
		var out = new LinkedHashMap<String, Object>();
		out.put("title", title.text());
		out.put("formatted_title", title.formatted());
		out.put("subtitle", subtitle.text());
		out.put("formatted_subtitle", subtitle.formatted());
		out.put("seconds_left", ticksLeft / 20.0);
		return json(out);
	}

	private static String json(Object o) {
		return MinescriptChat.ascii(GSON.toJson(o));
	}
}
