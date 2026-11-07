package com.niklas2233.minescriptaddon;

import com.niklas2233.minescriptaddon.StatusPanel.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusPanelTest {
	private static final long S = 1_000_000_000L;
	private static final long T = Long.MAX_VALUE - S; // nanoTime may wrap

	private static List<String> shown(StatusPanel p, long now) {
		return p.live(now).stream().map(StatusPanel.Line::shown).toList();
	}

	@Test
	void sortedByNameAndReplacedByName() {
		var p = new StatusPanel();
		p.set("miner", "42 blocks", -1, 2, T);
		p.set("Farm", "idle", -1, 2, T);
		p.set("auto", "on", -1, 2, T);
		p.set("miner", "43 blocks", -1, 2, T);
		assertEquals(List.of("auto: on", "Farm: idle", "miner: 43 blocks"), shown(p, T), "case-insensitive, one line per name");
		p.clear("Farm", T);
		assertEquals(List.of("auto: on", "miner: 43 blocks"), shown(p, T));
	}

	@Test
	void linesExpireUnlessRenewed() {
		var p = new StatusPanel();
		p.set("a", "x", -1, 2, T);
		p.set("b", "y", -1, 1, T);
		assertEquals(List.of("a: x", "b: y"), shown(p, T + S / 2));
		assertEquals(List.of("a: x"), shown(p, T + S), "b not renewed within 1 s");
		p.set("a", "x", -1, 2, T + S);
		assertEquals(List.of("a: x"), shown(p, T + 2 * S + S / 2), "renewed");
		p.set("z", "no ttl", -1, 0, T);
		assertEquals(List.of(), shown(p, T + 4 * S), "ttl <= 0 still expires (50 ms)");
	}

	@Test
	void layoutPerCorner() {
		var lines = List.of(new StatusPanel.Line("a", "x", -1), new StatusPanel.Line("bb", "yy", -1));
		// width of "a: x" = 4 chars, "bb: yy" = 6 chars at 6 px each -> 36 + 2*2 padding = 40; height 2*10 + 4 - 1 = 23
		var tl = StatusPanel.layout(Config.DEFAULT, lines, s -> s.length() * 6, 320, 240);
		assertEquals(List.of(4, 4, 40, 23), List.of(tl.x(), tl.y(), tl.width(), tl.height()));
		assertEquals(List.of(6, 6, 6, 16), List.of(tl.lines().get(0).x(), tl.lines().get(0).y(), tl.lines().get(1).x(), tl.lines().get(1).y()));
		var br = StatusPanel.layout(new Config(true, "bottom_right", 10, 20, 0.5), lines, s -> s.length() * 6, 320, 240);
		assertEquals(List.of(320 - 10 - 40, 240 - 20 - 23), List.of(br.x(), br.y()));
		var tr = StatusPanel.layout(new Config(true, "top_right", 0, 0, 0.5), lines, s -> s.length() * 6, 320, 240);
		assertEquals(List.of(280, 0), List.of(tr.x(), tr.y()));
		var bl = StatusPanel.layout(new Config(true, "bottom_left", 2, 2, 0.5), lines, s -> s.length() * 6, 320, 240);
		assertEquals(List.of(2, 215), List.of(bl.x(), bl.y()));
		assertNull(StatusPanel.layout(Config.DEFAULT, List.of(), s -> 0, 320, 240), "nothing to draw");
	}

	@Test
	void configParseAndBackground() {
		assertEquals(Config.DEFAULT, Config.parse("{}"));
		assertEquals(new Config(false, "bottom_left", 1, 2, 1.0), Config.parse("{\"enabled\":false,\"corner\":\"bottom_left\",\"x\":1,\"y\":2,\"opacity\":3}"));
		assertEquals(0x80000000, Config.DEFAULT.background(), "like hud.rect's default");
		assertEquals(0, new Config(true, "top_left", 0, 0, 0).background());
		assertThrows(IllegalStateException.class, () -> Config.parse("{\"corner\":\"middle\"}"));
		assertThrows(IllegalStateException.class, () -> Config.parse("{\"enabled\":\"yes\"}"));
		assertThrows(Exception.class, () -> Config.parse("{\"x\":[1]}"));
	}

	@Test
	void loadCreatesDefaultsAndLeavesBrokenFilesAlone(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config/minescriptaddon-status.json");
		assertEquals(Config.DEFAULT, Config.load(file));
		assertEquals(Config.DEFAULT, Config.parse(Files.readString(file)), "created with defaults");
		Files.writeString(file, "{\"corner\": \"top_right\", \"x\": 8}");
		assertEquals(new Config(true, "top_right", 8, 4, 0.5), Config.load(file));
		Files.writeString(file, "{broken");
		assertEquals(Config.DEFAULT, Config.load(file));
		assertEquals("{broken", Files.readString(file), "left for the user to fix");
	}
}
