package com.niklas2233.minescriptaddon;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsTest {
	// What settings.py writes: json.dump(indent=2), floats always with a "."
	private static final String PY = """
			{
			  "radius": 8,
			  "speed": 1.5,
			  "whole": 2.0,
			  "name": "farm",
			  "sneak": true,
			  "crops": ["wheat", "carrots"],
			  "ys": [1.0, 2.5],
			  "empty": [],
			  "extra": {"a": 1},
			  "mixed": [1, "a"]
			}
			""";

	@Test
	void parsesKindsAndFieldText() {
		var entries = Settings.parse(PY);
		assertEquals(List.of("radius", "speed", "whole", "name", "sneak", "crops", "ys", "empty", "extra", "mixed"),
				entries.stream().map(Settings.Entry::key).toList()); // file order
		assertEquals(List.of(Settings.Kind.INT, Settings.Kind.FLOAT, Settings.Kind.FLOAT, Settings.Kind.STR, Settings.Kind.BOOL,
				Settings.Kind.LIST, Settings.Kind.LIST, Settings.Kind.LIST, Settings.Kind.OTHER, Settings.Kind.OTHER),
				entries.stream().map(Settings.Entry::kind).toList());
		assertEquals(Settings.Kind.FLOAT, entries.get(6).elem());
		assertEquals(Settings.Kind.STR, entries.get(7).elem());
		assertEquals(List.of("8", "1.5", "2.0", "farm", "true", "wheat, carrots", "1.0, 2.5", ""),
				entries.subList(0, 8).stream().map(Settings.Entry::text).toList());
		assertThrows(RuntimeException.class, () -> Settings.parse("[1]"));
		assertThrows(RuntimeException.class, () -> Settings.parse("{broken"));
	}

	@Test
	void readsFieldTextBackOrSaysWhy() {
		var e = Settings.parse(PY);
		assertEquals(new JsonPrimitive(12L), e.get(0).read(" 12 "));
		assertEquals(new JsonPrimitive(-3.0), e.get(1).read("-3"));
		assertEquals(new JsonPrimitive(1e-3), e.get(1).read("1e-3"));
		assertEquals(JsonParser.parseString("[\"beets\", \"x y\"]"), e.get(5).read("beets,  x y "));
		assertEquals(JsonParser.parseString("[3.0, 4.5]"), e.get(6).read("3, 4.5"));
		assertEquals(JsonParser.parseString("[]"), e.get(5).read("  "));
		assertEquals(new JsonPrimitive(" spaced "), e.get(3).read(" spaced "));
		for (String bad : new String[] {"1.5", "", "x", "99999999999999999999"}) assertThrows(IllegalArgumentException.class, () -> e.get(0).read(bad), bad);
		for (String bad : new String[] {"NaN", "Infinity", "1f", "0x10", "1e999", ""}) assertThrows(IllegalArgumentException.class, () -> e.get(1).read(bad), bad);
		assertThrows(IllegalArgumentException.class, () -> e.get(6).read("1, x"));
		assertThrows(IllegalArgumentException.class, () -> e.get(8).read("{}"));
	}

	@Test
	void saveMergesOverCurrentFileAtomically(@TempDir Path dir) throws Exception {
		Path file = Settings.file(dir, "farm");
		Files.writeString(file, PY);
		Files.writeString(file, PY.replace("\"radius\": 8,", "\"radius\": 8, \"added_by_script\": 1,")); // changed after the editor opened
		Settings.save(file, Map.of("radius", new JsonPrimitive(20L), "speed", new JsonPrimitive(2.0)));
		var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals("20", saved.get("radius").toString());
		assertEquals("2.0", saved.get("speed").toString()); // stays a float for Python
		assertEquals("1", saved.get("added_by_script").toString());
		assertEquals("{\"a\":1}", saved.get("extra").toString());
		try (var files = Files.list(dir)) {
			assertEquals(List.of("farm.json"), files.map(p -> p.getFileName().toString()).toList()); // no temp file left
		}
	}

	@Test
	void namesAndFiles(@TempDir Path dir) throws Exception {
		for (String n : new String[] {"b.json", "a.json", "a.json.123.tmp", "notes.txt", ".hidden.json"}) Files.writeString(dir.resolve(n), "{}");
		assertEquals(List.of("a", "b"), Settings.names(dir));
		assertEquals(List.of(), Settings.names(dir.resolve("missing")));
		assertEquals(dir.resolve("my_script-2.json"), Settings.file(dir, "my_script-2"));
		for (String bad : new String[] {"../x", "a/b", ".x", "", "a\\b"}) assertThrows(IllegalArgumentException.class, () -> Settings.file(dir, bad), bad);
	}
}
