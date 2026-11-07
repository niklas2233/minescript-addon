package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickClockTest {
	private static final long MS = 1_000_000L;

	private static CompletableFuture<Long> awaitAsync(TickClock clock, long target, long maxWaitMs) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return clock.await(target, maxWaitMs * MS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void waiterWakesPromptlyAfterTheTick() throws Exception {
		var clock = new TickClock();
		var waiter = awaitAsync(clock, 1, 5000);
		Thread.sleep(50);
		assertFalse(waiter.isDone(), "blocks until the tick");
		long ticked = System.nanoTime();
		clock.tick(ticked);
		assertEquals(1, waiter.get(1, TimeUnit.SECONDS));
		assertTrue(System.nanoTime() - ticked < 500 * MS, "woken by the tick, not by the 5 s timeout");
	}

	@Test
	void timeoutReturnsTheCurrentCount() throws Exception {
		var clock = new TickClock();
		clock.tick(0);
		long start = System.nanoTime();
		assertEquals(1, clock.await(2, 100 * MS));
		long took = System.nanoTime() - start;
		assertTrue(took >= 100 * MS && took < 2000 * MS, "waited about maxWait: " + took / MS + " ms");
		assertEquals(1, clock.await(1, 0), "already reached: no wait");
	}

	@Test
	void waitsForNTicks() throws Exception {
		var clock = new TickClock();
		var waiter = awaitAsync(clock, clock.count() + 3, 5000);
		clock.tick(0);
		clock.tick(0);
		Thread.sleep(50);
		assertFalse(waiter.isDone(), "2 of 3 ticks: still waiting");
		clock.tick(0);
		assertEquals(3, waiter.get(1, TimeUnit.SECONDS));
	}

	@Test
	void secondsSinceUsesTheTickEndTime() {
		var clock = new TickClock();
		clock.tick(1_000 * MS);
		clock.tick(1_050 * MS);
		assertEquals(0.5, clock.secondsSince(1, 1_500 * MS), 1e-9);
		assertEquals(0.45, clock.secondsSince(2, 1_500 * MS), 1e-9);
		assertEquals(-1, clock.secondsSince(3, 1_500 * MS), "not ended yet");
		assertEquals(0.55, clock.secondsSince(0, 1_500 * MS), 1e-9, "before the first kept tick: 50 ms per tick");
	}
}
