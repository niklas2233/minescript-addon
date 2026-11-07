package com.niklas2233.minescriptaddon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inputs held down by scripts (inputs.py). A hold lasts ttl seconds unless renewed, so a crashed or killed
 * script lets go. No Minecraft classes (tested by HoldsTest). Times are System.nanoTime() values.
 */
final class Holds {
	record Held(String key, String owner, double secondsLeft) {}

	private record Hold(String owner, long deadline) {}

	private final Map<String, Hold> holds = new LinkedHashMap<>();

	/** Holds or renews key for ttl seconds from now; the latest caller becomes the owner. */
	synchronized void hold(String key, String owner, double ttlSeconds, long now) {
		// ponytail: ttl clamped to 50 ms .. ~11 days so the nanosecond deadline can't overflow (as in KeyClaims)
		holds.put(key, new Hold(owner, now + (long) (Math.clamp(ttlSeconds, 0.05, 1e6) * 1e9)));
	}

	/** Returns true if key was held. */
	synchronized boolean release(String key) {
		return holds.remove(key) != null;
	}

	/** Releases the holds whose owner starts with prefix ("" = all) and returns their keys. */
	synchronized List<String> releaseAll(String prefix) {
		return removeIf((key, h) -> h.owner().startsWith(prefix));
	}

	/** Drops the holds that weren't renewed in time and returns their keys. */
	synchronized List<String> expire(long now) {
		return removeIf((key, h) -> now - h.deadline() >= 0); // subtraction: nanoTime may wrap
	}

	synchronized List<String> keys() {
		return List.copyOf(holds.keySet());
	}

	synchronized List<Held> held(long now) {
		var out = new ArrayList<Held>();
		holds.forEach((key, h) -> out.add(new Held(key, h.owner(), Math.max(0, (h.deadline() - now) / 1e9))));
		return out;
	}

	private List<String> removeIf(java.util.function.BiPredicate<String, Hold> test) {
		var removed = new ArrayList<String>();
		holds.entrySet().removeIf(e -> test.test(e.getKey(), e.getValue()) && removed.add(e.getKey()));
		return removed;
	}
}
