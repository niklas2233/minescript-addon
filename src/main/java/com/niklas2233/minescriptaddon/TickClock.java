package com.niklas2233.minescriptaddon;

/**
 * Client tick counter for ticks.py: counts ended ticks and remembers when each one ended, and lets script
 * threads block until a target tick. No Minecraft classes (tested by TickClockTest). Times are System.nanoTime().
 */
final class TickClock {
	// ponytail: end times of the last 65536 ticks (~55 min, 512 KB); older ticks are estimated at 50 ms each
	private static final int SIZE = 1 << 16;
	private final long[] ended = new long[SIZE];
	private long count;

	synchronized void tick(long nanos) {
		count++;
		ended[(int) (count & (SIZE - 1))] = nanos;
		notifyAll();
	}

	synchronized long count() {
		return count;
	}

	/** Blocks until count >= target or maxWaitNanos pass, then returns the count. */
	synchronized long await(long target, long maxWaitNanos) throws InterruptedException {
		long deadline = System.nanoTime() + maxWaitNanos;
		while (count < target) {
			long left = deadline - System.nanoTime();
			if (left <= 0) break;
			wait(left / 1_000_000, (int) (left % 1_000_000));
		}
		return count;
	}

	/** Seconds since tick c ended, or -1 if it hasn't ended yet. */
	synchronized double secondsSince(long c, long now) {
		if (c > count) return -1;
		long oldest = Math.max(1, count - SIZE + 1);
		if (count == 0) return 0;
		if (c < oldest) return secondsSince(oldest, now) + (oldest - c) * 0.05;
		return (now - ended[(int) (c & (SIZE - 1))]) / 1e9;
	}
}
