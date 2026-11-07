package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The JSON files behind settings.py (config/minescriptaddon-settings/&lt;script&gt;.json), read and written for the
 * /msettings editor. A value's type comes from the file: true/false, a number with ".", "e" or "E" (Python writes
 * floats as 2.0), any other number, a string, or a list of one of those. Anything else is kept but not editable.
 */
final class Settings {
	enum Kind { BOOL, INT, FLOAT, STR, LIST, OTHER }

	/** One setting; elem is the element kind of a LIST (STR for an empty list). */
	record Entry(String key, Kind kind, Kind elem, JsonElement value) {
		/** What the editor's field starts with: the value, lists comma-separated. */
		String text() {
			return kind == Kind.LIST ? String.join(", ", value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList())
					: kind == Kind.OTHER ? value.toString() : value.getAsString();
		}

		/** Parses the editor's text back into a value of this setting's type; throws IllegalArgumentException with why not. */
		JsonElement read(String text) {
			if (kind != Kind.LIST) return Settings.read(kind, text);
			var out = new JsonArray();
			if (!text.isBlank()) for (String item : text.split(",", -1)) out.add(Settings.read(elem, elem == Kind.STR ? item.strip() : item));
			return out;
		}
	}

	static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");
	private static final Pattern INT = Pattern.compile("\\s*[-+]?\\d+\\s*");
	private static final Pattern FLOAT = Pattern.compile("\\s*[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?\\s*");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Settings() {}

	/** Throws on anything but a JSON object. */
	static List<Entry> parse(String json) {
		var out = new ArrayList<Entry>();
		for (var e : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
			Kind kind = kind(e.getValue());
			Kind elem = Kind.STR;
			if (kind == Kind.LIST) {
				var kinds = e.getValue().getAsJsonArray().asList().stream().map(Settings::kind).distinct().toList();
				if (kinds.size() > 1 || kinds.contains(Kind.LIST) || kinds.contains(Kind.OTHER)) kind = Kind.OTHER;
				else if (kinds.size() == 1) elem = kinds.getFirst();
			}
			out.add(new Entry(e.getKey(), kind, elem, e.getValue()));
		}
		return out;
	}

	static Kind kind(JsonElement e) {
		if (e.isJsonArray()) return Kind.LIST;
		if (!(e instanceof JsonPrimitive p)) return Kind.OTHER;
		if (p.isBoolean()) return Kind.BOOL;
		if (p.isString()) return Kind.STR;
		String s = p.getAsString();
		return s.contains(".") || s.contains("e") || s.contains("E") ? Kind.FLOAT : Kind.INT;
	}

	static JsonElement read(Kind kind, String text) {
		switch (kind) {
			case BOOL -> {
				if (text.strip().equalsIgnoreCase("true")) return new JsonPrimitive(true);
				if (text.strip().equalsIgnoreCase("false")) return new JsonPrimitive(false);
				throw new IllegalArgumentException("not true or false: " + text.strip());
			}
			case INT -> {
				if (!INT.matcher(text).matches()) throw new IllegalArgumentException("not a whole number: " + text.strip());
				try {
					return new JsonPrimitive(Long.parseLong(text.strip()));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("too big: " + text.strip());
				}
			}
			case FLOAT -> {
				double d = FLOAT.matcher(text).matches() ? Double.parseDouble(text.strip()) : Double.NaN;
				if (!Double.isFinite(d)) throw new IllegalArgumentException("not a number: " + text.strip());
				return new JsonPrimitive(d);
			}
			case STR -> {
				return new JsonPrimitive(text);
			}
			default -> throw new IllegalArgumentException("not editable");
		}
	}

	/** The file for script name, or IllegalArgumentException for names that would leave the directory. */
	static Path file(Path dir, String name) {
		if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("bad settings name: " + name);
		return dir.resolve(name + ".json");
	}

	/** Script names that have a settings file, sorted. */
	static List<String> names(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5)).filter(n -> NAME.matcher(n).matches()).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * Writes changes over the file's current contents (so keys a script added meanwhile survive), atomically:
	 * a temp file in the same directory, then a rename.
	 */
	static void save(Path file, Map<String, JsonElement> changes) throws IOException {
		JsonObject root;
		try {
			root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		} catch (Exception e) {
			root = new JsonObject();
		}
		changes.forEach(root::add);
		write(file, GSON.toJson(root) + "\n");
	}

	static void write(Path file, String json) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString() + ".", ".tmp");
		try {
			Files.writeString(tmp, json);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
