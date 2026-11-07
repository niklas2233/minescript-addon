package com.niklas2233.minescriptaddon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Last {@link #CAPACITY} chat lines plus chat-box hide rules. No Minecraft classes, so ChatLogTest can test it.
 * Times: epochMs for the recorded line, nanos (System.nanoTime) for dedup windows and rule ttl.
 */
final class ChatLog {
	static final int CAPACITY = 500;
	/** An event line and its chat-box copy arrive in the same call stack; anything older is not a copy. */
	static final long DEDUP_WINDOW_NANOS = 1_000_000_000L;

	record Click(String action, String value) {}

	record Line(long seq, long time, String source, String text, String formatted, List<Click> clicks, String screen) {}

	private record Pending(String formatted, long nanos) {}

	private final ArrayDeque<Line> lines = new ArrayDeque<>();
	private final ArrayDeque<Pending> pending = new ArrayDeque<>();
	private final Elements<Pattern> hideRules = new Elements<>();
	private long seq; // never reset, so a script's saved seq stays valid across clear()
	private String actionBar = "";

	/** A line from Fabric's receive events: source "chat", "game" or "actionbar". */
	synchronized Line fromEvent(String source, String text, String formatted, List<Click> clicks, String screen,
			long epochMs, long nanos) {
		if (source.equals("actionbar")) {
			actionBar = text;
		} else {
			pending.addLast(new Pending(formatted, nanos)); // its chat-box copy comes next via fromChatBox
			if (pending.size() > 64) pending.removeFirst();
		}
		return add(source, text, formatted, clicks, screen, epochMs);
	}

	/**
	 * A line about to be added to the chat box. Recorded as "client" unless it is the chat-box copy of an
	 * event line already recorded. Returns true if a hide rule says it should not be shown.
	 */
	synchronized boolean fromChatBox(String text, String formatted, List<Click> clicks, String screen,
			long epochMs, long nanos) {
		if (!consumePending(formatted, nanos)) add("client", text, formatted, clicks, screen, epochMs);
		boolean[] hidden = {false};
		hideRules.forEachLive(nanos, p -> hidden[0] |= p.matcher(text).find());
		return hidden[0];
	}

	synchronized long latestSeq() {
		return seq;
	}

	/** Lines with seq greater than {@code after}, oldest first, at most max (max <= 0: no limit). */
	synchronized List<Line> since(long after, int max) {
		var out = new ArrayList<Line>();
		for (Line l : lines) {
			if (l.seq() > after) out.add(l);
		}
		return max > 0 && out.size() > max ? out.subList(0, max) : out;
	}

	synchronized String actionBar() {
		return actionBar;
	}

	synchronized void clear() {
		lines.clear();
		pending.clear();
		actionBar = "";
	}

	/** Hides matching chat-box lines (Java regex, find() on the plain text). ttlSeconds <= 0: until unhide. */
	void hide(String regex, double ttlSeconds, long nanos) {
		hideRules.put(regex, Pattern.compile(regex), ttlSeconds, nanos);
	}

	void unhide(String regex, long nanos) {
		hideRules.remove(regex, nanos);
	}

	private boolean consumePending(String formatted, long nanos) {
		for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (nanos - p.nanos() >= DEDUP_WINDOW_NANOS) {
				it.remove();
			} else if (p.formatted().equals(formatted)) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	private Line add(String source, String text, String formatted, List<Click> clicks, String screen, long epochMs) {
		var line = new Line(++seq, epochMs, source, text, formatted, List.copyOf(clicks), screen);
		lines.addLast(line);
		if (lines.size() > CAPACITY) lines.removeFirst();
		return line;
	}
}
