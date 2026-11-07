package com.niklas2233.minescriptaddon;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The item dict screens.py and inventory.py share, built from real items. */
class ItemJsonTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// 26.x binds items' default components (durability, name...) when registries load, not at bootstrap
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
	}

	@Test
	void namedLoredDamagedItem() {
		var sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("§bShiny"));
		sword.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Line one"), Component.literal("Café"))));
		var tag = new CompoundTag();
		tag.putInt("k", 1);
		tag.putString("id", "SWORD_1");
		sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		sword.setDamageValue(12);
		assertEquals(JsonParser.parseString("""
				{"slot": 3, "id": "minecraft:diamond_sword", "count": 1, "name": "Shiny", "lore": ["Line one", "Café"],
				 "custom_data": {"k": 1, "id": "SWORD_1"}, "damage": 12, "max_damage": 1561}"""),
				JsonParser.parseString(ItemJson.of(sword, 3).toString()));
	}

	@Test
	void plainStackHasNoDurability() {
		var dirt = new ItemStack(Items.DIRT, 64);
		assertEquals(JsonParser.parseString("""
				{"slot": 40, "id": "minecraft:dirt", "count": 64, "name": "Dirt", "lore": [], "custom_data": null,
				 "damage": 0, "max_damage": 0}"""),
				JsonParser.parseString(ItemJson.of(dirt, 40).toString()));
	}

	@Test
	void equipmentSlotsMapToInventorySlots() {
		assertEquals(List.of(39, 38, 37, 36, 40, 5),
				List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND,
						EquipmentSlot.MAINHAND).stream().map(s -> MinescriptInventory.inventorySlot(s, 5)).toList());
	}
}
