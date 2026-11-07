package com.niklas2233.minescriptaddon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The item dict shared by screens.py and inventory.py, so both have exactly the same fields:
 * {"slot", "id", "count", "name", "lore", "custom_data", "damage", "max_damage"}. Names and lore are plain text.
 */
final class ItemJson {
	private ItemJson() {}

	static JsonObject of(ItemStack stack, int slot) {
		var o = new JsonObject();
		o.addProperty("slot", slot);
		o.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		o.addProperty("count", stack.getCount());
		o.addProperty("name", MinescriptChat.plain(stack.getHoverName()));
		var lore = new JsonArray();
		var itemLore = stack.get(DataComponents.LORE);
		if (itemLore != null) itemLore.lines().forEach(l -> lore.add(MinescriptChat.plain(l)));
		o.add("lore", lore);
		o.add("custom_data", customData(stack.get(DataComponents.CUSTOM_DATA)));
		boolean damageable = stack.isDamageableItem();
		o.addProperty("damage", damageable ? stack.getDamageValue() : 0);
		o.addProperty("max_damage", damageable ? stack.getMaxDamage() : 0);
		return o;
	}

	/** The item's custom_data as JSON (NBT number types are lost), or null if it has none or can't be converted. */
	private static JsonElement customData(CustomData data) {
		if (data == null || data.isEmpty()) return JsonNull.INSTANCE;
		try {
			return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data.copyTag());
		} catch (RuntimeException e) {
			return JsonNull.INSTANCE; // one odd item shouldn't hide the rest
		}
	}
}
