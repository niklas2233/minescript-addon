package com.niklas2233.minescriptaddon;

/**
 * Which key slots a script has claimed, and the presses queued for it. A claim lasts ttl seconds unless
 * renewed, so a crashed or killed script stops swallowing the key. No Minecraft classes (tested by
 * KeyClaimsTest). Times are System.nanoTime() values; slots are 1-based.
 */
final class KeyClaims {
	private final boolean[] claimed;
	private final long[] deadline;
	private final int[] presses;

	KeyClaims(int count) {
		claimed = new boolean[count];
		deadline = new long[count];
		presses = new int[count];
	}

	int count() {
		return claimed.length;
	}

	/** Returns true if the slot is claimed: the press is queued for the script and nothing else happens. */
	synchronized boolean press(int slot, long now) {
		int i = index(slot);
		if (!live(i, now)) return false;
		presses[i]++;
		return true;
	}

	/** Claims or renews. A fresh claim (or one renewed after expiry) starts with no queued presses. */
	synchronized void claim(int slot, double ttlSeconds, long now) {
		int i = index(slot);
		if (!live(i, now)) presses[i] = 0;
		claimed[i] = true;
		// ponytail: ttl clamped to 50 ms .. ~11 days so the nanosecond deadline can't overflow
		deadline[i] = now + (long) (Math.clamp(ttlSeconds, 0.05, 1e6) * 1e9);
	}

	/** Renews the claim, then consumes up to max queued presses (max <= 0: all) and returns how many. */
	synchronized int take(int slot, int max, double ttlSeconds, long now) {
		claim(slot, ttlSeconds, now);
		int i = index(slot);
		int n = max > 0 ? Math.min(max, presses[i]) : presses[i];
		presses[i] -= n;
		return n;
	}

	synchronized void release(int slot) {
		int i = index(slot);
		claimed[i] = false;
		presses[i] = 0;
	}

	private boolean live(int i, long now) {
		return claimed[i] && now - deadline[i] < 0; // subtraction: nanoTime may wrap
	}

	/** 0-based index of a 1-based slot; throws IllegalArgumentException (with the count) when out of range. */
	int index(int slot) {
		if (slot < 1 || slot > claimed.length) {
			throw new IllegalArgumentException("slot " + slot + " outside 1.." + claimed.length + " (count is " + claimed.length + ")");
		}
		return slot - 1;
	}
}
