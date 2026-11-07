package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyClaimsTest {
	private static final long S = 1_000_000_000L;
	private static final long T = Long.MAX_VALUE - S; // nanoTime may wrap

	@Test
	void unclaimedPressesAreNotQueued() {
		var k = new KeyClaims(3);
		assertFalse(k.press(1, T), "unclaimed: the slot's command runs");
		k.claim(1, 2, T);
		assertEquals(0, k.take(1, 0, 2, T), "presses before the claim went to the command");
	}

	@Test
	void claimedPressesQueueAndAreConsumed() {
		var k = new KeyClaims(3);
		k.claim(2, 2, T);
		assertTrue(k.press(2, T));
		assertTrue(k.press(2, T));
		assertTrue(k.press(2, T));
		assertEquals(1, k.take(2, 1, 2, T), "wait() takes one");
		assertEquals(2, k.take(2, 0, 2, T), "pressed() takes the rest");
		assertEquals(0, k.take(2, 0, 2, T));
		assertFalse(k.press(1, T), "other slots unaffected");
	}

	@Test
	void claimExpiresUnlessRenewed() {
		var k = new KeyClaims(1);
		k.claim(1, 2, T);
		assertTrue(k.press(1, T + S));
		assertFalse(k.press(1, T + 2 * S), "expired: back to the command");
		k.claim(1, 2, T + 3 * S);
		assertEquals(0, k.take(1, 0, 2, T + 3 * S), "stale presses dropped on a fresh claim");
		k.take(1, 0, 2, T + 4 * S);            // take() renews
		assertTrue(k.press(1, T + 5 * S));     // would have expired at T+5s without renewal
		assertEquals(1, k.take(1, 0, 2, T + 5 * S));
	}

	@Test
	void ttlIsClamped() {
		var k = new KeyClaims(1);
		k.claim(1, 0, T);
		assertTrue(k.press(1, T + S / 100), "0 => 50 ms minimum");
		assertFalse(k.press(1, T + S / 10));
		k.claim(1, 1e300, T);
		assertTrue(k.press(1, T + 1000 * S), "huge ttl doesn't overflow");
	}

	@Test
	void releaseDropsClaimAndPresses() {
		var k = new KeyClaims(1);
		k.claim(1, 2, T);
		k.press(1, T);
		k.release(1);
		assertFalse(k.press(1, T));
		k.claim(1, 2, T);
		assertEquals(0, k.take(1, 0, 2, T));
	}

	@Test
	void slotRange() {
		var k = new KeyClaims(3);
		var e = assertThrows(IllegalArgumentException.class, () -> k.claim(4, 2, T));
		assertTrue(e.getMessage().contains("count is 3"), e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> k.press(0, T));
		assertThrows(IllegalArgumentException.class, () -> new KeyClaims(0).release(1));
		assertEquals(3, k.count());
	}
}
