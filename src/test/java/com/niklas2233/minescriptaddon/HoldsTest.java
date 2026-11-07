package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldsTest {
	private static final long S = 1_000_000_000L;
	private static final long T = Long.MAX_VALUE - S; // nanoTime may wrap

	@Test
	void renewKeepsItDown() {
		var h = new Holds();
		h.hold("attack", "farm", 1, T);
		for (int i = 1; i <= 5; i++) {
			assertEquals(List.of(), h.expire(T + i * S / 2), "renewed every 0.5 s");
			h.hold("attack", "farm", 1, T + i * S / 2);
		}
		assertEquals(List.of("attack"), h.keys());
	}

	@Test
	void expiryReleases() {
		var h = new Holds();
		h.hold("forward", "walk", 1, T);
		h.hold("jump", "walk", 3, T);
		assertEquals(List.of(), h.expire(T + S / 2));
		assertEquals(List.of("forward"), h.expire(T + S), "not renewed within ttl");
		assertEquals(List.of("jump"), h.keys());
		assertEquals(List.of(new Holds.Held("jump", "walk", 2)), h.held(T + S));
		assertEquals(List.of(), h.expire(T + S), "released once");
	}

	@Test
	void releaseAllIsOwnerScoped() {
		var h = new Holds();
		h.hold("attack", "farm.left", 5, T);
		h.hold("use", "farm.right", 5, T);
		h.hold("forward", "walker", 5, T);
		assertEquals(List.of("attack", "use"), h.releaseAll("farm."), "owner works like an id prefix");
		assertEquals(List.of("forward"), h.keys());
		assertEquals(List.of(), h.releaseAll("farm"));
		assertTrue(h.release("forward"));
		assertFalse(h.release("forward"));
		h.hold("sneak", "x", 5, T);
		assertEquals(List.of("sneak"), h.releaseAll(""), "\"\" releases everything");
	}

	@Test
	void holdRenewedAfterExpiryStartsFresh() {
		var h = new Holds();
		h.hold("use", "a", 1, T);
		assertEquals(List.of("use"), h.expire(T + 2 * S));
		h.hold("use", "b", 1, T + 3 * S);
		assertEquals(List.of(new Holds.Held("use", "b", 1)), h.held(T + 3 * S), "new owner, full ttl");
		assertEquals(List.of(), h.expire(T + 3 * S + S / 2));
		assertEquals(List.of("use"), h.expire(T + 4 * S));
	}
}
