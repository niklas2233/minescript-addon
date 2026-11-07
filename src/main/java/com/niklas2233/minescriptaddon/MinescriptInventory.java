package com.niklas2233.minescriptaddon;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;

import java.util.Map;

/**
 * The player's own inventory for inventory.py, read on the render thread, no screen needed. Read only. Slots are
 * the inventory's own numbers: 0-8 hotbar, 9-35 main, 36-39 armor (feet, legs, chest, head), 40 offhand.
 */
public class MinescriptInventory {
	static final int OFFHAND = 40;
	/** inventory.py's equipment() keys, read through the entity's equipment slots. */
	static final Map<String, EquipmentSlot> EQUIPMENT = Map.of("head", EquipmentSlot.HEAD, "chest", EquipmentSlot.CHEST,
			"legs", EquipmentSlot.LEGS, "feet", EquipmentSlot.FEET, "mainhand", EquipmentSlot.MAINHAND, "offhand", EquipmentSlot.OFFHAND);

	/**
	 * {"selected": 0-8, "items": [ItemJson, ...] (slots 0-40, non-empty, by slot), "equipment": {key: ItemJson or
	 * null}}, or null without a player. An equipment item's slot is the inventory slot it is in.
	 */
	public static String snapshot() {
		return RenderThread.read(() -> {
			var player = Minecraft.getInstance().player;
			if (player == null) return "null";
			Inventory inv = player.getInventory();
			var o = new JsonObject();
			o.addProperty("selected", inv.getSelectedSlot());
			var items = new JsonArray();
			for (int i = 0; i <= OFFHAND; i++) {
				var stack = inv.getItem(i);
				if (!stack.isEmpty()) items.add(ItemJson.of(stack, i));
			}
			o.add("items", items);
			var equipment = new JsonObject();
			EQUIPMENT.forEach((key, slot) -> {
				var stack = player.getItemBySlot(slot);
				equipment.add(key, stack.isEmpty() ? JsonNull.INSTANCE : ItemJson.of(stack, inventorySlot(slot, inv.getSelectedSlot())));
			});
			o.add("equipment", equipment);
			return MinescriptChat.ascii(o.toString());
		}, "null");
	}

	/** Where an equipment slot's item sits in the inventory: the selected hotbar slot for the main hand. */
	static int inventorySlot(EquipmentSlot slot, int selected) {
		if (slot == EquipmentSlot.MAINHAND) return selected;
		for (var e : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) if (e.getValue() == slot) return e.getIntKey();
		return -1;
	}
}
