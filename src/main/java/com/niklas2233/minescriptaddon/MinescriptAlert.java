package com.niklas2233.minescriptaddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Gets the player's attention for a script (alert.py): a sound only this client hears, a toast, a taskbar flash
 * when the window isn't focused, and optionally a client-side chat line. Scripts call from their own threads, so
 * everything runs on the render thread. Never throws back into the script: each part that fails is logged.
 */
public class MinescriptAlert {
	private static final Logger LOG = LoggerFactory.getLogger("minescriptaddon");
	static final String DEFAULT_SOUND = "minecraft:block.note_block.bell";

	public static void alert(String title, String text, boolean sound, String soundId, double volume, double pitch,
			int repeat, boolean toast, boolean flash, boolean chat) {
		var mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (toast) run("toast", () -> SystemToast.add(toasts(mc), new SystemToast.SystemToastId(), Component.literal(title), Component.literal(text)));
			if (chat && mc.player != null) run("chat line", () -> mc.player.sendSystemMessage(Component.literal(text)));
			if (flash && !mc.getWindow().isFocused()) run("window flash", () -> flash(mc.getWindow().handle()));
			if (sound) run("sound", () -> {
				SoundEvent event = soundEvent(mc, soundId);
				// ponytail: repeat capped at 20 so a typo can't ring for minutes
				for (int i = 0; i < Math.clamp(repeat, 1, 20); i++) {
					CompletableFuture.delayedExecutor(500L * i, TimeUnit.MILLISECONDS).execute(() -> mc.execute(() -> run("sound",
							() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(event, (float) pitch, (float) volume)))));
				}
			});
		});
	}

	/** The sound for id, or the bell (with a warning) if id is invalid or no sound (incl. resource packs) has it. */
	private static SoundEvent soundEvent(Minecraft mc, String soundId) {
		Identifier id = Identifier.tryParse(soundId);
		if (id == null || mc.getSoundManager().getSoundEvent(id) == null) {
			LOG.warn("Unknown sound {}, playing {} instead", soundId, DEFAULT_SOUND);
			id = Identifier.parse(DEFAULT_SOUND);
		}
		return SoundEvent.createVariableRangeEvent(id);
	}

	// Minecraft.getToastManager() in 26.1, Gui.toastManager() in 26.2+.
	static Method toastsGetter() throws NoSuchMethodException {
		try {
			return Minecraft.class.getMethod("getToastManager");
		} catch (NoSuchMethodException e) {
			return Gui.class.getMethod("toastManager");
		}
	}

	private static ToastManager toasts(Minecraft mc) throws ReflectiveOperationException {
		Method m = toastsGetter();
		return (ToastManager) m.invoke(m.getDeclaringClass() == Gui.class ? mc.gui : mc);
	}

	// 26.1-26.2 run on GLFW, 26.3 on SDL; each ships only its own LWJGL module. Looked up without initializing.
	static Method flashMethod() throws ReflectiveOperationException {
		var loader = MinescriptAlert.class.getClassLoader();
		try {
			return Class.forName("org.lwjgl.glfw.GLFW", false, loader).getMethod("glfwRequestWindowAttention", long.class);
		} catch (ClassNotFoundException e) {
			return Class.forName("org.lwjgl.sdl.SDLVideo", false, loader).getMethod("SDL_FlashWindow", long.class, int.class);
		}
	}

	private static void flash(long window) throws ReflectiveOperationException {
		Method m = flashMethod();
		if (m.getParameterCount() == 1) m.invoke(null, window);
		else m.invoke(null, window, m.getDeclaringClass().getField("SDL_FLASH_UNTIL_FOCUSED").getInt(null));
	}

	private interface Part {
		void run() throws Exception;
	}

	private static void run(String what, Part part) {
		try {
			part.run();
		} catch (Exception | LinkageError e) {
			LOG.warn("Alert: couldn't show the {}", what, e);
		}
	}
}
