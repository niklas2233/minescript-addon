package com.niklas2233.minescriptaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Draws the shared status panel (status.py) as a HUD element. Hidden with the rest of the HUD (F1) and while
 * the debug screen is open. config/minescriptaddon-status.json is re-read within a second of changing.
 */
public class MinescriptStatus implements ClientModInitializer {
	static final StatusPanel PANEL = new StatusPanel();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("minescriptaddon-status.json");
	private static volatile StatusPanel.Config config = StatusPanel.Config.DEFAULT;
	private static FileTime loadedAt;
	private static long checkedAt;

	@Override
	public void onInitializeClient() {
		config = StatusPanel.Config.load(FILE);
		loadedAt = modified();
		checkedAt = System.nanoTime();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("minescriptaddon", "status"), (g, delta) -> draw(g));
	}

	// --- Bridge API for status.py

	public static void set(String name, String text, int color, double ttl) {
		PANEL.set(name, text, color, ttl, System.nanoTime());
	}

	public static void clear(String name) {
		PANEL.clear(name, System.nanoTime());
	}

	// --- Render thread

	private static void draw(GuiGraphicsExtractor g) {
		var mc = Minecraft.getInstance();
		long now = System.nanoTime();
		if (Compat.hudHidden(mc) || mc.getDebugOverlay().showDebugScreen()) return;
		if (now - checkedAt > 1_000_000_000L) reloadIfChanged(now);
		var c = config;
		if (!c.enabled()) return;
		var layout = StatusPanel.layout(c, PANEL.live(now), mc.font::width, g.guiWidth(), g.guiHeight());
		if (layout == null) return;
		g.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), c.background());
		for (var p : layout.lines()) g.text(mc.font, p.line().shown(), p.x(), p.y(), p.line().color(), true);
	}

	private static void reloadIfChanged(long now) {
		checkedAt = now;
		FileTime t = modified();
		if (t == null || t.equals(loadedAt)) return;
		config = StatusPanel.Config.load(FILE);
		loadedAt = modified();
	}

	private static FileTime modified() {
		try {
			return Files.getLastModifiedTime(FILE);
		} catch (Exception e) {
			return null;
		}
	}
}
