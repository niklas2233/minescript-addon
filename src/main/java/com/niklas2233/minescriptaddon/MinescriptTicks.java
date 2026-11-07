package com.niklas2233.minescriptaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Client tick counter for ticks.py. Counts at END_CLIENT_TICK, also outside a world (about 20 per second;
 * slower when the game lags). Counts cross the bridge as int: 2^31 ticks is over 3 years.
 */
public class MinescriptTicks implements ClientModInitializer {
	static final TickClock CLOCK = new TickClock();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> CLOCK.tick(System.nanoTime()));
	}

	public static int count() {
		return (int) CLOCK.count();
	}

	/**
	 * Blocks until tick target has ended or maxWaitMs (at most 1000) pass, and returns the count. ticks.py calls
	 * it in Minescript's script loop (the job's own thread); on the render thread it never blocks, since the tick
	 * can't end while it waits.
	 */
	public static int waitFor(int target, int maxWaitMs) {
		if (Minecraft.getInstance().isSameThread()) return count();
		try {
			return (int) CLOCK.await(target, Math.clamp(maxWaitMs, 0, 1000) * 1_000_000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return count();
		}
	}

	/** Seconds since tick count ended, or -1 if it hasn't ended yet. */
	public static double timeSince(int count) {
		return CLOCK.secondsSince(count, System.nanoTime());
	}
}
