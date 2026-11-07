package com.niklas2233.minescriptaddon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * config/minescriptaddon-keys.json: {"count": 3, "show_press": true, "keys": {"1": {"label": "", "command": ""}, ...}}.
 * No Minecraft classes, so KeyConfigTest can test it. Slot entries above count are kept but not registered.
 */
record KeyConfig(int count, boolean showPress, Map<Integer, Slot> slots) {
	static final int DEFAULT_COUNT = 3;
	static final int MAX_COUNT = 50;
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");

	record Slot(String label, String command) {
		static final Slot EMPTY = new Slot("", "");
	}

	static KeyConfig defaults() {
		return new KeyConfig(DEFAULT_COUNT, true, Map.of()).withMissingSlots();
	}

	Slot slot(int n) {
		return slots.getOrDefault(n, Slot.EMPTY);
	}

	/** Throws on anything that isn't the expected shape; the caller falls back to defaults. */
	static KeyConfig parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		// getAsJsonPrimitive: Gson would otherwise silently unwrap [3] or [true]
		int count = root.has("count") ? root.getAsJsonPrimitive("count").getAsInt() : DEFAULT_COUNT;
		boolean showPress = !root.has("show_press") || bool(root.getAsJsonPrimitive("show_press"));
		var slots = new TreeMap<Integer, Slot>();
		if (root.has("keys")) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("keys").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				slots.put(Integer.parseInt(e.getKey().trim()), new Slot(str(o, "label"), str(o, "command")));
			}
		}
		return new KeyConfig(Math.clamp(count, 0, MAX_COUNT), showPress, slots);
	}

	/** Adds empty entries for slots 1..count that the file doesn't have yet. */
	KeyConfig withMissingSlots() {
		var all = new TreeMap<>(slots);
		for (int i = 1; i <= count; i++) all.putIfAbsent(i, Slot.EMPTY);
		return new KeyConfig(count, showPress, all);
	}

	String toJson() {
		var keys = new JsonObject();
		new TreeMap<>(slots).forEach((n, s) -> {
			var o = new JsonObject();
			o.addProperty("label", s.label());
			o.addProperty("command", s.command());
			keys.add(String.valueOf(n), o);
		});
		var root = new JsonObject();
		root.addProperty("count", count);
		root.addProperty("show_press", showPress);
		root.add("keys", keys);
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
	}

	/**
	 * Reads the file, creating it (or adding missing slot entries) as needed. A missing or broken file
	 * never throws: it logs a warning and returns defaults, and a broken file is left alone so it can be fixed.
	 */
	static KeyConfig load(Path file) {
		KeyConfig config;
		try {
			config = Files.exists(file) ? parse(Files.readString(file)) : defaults();
		} catch (Exception e) {
			LOG.warn("Broken {}, using defaults until it's fixed: {}", file, e.toString());
			return defaults();
		}
		KeyConfig full = config.withMissingSlots();
		if (!Files.exists(file) || !full.slots().equals(config.slots())) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, full.toJson());
			} catch (Exception e) {
				LOG.warn("Could not write {}", file, e);
			}
		}
		return full;
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.getAsJsonPrimitive(key).getAsString() : "";
	}

	private static boolean bool(JsonPrimitive p) {
		if (!p.isBoolean()) throw new IllegalStateException("show_press must be true or false, not " + p);
		return p.getAsBoolean();
	}
}
