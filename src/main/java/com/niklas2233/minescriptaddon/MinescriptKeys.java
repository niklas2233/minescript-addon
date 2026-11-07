package com.niklas2233.minescriptaddon;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * "Minescript key 1..N" keybinds (Options -> Controls -> Minescript). A press goes to the script that claimed
 * the slot through keys.py, otherwise it runs the slot's command from config/minescriptaddon-keys.json.
 * count and labels are read at startup only (keybinds can only be registered then); commands and show_press
 * are re-read on the next press after the file changes.
 */
public class MinescriptKeys implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");
	private static final String NAME_PREFIX = "key.minescriptaddon.slot.";
	private static final String CATEGORY_KEY = "key.category.minescriptaddon.keys";
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("minescriptaddon-keys.json");

	private static KeyConfig startup = KeyConfig.defaults(); // count + labels, fixed after startup
	private static volatile KeyConfig config = startup;      // commands + show_press, reloaded on change
	private static FileTime loadedAt;
	private static KeyMapping[] mappings = new KeyMapping[0];
	private static KeyClaims claims = new KeyClaims(0);

	@Override
	public void onInitializeClient() {
		startup = config = KeyConfig.load(FILE);
		loadedAt = modified();
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minescriptaddon", "keys"));
		mappings = new KeyMapping[startup.count()];
		for (int i = 0; i < mappings.length; i++) {
			// Unbound by default. The (name, keyCode, category) constructor means keyboard in every 26.x;
			// InputConstants.Type.KEYSYM was renamed in 26.3.
			mappings[i] = KeyMappingHelper.registerKeyMapping(
					new KeyMapping(NAME_PREFIX + (i + 1), InputConstants.UNKNOWN.getValue(), category));
		}
		claims = new KeyClaims(mappings.length);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (int i = 0; i < mappings.length; i++) {
				while (mappings[i].consumeClick()) {
					// Like vanilla keys: nothing while a screen (inventory, chest, chat) is open or outside a world.
					if (!MinescriptChat.screenOpen() && client.player != null) pressed(i + 1);
				}
			}
		});
	}

	// --- Bridge API for keys.py (slots are 1-based; out of range throws IllegalArgumentException).

	public static int count() {
		return claims.count();
	}

	public static void claim(int slot, double ttl) {
		claims.claim(slot, ttl, System.nanoTime());
	}

	/** Renews the claim and consumes up to max queued presses (max <= 0: all). */
	public static int take(int slot, int max, double ttl) {
		return claims.take(slot, max, ttl, System.nanoTime());
	}

	public static void release(int slot) {
		claims.release(slot);
	}

	/** The bound key's display name, e.g. "R", or "" if unbound. */
	public static String binding(int slot) {
		KeyMapping m = mappings[claims.index(slot)];
		return m.isUnbound() ? "" : m.getTranslatedKeyMessage().getString();
	}

	/** Display names for the Controls menu, supplied through ClientLanguageMixin. Null: not one of ours. */
	public static String translate(String key) {
		if (key.equals(CATEGORY_KEY)) return "Minescript";
		if (!key.startsWith(NAME_PREFIX)) return null;
		try {
			int n = Integer.parseInt(key.substring(NAME_PREFIX.length()));
			String label = startup.slot(n).label();
			return "Minescript key " + n + (label.isBlank() ? "" : " (" + label + ")");
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// --- Presses

	private static void pressed(int slot) {
		if (claims.press(slot, System.nanoTime())) return; // a script has it
		reloadIfChanged();
		String command = config.slot(slot).command().strip();
		if (command.isEmpty()) return;
		var mc = Minecraft.getInstance();
		if (command.startsWith("/")) {
			mc.getConnection().sendCommand(command.substring(1));
		} else if (command.startsWith("\\")) {
			if (!runMinescript(command)) command = "Minescript not found, can't run " + command;
		} else {
			command = "command must start with / or \\: " + command;
		}
		if (config.showPress()) mc.player.sendOverlayMessage(Component.literal("Minescript key " + slot + ": " + command));
	}

	private static Method minescriptOnChat;

	/**
	 * Runs "\command" exactly as if typed in chat. Minescript 5.0 has no documented API for this, so this calls
	 * net.minescript.common.Minescript.onClientChat(String): the public static method its chat-screen hook
	 * calls (it also adds the line to chat history, like typing). Reflection keeps Minescript optional; if a
	 * future Minescript drops the method, the press reports it instead of sending "\..." to the server as chat.
	 */
	private static boolean runMinescript(String command) {
		try {
			if (minescriptOnChat == null) {
				minescriptOnChat = Class.forName("net.minescript.common.Minescript").getMethod("onClientChat", String.class);
			}
			minescriptOnChat.invoke(null, command);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			LOG.warn("Can't run {} through Minescript", command, e);
			return false;
		}
	}

	private static void reloadIfChanged() {
		FileTime now = modified();
		if (now == null || now.equals(loadedAt)) return;
		config = KeyConfig.load(FILE);
		loadedAt = modified(); // load may have added missing slot entries
	}

	private static FileTime modified() {
		try {
			return Files.getLastModifiedTime(FILE);
		} catch (Exception e) {
			return null;
		}
	}
}
