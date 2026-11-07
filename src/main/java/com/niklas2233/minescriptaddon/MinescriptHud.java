package com.niklas2233.minescriptaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Client entrypoint plus the static API Minescript scripts call through Java reflection
 * (see hud.py, which this mod copies into the minescript folder on startup).
 * Elements are keyed by id; drawing an existing id replaces it. Later ids draw on top.
 * The overloads with a trailing ttl (seconds, 0 = never) make the element vanish unless redrawn in time.
 */
public class MinescriptHud implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");

	private static final Elements<Consumer<GuiGraphicsExtractor>> elements = new Elements<>();

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("minescriptaddon", "elements"),
				(g, delta) -> elements.forEachLive(System.nanoTime(), e -> e.accept(g)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		installPythonModules();
	}

	public static void text(String id, int x, int y, String text, int color, boolean shadow) {
		text(id, x, y, text, color, shadow, 0);
	}

	public static void text(String id, int x, int y, String text, int color, boolean shadow, double ttl) {
		put(id, ttl, g -> g.text(Minecraft.getInstance().font, text, ax(g, x), ay(g, y), color, shadow));
	}

	public static void rect(String id, int x, int y, int w, int h, int color) {
		rect(id, x, y, w, h, color, 0);
	}

	public static void rect(String id, int x, int y, int w, int h, int color, double ttl) {
		put(id, ttl, g -> {
			int x0 = ax(g, x), y0 = ay(g, y);
			g.fill(x0, y0, x0 + w, y0 + h, color);
		});
	}

	public static void item(String id, int x, int y, String itemId, int count) {
		item(id, x, y, itemId, count, 0);
	}

	public static void item(String id, int x, int y, String itemId, int count, double ttl) {
		var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		var stack = new ItemStack(item, Math.max(1, count));
		put(id, ttl, g -> {
			int x0 = ax(g, x), y0 = ay(g, y);
			g.item(stack, x0, y0);
			if (count > 1) g.itemDecorations(Minecraft.getInstance().font, stack, x0, y0);
		});
	}

	public static int textWidth(String text) {
		return Minecraft.getInstance().font.width(text);
	}

	public static int screenWidth() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	public static int screenHeight() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	public static void remove(String id) {
		elements.remove(id, System.nanoTime());
	}

	public static void clear() {
		elements.clear(null);
	}

	/** Removes only ids starting with prefix, e.g. clear("farm.") for one script's elements. */
	public static void clear(String prefix) {
		elements.clear(prefix);
	}

	private static void put(String id, double ttl, Consumer<GuiGraphicsExtractor> draw) {
		elements.put(id, draw, ttl, System.nanoTime());
	}

	// Negative coordinates anchor to the right/bottom edge, e.g. x = -10 is 10px from the right.
	private static int ax(GuiGraphicsExtractor g, int x) { return x < 0 ? g.guiWidth() + x : x; }
	private static int ay(GuiGraphicsExtractor g, int y) { return y < 0 ? g.guiHeight() + y : y; }

	/** Copies the Python wrappers (hud.py, chat.py, keys.py, alert.py, inputs.py, ticks.py, screen_info.py, status.py, screens.py) into the minescript folder, overwriting old copies. */
	private static void installPythonModules() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("minescript");
		for (String name : new String[] {"hud.py", "chat.py", "keys.py", "alert.py", "inputs.py", "ticks.py", "screen_info.py", "status.py", "screens.py"}) {
			try (InputStream in = MinescriptHud.class.getResourceAsStream("/" + name)) {
				Files.createDirectories(dir);
				Files.write(dir.resolve(name), in.readAllBytes());
			} catch (Exception e) {
				LOG.warn("Could not install {}", dir.resolve(name), e);
			}
		}
	}
}
