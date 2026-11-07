package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The open screen and its container slots for screens.py, read on the render thread. Text is returned as
 * ASCII-only JSON (see MinescriptChat.ascii). Without a container screen: no items, and clicks do nothing.
 */
public class MinescriptScreens {
	private static final Gson GSON = new Gson();
	/** A container counts as loaded once a non-player slot has an item, or this long after opening (empty chests). */
	static final long SETTLE_NANOS = 500_000_000L;

	/** The open screen's plain title as a JSON string ("" if none). */
	public static String title() {
		return RenderThread.read(() -> {
			var screen = MinescriptChat.openScreen();
			return json(GSON.toJson(screen == null ? "" : MinescriptChat.plain(screen.getTitle())));
		}, "\"\"");
	}

	/** {"open", "title", "container", "ready"}: ready once a container's contents have arrived (any other screen: at once). */
	public static String state() {
		return RenderThread.read(() -> {
			var screen = MinescriptChat.openScreen();
			var o = new JsonObject();
			o.addProperty("open", screen != null);
			o.addProperty("title", screen == null ? "" : MinescriptChat.plain(screen.getTitle()));
			boolean container = screen instanceof AbstractContainerScreen<?>;
			o.addProperty("container", container);
			o.addProperty("ready", screen != null && (!container || loaded((AbstractContainerScreen<?>) screen)));
			return json(o.toString());
		}, "{\"open\":false,\"title\":\"\",\"container\":false,\"ready\":false}");
	}

	/** Non-empty slots of the open container: ItemJson dicts plus "player": true for the player's own inventory slots. */
	public static String items() {
		return RenderThread.read(() -> {
			var out = new JsonArray();
			if (MinescriptChat.openScreen() instanceof AbstractContainerScreen<?> screen) {
				var slots = screen.getMenu().slots;
				for (int i = 0; i < slots.size(); i++) {
					Slot slot = slots.get(i);
					ItemStack stack = slot.getItem();
					if (stack.isEmpty()) continue;
					var o = ItemJson.of(stack, i);
					o.addProperty("player", slot.container instanceof Inventory);
					out.add(o);
				}
			}
			return json(out.toString());
		}, "[]");
	}

	/**
	 * The game's own click on menu slot `slot` (the "slot" from items()): button "left"/"right" pick up,
	 * "shift" quick-moves, "middle" clones (creative only). False if no container is open or slot is out of range.
	 */
	public static boolean click(int slot, String button) {
		return RenderThread.read(() -> {
			var mc = Minecraft.getInstance();
			if (!(MinescriptChat.openScreen() instanceof AbstractContainerScreen<?> screen) || mc.gameMode == null
					|| mc.player == null) return false;
			var menu = screen.getMenu();
			if (slot < 0 || slot >= menu.slots.size()) return false;
			switch (button) {
				case "left" -> mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
				case "right" -> mc.gameMode.handleContainerInput(menu.containerId, slot, 1, ContainerInput.PICKUP, mc.player);
				case "shift" -> mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
				case "middle" -> mc.gameMode.handleContainerInput(menu.containerId, slot, 2, ContainerInput.CLONE, mc.player);
				default -> {
					return false;
				}
			}
			return true;
		}, false);
	}

	/** Closes the open screen like Escape (a container is closed on the server too). */
	public static void close() {
		RenderThread.read(() -> {
			var screen = MinescriptChat.openScreen();
			if (screen != null) screen.onClose();
			return null;
		}, null);
	}

	private static boolean loaded(AbstractContainerScreen<?> screen) {
		if (System.nanoTime() - MinescriptChat.screenOpenedNanos() >= SETTLE_NANOS) return true;
		return screen.getMenu().slots.stream().anyMatch(s -> !(s.container instanceof Inventory) && s.hasItem());
	}

	private static String json(String s) {
		return MinescriptChat.ascii(s);
	}
}
