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
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> event(overlay ? "actionbar" : "game", message));
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

	public static int latestSeq() {
		return (int) LOG.latestSeq();
	}

	/**
	 * Lines after seq as ONE JSON string (array of {seq,time,source,text,formatted,clicks:[{action,value}],screen}).
	 * A single String is the most reliable shape over Minescript's JavaClass bridge: it converts straight to a
	 * Python str in one call, where a List of String[] would need a bridge call per element.
	 */
	public static String since(int seq, int max) {
		return GSON.toJson(LOG.since(seq, max));
	}

	public static String actionBar() {
		return LOG.actionBar();
	}

	public static void hide(String regex, double ttl) {
		LOG.hide(regex, ttl, System.nanoTime());
	}

	public static void unhide(String regex) {
		LOG.unhide(regex, System.nanoTime());
	}

	// --- Capture

	private static void event(String source, Component message) {
		LOG.fromEvent(source, plain(message), formatted(message), clicks(message), screen(),
				System.currentTimeMillis(), System.nanoTime());
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
