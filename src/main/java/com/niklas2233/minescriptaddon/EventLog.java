package com.niklas2233.minescriptaddon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * The last {@link #CAPACITY} sound or particle events for world_events.py, with seq numbers that never reset
 * (clear() keeps counting). No Minecraft classes, so EventLogTest can test it.
 */
final class EventLog<E extends EventLog.Event> {
	static final int CAPACITY = 500;

	interface Event {
		long seq();
		double x();
		double y();
		double z();
		/** Positioned relative to the listener (UI sounds, music): never "near" anything. */
		boolean relative();
	}

	record Sound(long seq, long time, String id, String category, double x, double y, double z, float volume, float pitch,
			boolean relative) implements Event {}

	record Particle(long seq, long time, String id, double x, double y, double z, int count) implements Event {
		@Override
		public boolean relative() {
			return false;
		}
	}

	/** Matching events plus the newest seq scanned, so a poller can skip past events that didn't match. */
	record Page<E>(long latest, List<E> events) {}

	private final ArrayDeque<E> events = new ArrayDeque<>();
	private long seq;

	/** Adds the event make builds for the next seq. */
	synchronized E add(LongFunction<E> make) {
		E e = make.apply(++seq);
		events.addLast(e);
		if (events.size() > CAPACITY) events.removeFirst();
		return e;
	}

	synchronized long latestSeq() {
		return seq;
	}

	/** Events with seq greater than after, oldest first; with radius >= 0 only those within radius blocks of x/y/z. */
	synchronized Page<E> since(long after, double x, double y, double z, double radius) {
		var out = new ArrayList<E>();
		for (E e : events) {
			if (e.seq() <= after) continue;
			if (radius >= 0 && (e.relative() || sq(e.x() - x) + sq(e.y() - y) + sq(e.z() - z) > radius * radius)) continue;
			out.add(e);
		}
		return new Page<>(seq, out);
	}

	synchronized void clear() {
		events.clear();
	}

	private static double sq(double d) {
		return d * d;
	}
}
