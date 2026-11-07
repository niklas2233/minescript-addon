package com.niklas2233.minescriptaddon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The shared status panel (status.py): one "name: text" line per script, sorted by name, each gone unless
 * renewed within its ttl. No Minecraft classes (tested by StatusPanelTest). Times are System.nanoTime().
 */
final class StatusPanel {
	static final int PADDING = 2;
	static final int LINE_HEIGHT = 10; // the font's 9 px plus 1 px spacing
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");
	private static final Comparator<Line> ORDER = Comparator.comparing(Line::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Line::name);

	record Line(String name, String text, int color) {
		String shown() {
			return name + ": " + text;
		}
	}

	record Placed(int x, int y, Line line) {}

	/** The background box and where each line goes, in GUI-scaled pixels. */
	record Layout(int x, int y, int width, int height, List<Placed> lines) {}

	private final Elements<Line> lines = new Elements<>();

	void set(String name, String text, int color, double ttlSeconds, long now) {
		lines.put(name, new Line(name, text, color), Math.max(0.05, ttlSeconds), now); // always expires
	}

	void clear(String name, long now) {
		lines.remove(name, now);
	}

	List<Line> live(long now) {
		var out = new ArrayList<Line>();
		lines.forEachLive(now, out::add);
		out.sort(ORDER);
		return out;
	}

	/** null when there's nothing to draw. x/y offsets count inwards from the configured corner. */
	static Layout layout(Config c, List<Line> lines, ToIntFunction<String> textWidth, int screenWidth, int screenHeight) {
		if (lines.isEmpty()) return null;
		int width = lines.stream().mapToInt(l -> textWidth.applyAsInt(l.shown())).max().orElse(0) + 2 * PADDING;
		int height = lines.size() * LINE_HEIGHT + 2 * PADDING - 1;
		int x = c.corner().endsWith("left") ? c.x() : screenWidth - c.x() - width;
		int y = c.corner().startsWith("top") ? c.y() : screenHeight - c.y() - height;
		var placed = new ArrayList<Placed>();
		for (int i = 0; i < lines.size(); i++) placed.add(new Placed(x + PADDING, y + PADDING + i * LINE_HEIGHT, lines.get(i)));
		return new Layout(x, y, width, height, placed);
	}

	/** config/minescriptaddon-status.json: {"enabled": true, "corner": "top_left", "x": 4, "y": 4, "opacity": 0.5}. */
	record Config(boolean enabled, String corner, int x, int y, double opacity) {
		static final Config DEFAULT = new Config(true, "top_left", 4, 4, 0.5);
		static final List<String> CORNERS = List.of("top_left", "top_right", "bottom_left", "bottom_right");

		/** Background ARGB: black at the configured opacity (0.5 = hud.rect's default 0x80000000). */
		int background() {
			return (int) Math.round(Math.clamp(opacity, 0, 1) * 255) << 24;
		}

		/** Throws on anything that isn't the expected shape; the caller falls back to defaults. Missing keys: defaults. */
		static Config parse(String json) {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			boolean enabled = !root.has("enabled") || bool(root.getAsJsonPrimitive("enabled"));
			String corner = root.has("corner") ? root.getAsJsonPrimitive("corner").getAsString() : DEFAULT.corner();
			if (!CORNERS.contains(corner)) throw new IllegalStateException("corner must be one of " + CORNERS + ", not " + corner);
			int x = root.has("x") ? root.getAsJsonPrimitive("x").getAsInt() : DEFAULT.x();
			int y = root.has("y") ? root.getAsJsonPrimitive("y").getAsInt() : DEFAULT.y();
			double opacity = root.has("opacity") ? root.getAsJsonPrimitive("opacity").getAsDouble() : DEFAULT.opacity();
			return new Config(enabled, corner, x, y, Math.clamp(opacity, 0, 1));
		}

		String toJson() {
			var root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("corner", corner);
			root.addProperty("x", x);
			root.addProperty("y", y);
			root.addProperty("opacity", opacity);
			return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
		}

		/** Like KeyConfig.load: creates a missing file with defaults; a broken file logs a warning and is left alone. */
		static Config load(Path file) {
			try {
				if (Files.exists(file)) return parse(Files.readString(file));
				Files.createDirectories(file.getParent());
				Files.writeString(file, DEFAULT.toJson());
			} catch (Exception e) {
				LOG.warn("Broken or unwritable {}, using defaults until it's fixed: {}", file, e.toString());
			}
			return DEFAULT;
		}

		private static boolean bool(JsonPrimitive p) {
			if (!p.isBoolean()) throw new IllegalStateException("enabled must be true or false, not " + p);
			return p.getAsBoolean();
		}
	}
}
