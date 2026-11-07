package com.niklas2233.minescripthud;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client entrypoint plus the static API Minescript scripts call through Java reflection
 * (see hud.py, which this mod copies into the minescript folder on startup).
 * Elements are keyed by id; drawing an existing id replaces it. Later ids draw on top.
 */
public class MinescriptHud implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("minescripthud");

	// ponytail: copy-on-write snapshot; scripts write from their own thread, render thread only reads.
	private static volatile Map<String, Consumer<GuiGraphicsExtractor>> elements = Map.of();

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("minescripthud", "elements"),
				(g, delta) -> elements.values().forEach(e -> e.accept(g)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		installPythonModule();
	}

	public static void text(String id, int x, int y, String text, int color, boolean shadow) {
		put(id, g -> g.text(Minecraft.getInstance().font, text, ax(g, x), ay(g, y), color, shadow));
	}

	public static void rect(String id, int x, int y, int w, int h, int color) {
		put(id, g -> {
			int x0 = ax(g, x), y0 = ay(g, y);
			g.fill(x0, y0, x0 + w, y0 + h, color);
		});
	}

	public static void item(String id, int x, int y, String itemId, int count) {
		var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		var stack = new ItemStack(item, Math.max(1, count));
		put(id, g -> {
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

	public static synchronized void remove(String id) {
		var copy = new LinkedHashMap<>(elements);
		copy.remove(id);
		elements = copy;
	}

	public static synchronized void clear() {
		elements = Map.of();
	}

	private static synchronized void put(String id, Consumer<GuiGraphicsExtractor> draw) {
		var copy = new LinkedHashMap<>(elements);
		copy.put(id, draw);
		elements = copy;
	}

	// Negative coordinates anchor to the right/bottom edge, e.g. x = -10 is 10px from the right.
	private static int ax(GuiGraphicsExtractor g, int x) { return x < 0 ? g.guiWidth() + x : x; }
	private static int ay(GuiGraphicsExtractor g, int y) { return y < 0 ? g.guiHeight() + y : y; }

	private static void installPythonModule() {
		Path target = FabricLoader.getInstance().getGameDir().resolve("minescript").resolve("hud.py");
		try (InputStream in = MinescriptHud.class.getResourceAsStream("/hud.py")) {
			Files.createDirectories(target.getParent());
			Files.write(target, in.readAllBytes());
		} catch (Exception e) {
			LOG.warn("Could not install {}", target, e);
		}
	}
}
