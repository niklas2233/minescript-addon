package com.niklas2233.minescriptaddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

/**
 * Lookups that moved between 26.x versions. In 26.1 Gui owns the chat, tab list, titles and F1 state; 26.2
 * moved them to Gui.hud (class Hud), replaced Options.hideGui with Hud.isHidden() and moved Minecraft.setScreen to
 * Gui.setScreen. Checked by VersionCompatTest.
 */
final class Compat {
	static final Field HUD_FIELD = hudField(); // null in 26.1

	/** The class that owns getChat()/getTabList()/the title fields: Gui in 26.1, Hud in 26.2+. */
	static Class<?> hudClass() {
		return HUD_FIELD == null ? Gui.class : HUD_FIELD.getType();
	}

	static Object hud(Minecraft mc) {
		try {
			return HUD_FIELD == null ? mc.gui : HUD_FIELD.get(mc.gui);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	static ChatComponent chat(Minecraft mc) {
		return (ChatComponent) call(hud(mc), "getChat");
	}

	static PlayerTabOverlay tabList(Minecraft mc) {
		return (PlayerTabOverlay) call(hud(mc), "getTabList");
	}

	/** True while the HUD is hidden with F1. */
	static boolean hudHidden(Minecraft mc) {
		if (HUD_FIELD != null) return (boolean) call(hud(mc), "isHidden");
		try {
			return Options.class.getField("hideGui").getBoolean(mc.options);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Minecraft.setScreen in 26.1, Gui.setScreen in 26.2+. */
	static void setScreen(Minecraft mc, Screen screen) {
		try {
			try {
				Gui.class.getMethod("setScreen", Screen.class).invoke(mc.gui, screen);
			} catch (NoSuchMethodException e) {
				Minecraft.class.getMethod("setScreen", Screen.class).invoke(mc, screen);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object call(Object target, String method) {
		try {
			return hudClass().getMethod(method).invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field hudField() {
		try {
			return Gui.class.getField("hud");
		} catch (NoSuchFieldException e) {
			return null;
		}
	}
}
