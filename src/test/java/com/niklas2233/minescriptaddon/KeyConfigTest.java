package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyConfigTest {
	@TempDir
	Path dir;

	@Test
	void missingFileIsCreatedWithDefaults() throws Exception {
		Path file = dir.resolve("config/minescriptaddon-keys.json");
		KeyConfig c = KeyConfig.load(file);
		assertEquals(3, c.count());
		assertTrue(c.showPress());
		assertEquals(List.of(1, 2, 3), List.copyOf(c.slots().keySet()));
		assertEquals(KeyConfig.parse(Files.readString(file)), c, "written file round-trips");
		assertTrue(Files.readString(file).contains("\"show_press\": true"));
	}

	@Test
	void parsesFieldsAndClampsCount() {
		var c = KeyConfig.parse("""
				{"count": 2, "show_press": false,
				 "keys": {"1": {"label": "Farm", "command": "\\\\farm"}, "2": {"command": "/spawn"}}}""");
		assertEquals(2, c.count());
		assertFalse(c.showPress());
		assertEquals(new KeyConfig.Slot("Farm", "\\farm"), c.slot(1));
		assertEquals(new KeyConfig.Slot("", "/spawn"), c.slot(2));
		assertEquals(KeyConfig.Slot.EMPTY, c.slot(9), "unknown slot: empty");
		assertEquals(0, KeyConfig.parse("{\"count\": -4}").count());
		assertEquals(KeyConfig.MAX_COUNT, KeyConfig.parse("{\"count\": 1000}").count());
		assertEquals(3, KeyConfig.parse("{}").count(), "defaults for missing fields");
		assertTrue(KeyConfig.parse("{}").showPress());
	}

	@Test
	void raisingCountAddsSlotsLoweringKeepsThem() throws Exception {
		Path file = dir.resolve("keys.json");
		Files.writeString(file, "{\"count\": 5, \"keys\": {\"2\": {\"label\": \"B\", \"command\": \"/b\"}}}");
		KeyConfig c = KeyConfig.load(file);
		assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(c.slots().keySet()));
		assertEquals("/b", c.slot(2).command(), "existing entry kept");
		assertEquals(c, KeyConfig.parse(Files.readString(file)), "missing slots written back");

		Files.writeString(file, Files.readString(file).replace("\"count\": 5", "\"count\": 1"));
		String before = Files.readString(file);
		KeyConfig lower = KeyConfig.load(file);
		assertEquals(1, lower.count());
		assertEquals(5, lower.slots().size(), "extra entries kept");
		assertEquals(before, Files.readString(file), "nothing to add: file untouched");
	}

	@Test
	void brokenFileUsesDefaultsAndIsLeftAlone() throws Exception {
		Path file = dir.resolve("keys.json");
		for (String broken : List.of("{not json", "[1, 2]", "{\"count\": \"many\"}", "{\"keys\": {\"x\": {}}}",
				"{\"keys\": {\"1\": \"/cmd\"}}", "{\"show_press\": [true]}", "{\"count\": [3]}",
				"{\"show_press\": \"yes\"}", "{\"keys\": {\"1\": {\"command\": [\"/a\"]}}}")) {
			Files.writeString(file, broken);
			assertEquals(KeyConfig.defaults(), KeyConfig.load(file), broken);
			assertEquals(broken, Files.readString(file), "broken file not overwritten: " + broken);
		}
		assertThrows(Exception.class, () -> KeyConfig.parse("{not json"));
	}

	@Test
	void unwritableLocationStillReturnsConfig() throws Exception {
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "");
		KeyConfig c = KeyConfig.load(blocker.resolve("keys.json")); // parent is a file: can't create
		assertEquals(KeyConfig.defaults(), c);
	}
}
