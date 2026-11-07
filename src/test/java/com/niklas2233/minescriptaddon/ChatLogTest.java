package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLogTest {
	private static final long S = 1_000_000_000L;
	private static final long T = Long.MAX_VALUE - S; // nanoTime may wrap

	private static List<String> texts(ChatLog log, long after) {
		return log.since(after, 0).stream().map(l -> l.source() + ":" + l.text()).toList();
	}

	private static void event(ChatLog log, String source, String text, long nanos) {
		log.fromEvent(source, text, "§a" + text, List.of(), "", 0, nanos);
	}

	private static boolean chatBox(ChatLog log, String text, long nanos) {
		return log.fromChatBox(text, "§a" + text, List.of(), "", 0, nanos);
	}

	@Test
	void eventLineAndItsChatBoxCopyAreRecordedOnce() {
		var log = new ChatLog();
		event(log, "game", "hello", T);
		chatBox(log, "hello", T);          // the chat-box copy: skipped
		chatBox(log, "hello", T);          // a second, client-added identical line: recorded
		chatBox(log, "[MyMod] Auto-sprint on", T);
		assertEquals(List.of("game:hello", "client:hello", "client:[MyMod] Auto-sprint on"), texts(log, 0));
	}

	@Test
	void dedupMatchesFormattingAndExpires() {
		var log = new ChatLog();
		event(log, "chat", "hi", T);
		log.fromChatBox("hi", "§chi", List.of(), "", 0, T);   // different formatting: not the copy
		event(log, "chat", "late", T);
		chatBox(log, "late", T + 2 * S);                      // outside the window: not the copy
		assertEquals(List.of("chat:hi", "client:hi", "chat:late", "client:late"), texts(log, 0));
	}

	private static ChatLog.Line actionBar(ChatLog log, String text) {
		return log.fromActionBar(text, "§a" + text, List.of(), "", 0);
	}

	@Test
	void actionBarRecordsOnlyChangesButStaysCurrent() {
		var log = new ChatLog();
		String rest = "You may not rest now, there are monsters nearby";
		assertEquals("actionbar", actionBar(log, rest).source());
		assertNull(actionBar(log, rest), "same text again (server re-send): no new line");
		assertNull(actionBar(log, rest));
		assertEquals(rest, log.actionBar());
		actionBar(log, "Now playing: C418 - cat");
		assertEquals("Now playing: C418 - cat", log.actionBar(), "always the latest");
		actionBar(log, rest);                       // changed back: recorded again
		assertNull(log.fromActionBar(rest, "§a" + rest, List.of(), "", 0));
		assertEquals(List.of("actionbar:" + rest, "actionbar:Now playing: C418 - cat", "actionbar:" + rest), texts(log, 0));
		log.fromActionBar(rest, "§c" + rest, List.of(), "", 0);
		assertEquals(4, log.since(0, 0).size(), "a formatting change counts as a change");
	}

	@Test
	void actionBarIsNotExpectedInChatBox() {
		var log = new ChatLog();
		String bar = "You may not rest now, there are monsters nearby";
		actionBar(log, bar);
		chatBox(log, bar, T);  // no pending marker from the action bar: the same text in chat is its own line
		assertEquals(List.of("actionbar:" + bar, "client:" + bar), texts(log, 0));
	}

	@Test
	void ringBufferKeepsLast500AndSeqIncreases() {
		var log = new ChatLog();
		for (int i = 1; i <= 510; i++) chatBox(log, "line " + i, T);
		var all = log.since(0, 0);
		assertEquals(ChatLog.CAPACITY, all.size());
		assertEquals(11, all.get(0).seq());
		assertEquals(510, all.get(499).seq());
		assertEquals(510, log.latestSeq());
		assertEquals(List.of(506L, 507L), log.since(505, 2).stream().map(ChatLog.Line::seq).toList(), "since + max");
		assertEquals(List.of(), log.since(510, 0));
	}

	@Test
	void clearKeepsSeqGoing() {
		var log = new ChatLog();
		chatBox(log, "a", T);
		actionBar(log, "bar");
		event(log, "game", "pending", T);
		log.clear();
		assertEquals(List.of(), log.since(0, 0));
		assertEquals("", log.actionBar());
		chatBox(log, "pending", T);      // pending dedup was cleared too
		assertEquals(List.of(new ChatLog.Line(4, 0, "client", "pending", "§apending", List.of(), "")), log.since(3, 0));
		assertEquals(5, actionBar(log, "bar").seq(), "action bar dedup was cleared too");
	}

	@Test
	void hideRulesHideButStillRecord() {
		var log = new ChatLog();
		log.hide("Auto-sprint (on|off)", 0, T);
		log.hide("^\\[Ad\\]", 2, T);
		assertTrue(chatBox(log, "[MyMod] Auto-sprint on", T));
		assertTrue(chatBox(log, "[Ad] buy stuff", T + S));
		assertFalse(chatBox(log, "[Ad] buy stuff", T + 2 * S), "ttl expired");
		assertFalse(chatBox(log, "normal", T));
		log.unhide("Auto-sprint (on|off)", T);
		assertFalse(chatBox(log, "[MyMod] Auto-sprint off", T));
		assertEquals(5, log.since(0, 0).size(), "hidden lines are still recorded");
		assertThrows(java.util.regex.PatternSyntaxException.class, () -> log.hide("(", 0, T));
	}
}
