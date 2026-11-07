package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records every chat line (server chat, game messages, action bar, and lines other client mods add) for
 * Minescript scripts, which poll it through chat.py. Server lines come from Fabric's receive events; the
 * ChatComponent mixin catches the rest and skips the chat-box copy of lines the events already recorded.
 */
public class MinescriptChat implements ClientModInitializer {
	static final ChatLog LOG = new ChatLog();
	private static final Gson GSON = new Gson();
	private static final Map<Integer, Character> LEGACY_COLORS = new HashMap<>();
	// The open screen, tracked with Fabric events: Minecraft's own accessor differs between 26.x versions
	// (public field in 26.1, screen() method in 26.2).
	private static volatile Screen openScreen;

	static {
		for (char code : "0123456789abcdef".toCharArray()) {
			LEGACY_COLORS.put(TextColor.fromLegacyFormat(ChatFormatting.getByCode(code)).getValue(), code);
		}
	}

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, timestamp) -> event("chat", message));
		// Action bar text (overlay=true) is captured by the overlay mixins instead: /title ... actionbar and other
		// mods' messages reach the action bar without this event, but every route ends in setOverlayMessage.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) event("game", message);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LOG.clear());
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			openScreen = screen;
			ScreenEvents.remove(screen).register(removed -> {
				if (openScreen == removed) openScreen = null;
			});
		});
	}

	// --- Bridge API for chat.py. Minescript passes Python ints as Java int and returns a Java long as an
	// --- opaque handle, so seq crosses the bridge as int (internally long; 2^31 lines won't happen).
	// --- Text is returned as ASCII-only JSON (see ascii()) and decoded with json.loads in Python.

	public static int latestSeq() {
		return (int) LOG.latestSeq();
	}

	/**
	 * Lines after seq as ONE JSON string (array of {seq,time,source,text,formatted,clicks:[{action,value}],screen}).
	 * A single String is the most reliable shape over Minescript's JavaClass bridge: it converts straight to a
	 * Python str in one call, where a List of String[] would need a bridge call per element.
	 */
	public static String since(int seq, int max) {
		return ascii(GSON.toJson(LOG.since(seq, max)));
	}

	/** The latest action bar text as an ASCII-only JSON string literal (see ascii()). */
	public static String actionBar() {
		return ascii(GSON.toJson(LOG.actionBar()));
	}

	/**
	 * Escapes every non-ASCII char of a JSON string as backslash-u-XXXX (surrogate pairs stay as two escapes,
	 * which json.loads joins back). Needed because Minescript 5.0 writes strings to the script's stdin with
	 * the JVM default charset (UTF-8) while Python on Windows reads a piped stdin with the locale code page
	 * (e.g. cp1252) unless UTF-8 mode is on: "§" (C2 A7) arrived as "Â§". ASCII survives either way.
	 */
	static String ascii(String json) {
		var sb = new StringBuilder(json.length());
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c < 0x80) sb.append(c);
			else sb.append(String.format("\\u%04x", (int) c));
		}
		return sb.toString();
	}

	public static void hide(String regex, double ttl) {
		LOG.hide(regex, ttl, System.nanoTime());
	}

	public static void unhide(String regex) {
		LOG.unhide(regex, System.nanoTime());
	}

	/** True while any screen (inventory, chest, chat, menu) is open. */
	static boolean screenOpen() {
		return openScreen != null;
	}

	// --- Capture

	private static void event(String source, Component message) {
		LOG.fromEvent(source, plain(message), formatted(message), clicks(message), screen(),
				System.currentTimeMillis(), System.nanoTime());
	}

	/** Called by the overlay mixins with every action bar message (from the server, /title, or any mod). */
	public static void onOverlayMessage(Component message) {
		LOG.fromActionBar(plain(message), formatted(message), clicks(message), screen(), System.currentTimeMillis());
	}

	/**
	 * Called by the title mixins before a title/subtitle is set. current is the one being replaced and showing
	 * whether a title is on screen: re-sending the text that's still shown isn't recorded again.
	 */
	public static void onTitle(String source, Component message, Component current, boolean showing) {
		if (message == null) return;
		String formatted = formatted(message);
		boolean repeat = showing && current != null && formatted(current).equals(formatted);
		LOG.fromTitle(source, plain(message), formatted, clicks(message), screen(), System.currentTimeMillis(), repeat);
	}

	/** Called by ChatComponentMixin; returns true to keep the line out of the chat box. */
	public static boolean chatBox(Component message) {
		return LOG.fromChatBox(plain(message), formatted(message), clicks(message), screen(),
				System.currentTimeMillis(), System.nanoTime());
	}

	static String plain(Component c) {
		return ChatFormatting.stripFormatting(c.getString()); // also strips § codes servers put in literal text
	}

	/** The text with § codes. Hex colors that aren't one of the 16 legacy colors are dropped. */
	static String formatted(Component c) {
		var sb = new StringBuilder();
		String[] prev = {""};
		c.visit((style, text) -> {
			String codes = codes(style);
			if (!codes.equals(prev[0])) {
				if (!prev[0].isEmpty()) sb.append("§r");
				sb.append(codes);
				prev[0] = codes;
			}
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style s) {
		var sb = new StringBuilder();
		if (s.getColor() != null) {
			Character code = LEGACY_COLORS.get(s.getColor().getValue());
			if (code != null) sb.append('§').append(code);
		}
		if (s.isObfuscated()) sb.append("§k");
		if (s.isBold()) sb.append("§l");
		if (s.isStrikethrough()) sb.append("§m");
		if (s.isUnderlined()) sb.append("§n");
		if (s.isItalic()) sb.append("§o");
		return sb.toString();
	}

	/** run_command / suggest_command / open_url click actions in text order, one per clickable span. */
	static List<ChatLog.Click> clicks(Component c) {
		var out = new ArrayList<ChatLog.Click>();
		ClickEvent[] last = {null};
		c.visit((style, text) -> {
			ClickEvent e = style.getClickEvent();
			if (e != null && !e.equals(last[0])) {
				switch (e) {
					case ClickEvent.RunCommand r -> out.add(new ChatLog.Click("run_command", r.command()));
					case ClickEvent.SuggestCommand s -> out.add(new ChatLog.Click("suggest_command", s.command()));
					case ClickEvent.OpenUrl u -> out.add(new ChatLog.Click("open_url", u.uri().toString()));
					default -> { }
				}
			}
			last[0] = e;
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static String screen() {
		Screen screen = openScreen;
		return screen == null ? "" : screen.getTitle().getString();
	}
}
