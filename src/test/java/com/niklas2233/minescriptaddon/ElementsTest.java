package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElementsTest {
	private static final long S = 1_000_000_000L;
	// Start near Long.MAX_VALUE so deadlines wrap past it, like System.nanoTime() can.
	private static final long T = Long.MAX_VALUE - S;

	private static List<String> live(Elements<String> e, long now) {
		var out = new ArrayList<String>();
		e.forEachLive(now, out::add);
		return out;
	}

	@Test
	void expiry() {
		var e = new Elements<String>();
		e.put("forever", "forever", 0, T);
		e.put("nan", "nan", Double.NaN, T);
		e.put("short", "short", 2, T);
		e.put("huge", "huge", 1e300, T);
		assertEquals(List.of("forever", "nan", "short", "huge"), live(e, T + S), "all live before ttl");
		assertEquals(List.of("forever", "nan", "huge"), live(e, T + 2 * S), "short skipped once expired");
		assertEquals(4, e.size(), "expired element kept until next put/remove");

		e.put("short", "short2", 2, T + 3 * S);
		assertEquals(List.of("forever", "nan", "huge", "short2"), live(e, T + 3 * S), "re-put after expiry is live");
		e.remove("missing", T + 10 * S);
		assertEquals(3, e.size(), "remove drops expired elements");
		assertEquals(List.of("forever", "nan", "huge"), live(e, T + 10 * S), "never/huge ttl survive");
	}

	@Test
	void replacingKeepsDrawOrder() {
		var e = new Elements<String>();
		e.put("a", "a", 0, T);
		e.put("b", "b", 0, T);
		e.put("a", "a2", 0, T);
		assertEquals(List.of("a2", "b"), live(e, T));
	}

	@Test
	void clearByPrefix() {
		var e = new Elements<String>();
		e.put("keep", "keep", 0, T);
		e.put("farm.a", "a", 0, T);
		e.put("farm.b", "b", 0, T);
		e.put("farmer", "farmer", 0, T);
		e.clear("farm.");
		assertEquals(List.of("keep", "farmer"), live(e, T), "only matching ids removed");
		e.clear("");
		assertEquals(0, e.size(), "clear(\"\") removes everything");
		e.put("x", "x", 0, T);
		e.clear(null);
		assertEquals(0, e.size(), "clear(null) removes everything");
	}
}
