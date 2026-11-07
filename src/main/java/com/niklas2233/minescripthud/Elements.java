package com.niklas2233.minescripthud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Id-keyed elements with optional expiry. No Minecraft classes, so ElementsCheck can test it directly.
 * Times are System.nanoTime() values passed in by the caller.
 */
final class Elements<T> {
	private record Entry<T>(T value, boolean expires, long deadline) {
		boolean live(long now) { return !expires || now - deadline < 0; } // subtraction: nanoTime may wrap
	}

	// ponytail: copy-on-write snapshot; scripts write from their own thread, render thread only reads.
	private volatile Map<String, Entry<T>> map = Map.of();

	/** ttlSeconds <= 0 (or NaN) never expires. */
	synchronized void put(String id, T value, double ttlSeconds, long now) {
		boolean expires = ttlSeconds > 0;
		// ponytail: capped at 1e9 s (~31 years) so the nanosecond deadline can't overflow
		long deadline = expires ? now + (long) (Math.min(ttlSeconds, 1e9) * 1e9) : 0;
		var copy = pruned(now);
		copy.put(id, new Entry<>(value, expires, deadline)); // replacing keeps the id's draw position
		map = copy;
	}

	synchronized void remove(String id, long now) {
		var copy = pruned(now);
		copy.remove(id);
		map = copy;
	}

	/** null or "" removes everything, otherwise only ids starting with prefix. */
	synchronized void clear(String prefix) {
		if (prefix == null || prefix.isEmpty()) {
			map = Map.of();
			return;
		}
		var copy = new LinkedHashMap<>(map);
		copy.keySet().removeIf(id -> id.startsWith(prefix));
		map = copy;
	}

	void forEachLive(long now, Consumer<T> action) {
		map.values().forEach(e -> { if (e.live(now)) action.accept(e.value()); });
	}

	int size() {
		return map.size();
	}

	private LinkedHashMap<String, Entry<T>> pruned(long now) {
		var copy = new LinkedHashMap<>(map);
		copy.values().removeIf(e -> !e.live(now));
		return copy;
	}
}
