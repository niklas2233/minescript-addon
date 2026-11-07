package com.niklas2233.minescriptaddon;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Component -> text conversion and the JSON shape chat.py parses. Uses real Minecraft text classes. */
class MinescriptChatTest {
	private static final Component LINE = Component.literal("[Server] ").withStyle(ChatFormatting.YELLOW)
			.append(Component.literal("Click ").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/help"))))
			.append(Component.literal("here").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/help"))))
			.append(Component.literal(" or ").withStyle(s -> s.withColor(TextColor.fromRgb(0x123456))))
			.append(Component.literal("site").withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://x.y")))))
			.append(Component.literal(" §cold§r codes"));

	@Test
	void plainStripsFormattingIncludingLiteralCodes() {
		assertEquals("[Server] Click here or site old codes", MinescriptChat.plain(LINE));
	}

	@Test
	void formattedUsesSectionCodes() {
		// Hex color 0x123456 has no § code; "site" and the last span inherit the root's yellow.
		assertEquals("§e[Server] §r§a§lClick here§r or §esite §cold§r codes", MinescriptChat.formatted(LINE));
	}

	@Test
	void clicksInOrderOncePerSpan() {
		assertEquals(List.of(new ChatLog.Click("run_command", "/help"), new ChatLog.Click("open_url", "https://x.y")),
				MinescriptChat.clicks(LINE));
		var suggest = Component.literal("a").withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/msg ")));
		assertEquals(List.of(new ChatLog.Click("suggest_command", "/msg ")), MinescriptChat.clicks(suggest));
	}

	// "§e", "é", "→" and an emoji (a surrogate pair), as escapes so this file's encoding can't matter.
	private static final String NON_ASCII = "§eYellow café → 😀";

	@Test
	void sinceJsonIsAsciiOnlyAndRoundTripsNonAscii() {
		long seq = MinescriptChat.LOG.latestSeq();
		MinescriptChat.LOG.fromChatBox("Yellow café → 😀", NON_ASCII, List.of(), "Café →", 0, 0);
		String json = MinescriptChat.since((int) seq, 10);
		assertTrue(json.chars().allMatch(c -> c < 0x80), "only ASCII crosses Minescript's pipe: " + json);
		assertTrue(json.contains("\\u00a7e") && json.contains("\\ud83d\\ude00"), json);
		var o = JsonParser.parseString(json).getAsJsonArray().get(0).getAsJsonObject();
		assertEquals(NON_ASCII, o.get("formatted").getAsString());
		assertEquals("Yellow café → 😀", o.get("text").getAsString());
		assertEquals("Café →", o.get("screen").getAsString());
	}

	@Test
	void actionBarIsAsciiJsonString() {
		MinescriptChat.LOG.fromActionBar(NON_ASCII, NON_ASCII, List.of(), "", 0);
		String json = MinescriptChat.actionBar();
		assertTrue(json.chars().allMatch(c -> c < 0x80), json);
		assertEquals(NON_ASCII, JsonParser.parseString(json).getAsString());
	}

	@Test
	void alertChatLinesAreRecordedAsClient() {
		// alert(chat=True) adds its line with ChatComponent.addClientSystemMessage, which reaches chatBox() through
		// ChatComponentMixin without a receive event first: recorded as "client", not "game".
		long seq = MinescriptChat.LOG.latestSeq();
		assertTrue(!MinescriptChat.chatBox(Component.literal("Inventory full")), "not hidden");
		var lines = MinescriptChat.LOG.since(seq, 0);
		assertEquals(List.of("client:Inventory full"), lines.stream().map(l -> l.source() + ":" + l.text()).toList());
	}

	@Test
	void sinceIsOneJsonArrayChatPyCanRead() {
		long seq = MinescriptChat.LOG.latestSeq();
		MinescriptChat.LOG.fromChatBox("hi", "§ahi", List.of(new ChatLog.Click("run_command", "/x")), "Chest", 5, 0);
		var arr = JsonParser.parseString(MinescriptChat.since((int) seq, 10)).getAsJsonArray();
		assertEquals(1, arr.size());
		var o = arr.get(0).getAsJsonObject();
		assertEquals(seq + 1, o.get("seq").getAsLong());
		assertEquals(5, o.get("time").getAsLong());
		assertEquals("client", o.get("source").getAsString());
		assertEquals("hi", o.get("text").getAsString());
		assertEquals("§ahi", o.get("formatted").getAsString());
		assertEquals("Chest", o.get("screen").getAsString());
		assertEquals("run_command", o.getAsJsonArray("clicks").get(0).getAsJsonObject().get("action").getAsString());
		assertEquals("/x", o.getAsJsonArray("clicks").get(0).getAsJsonObject().get("value").getAsString());
	}
}
