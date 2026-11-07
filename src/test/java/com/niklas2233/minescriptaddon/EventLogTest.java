package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventLogTest {
	private static EventLog.Sound sound(long seq, double x, boolean relative) {
		return new EventLog.Sound(seq, 1000 + seq, "minecraft:block.note_block.bell", "record", x, 64, 0, 1, 1, relative);
	}

	private static List<Long> seqs(EventLog.Page<? extends EventLog.Event> page) {
		return page.events().stream().map(EventLog.Event::seq).toList();
	}

	@Test
	void keepsTheLast500WithSeqsThatNeverReset() {
		var log = new EventLog<EventLog.Sound>();
		for (int i = 0; i < 600; i++) log.add(s -> sound(s, 0, false));
		var all = log.since(0, 0, 0, 0, -1);
		assertEquals(600, all.latest());
		assertEquals(EventLog.CAPACITY, all.events().size());
		assertEquals(101, all.events().getFirst().seq()); // oldest first
		assertEquals(List.of(599L, 600L), seqs(log.since(598, 0, 0, 0, -1)));
		log.clear();
		assertEquals(List.of(), log.since(0, 0, 0, 0, -1).events());
		assertEquals(601, log.add(s -> sound(s, 0, false)).seq());
	}

	@Test
	void nearFiltersByDistanceButStillReportsLatest() {
		var log = new EventLog<EventLog.Sound>();
		log.add(s -> sound(s, 3, false));   // 3 blocks away
		log.add(s -> sound(s, 10, false));  // 10 blocks away
		log.add(s -> sound(s, 0, true));    // relative (UI): never near
		log.add(s -> sound(s, 5, false));   // exactly on the radius
		var page = log.since(0, 0, 64, 0, 5);
		assertEquals(List.of(1L, 4L), seqs(page));
		assertEquals(4, page.latest());
		assertEquals(List.of(), log.since(4, 0, 64, 0, 100).events());
		assertEquals(List.of(1L, 2L, 3L, 4L), seqs(log.since(0, 0, 64, 0, -1))); // radius < 0: no filter
	}

	@Test
	void particlesUseTheSameLog() {
		var log = new EventLog<EventLog.Particle>();
		log.add(s -> new EventLog.Particle(s, 5, "minecraft:flame", 1, 2, 3, 8));
		var p = log.since(0, 1, 2, 3, 0).events().getFirst();
		assertEquals("minecraft:flame", p.id());
		assertEquals(8, p.count());
	}
}
