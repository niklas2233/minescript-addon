package com.niklas2233.minescriptaddon;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Runs a read on the render thread and returns its result, for bridge calls that snapshot game state. */
final class RenderThread {
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");

	/**
	 * Minescript runs bridge calls on the render thread by default (then this just calls read), or on the
	 * script's own thread inside `with script_loop:` (then it waits up to 1 s for the next frame). Any failure
	 * returns fallback, so nothing throws back into the script.
	 */
	static <T> T read(Supplier<T> read, T fallback) {
		var mc = Minecraft.getInstance();
		try {
			return mc.isSameThread() ? read.get() : mc.submit(read).get(1, TimeUnit.SECONDS);
		} catch (Exception | LinkageError e) {
			LOG.warn("Reading game state for a script failed", e);
			return fallback;
		}
	}
}
