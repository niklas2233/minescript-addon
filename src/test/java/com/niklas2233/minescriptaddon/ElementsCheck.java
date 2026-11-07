package com.niklas2233.minescriptaddon;

import java.util.ArrayList;
import java.util.List;

/** Plain-Java check for Elements (no test framework). Run by `./gradlew check` via the elementsCheck task. */
public class ElementsCheck {
	private static final long S = 1_000_000_000L;

	public static void main(String[] args) {
		// Start near Long.MAX_VALUE so deadlines wrap past it, like System.nanoTime() can.
		long t = Long.MAX_VALUE - S;
		var e = new Elements<String>();

		e.put("forever", "forever", 0, t);
		e.put("nan", "nan", Double.NaN, t);
		e.put("short", "short", 2, t);
		e.put("huge", "huge", 1e300, t);
		check(live(e, t + S), List.of("forever", "nan", "short", "huge"), "all live before ttl");
		check(live(e, t + 2 * S), List.of("forever", "nan", "huge"), "short skipped once expired");
		check(e.size() == 4, "expired element kept until next put/remove");

		e.put("short", "short2", 2, t + 3 * S);
		check(live(e, t + 3 * S), List.of("forever", "nan", "huge", "short2"), "re-put after expiry is live");
		e.remove("missing", t + 10 * S);
		check(e.size() == 3, "remove drops expired elements");
		check(live(e, t + 10 * S), List.of("forever", "nan", "huge"), "never/huge ttl survive");

		e.put("short", "replaced", 0, t);
		e.put("forever", "forever2", 0, t);
		check(live(e, t), List.of("forever2", "nan", "huge", "replaced"), "replacing keeps draw order");

		e.put("farm.a", "a", 0, t);
		e.put("farm.b", "b", 0, t);
		e.put("farmer", "farmer", 0, t);
		e.clear("farm.");
		check(live(e, t), List.of("forever2", "nan", "huge", "replaced", "farmer"), "clear(prefix) removes only matching ids");
		e.clear("");
		check(e.size() == 0, "clear(\"\") removes everything");
		e.put("x", "x", 0, t);
		e.clear(null);
		check(e.size() == 0, "clear(null) removes everything");

		System.out.println("ElementsCheck OK");
	}

	private static List<String> live(Elements<String> e, long now) {
		var out = new ArrayList<String>();
		e.forEachLive(now, out::add);
		return out;
	}

	private static void check(List<String> actual, List<String> expected, String what) {
		check(actual.equals(expected), what + ": expected " + expected + " but got " + actual);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
