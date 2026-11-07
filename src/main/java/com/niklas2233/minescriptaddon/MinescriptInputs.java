package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Vanilla inputs (attack, use, movement...) held down for scripts through inputs.py. Unlike pressing a key
 * through Minescript directly, a hold expires unless renewed within ttl seconds, so a crashed or killed script
 * lets go. Letting go restores the key's real physical state, so a key the player is holding stays down.
 */
public class MinescriptInputs implements ClientModInitializer {
	static final List<String> KEYS = List.of("attack", "use", "forward", "back", "left", "right", "jump", "sneak", "sprint", "pick");
	private static final Holds HOLDS = new Holds();
	private static final Gson GSON = new Gson();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			HOLDS.expire(System.nanoTime()).forEach(MinescriptInputs::letGo);
			if (client.player == null) HOLDS.releaseAll("").forEach(MinescriptInputs::letGo);
			for (String key : HOLDS.keys()) mapping(key).setDown(true);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(MinescriptInputs::letGoAll));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> letGoAll());
	}

	// --- Bridge API for inputs.py. Unknown keys throw IllegalArgumentException listing the valid ones.

	public static void hold(String key, String owner, double ttl) {
		mapping(key);
		HOLDS.hold(key, owner, ttl, System.nanoTime()); // pressed on the next tick, like every renewal
	}

	public static void release(String key) {
		mapping(key);
		if (HOLDS.release(key)) Minecraft.getInstance().execute(() -> letGo(key));
	}

	/** Releases the holds whose owner starts with prefix ("" = all). */
	public static void releaseAll(String prefix) {
		var keys = HOLDS.releaseAll(prefix);
		Minecraft.getInstance().execute(() -> keys.forEach(MinescriptInputs::letGo));
	}

	/** [[key, owner, secondsLeft], ...] as ASCII-only JSON (see MinescriptChat.ascii). */
	public static String held() {
		var rows = HOLDS.held(System.nanoTime()).stream().map(h -> List.of(h.key(), h.owner(), h.secondsLeft())).toList();
		return MinescriptChat.ascii(GSON.toJson(rows));
	}

	// --- Render thread only

	private static void letGoAll() {
		HOLDS.releaseAll("").forEach(MinescriptInputs::letGo);
	}

	private static void letGo(String key) {
		KeyMapping m = mapping(key);
		m.setDown(!MinescriptChat.screenOpen() && physicallyDown(m));
	}

	private static KeyMapping mapping(String key) {
		var o = Minecraft.getInstance().options;
		return switch (key) {
			case "attack" -> o.keyAttack;
			case "use" -> o.keyUse;
			case "forward" -> o.keyUp;
			case "back" -> o.keyDown;
			case "left" -> o.keyLeft;
			case "right" -> o.keyRight;
			case "jump" -> o.keyJump;
			case "sneak" -> o.keyShift;
			case "sprint" -> o.keySprint;
			case "pick" -> o.keyPickItem;
			case null, default -> throw new IllegalArgumentException("unknown key " + key + ", valid keys: " + String.join(", ", KEYS));
		};
	}

	// Mouse button numbers changed in 26.3 (GLFW 0/1/2 -> SDL 1/3/2) and the constants are inlined at compile
	// time, so they're read at runtime.
	static final int LEFT = constant("MOUSE_BUTTON_LEFT"), RIGHT = constant("MOUSE_BUTTON_RIGHT"), MIDDLE = constant("MOUSE_BUTTON_MIDDLE");
	// InputConstants.isKeyDown(Window, int) in 26.1-26.2, isKeyDown(int) in 26.3.
	static final MethodHandle IS_KEY_DOWN = isKeyDown();

	private static boolean physicallyDown(KeyMapping m) {
		var mc = Minecraft.getInstance();
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(m);
		if (key.equals(InputConstants.UNKNOWN)) return false;
		if (key.getType() == InputConstants.Type.MOUSE) {
			int b = key.getValue();
			// ponytail: Minecraft only tracks left/right/middle; a script hold on a side-button binding is let go
			return b == LEFT ? mc.mouseHandler.isLeftPressed() : b == RIGHT ? mc.mouseHandler.isRightPressed()
					: b == MIDDLE && mc.mouseHandler.isMiddlePressed();
		}
		try {
			return (boolean) IS_KEY_DOWN.invokeExact(mc.getWindow(), key.getValue());
		} catch (Throwable e) {
			return false;
		}
	}

	private static int constant(String name) {
		try {
			return InputConstants.class.getField(name).getInt(null);
		} catch (ReflectiveOperationException e) {
			return -1;
		}
	}

	private static MethodHandle isKeyDown() {
		var lookup = MethodHandles.publicLookup();
		try {
			return lookup.findStatic(InputConstants.class, "isKeyDown", MethodType.methodType(boolean.class, Window.class, int.class));
		} catch (ReflectiveOperationException e) {
			try {
				return MethodHandles.dropArguments(lookup.findStatic(InputConstants.class, "isKeyDown",
						MethodType.methodType(boolean.class, int.class)), 0, Window.class);
			} catch (ReflectiveOperationException e2) {
				throw new LinkageError("InputConstants.isKeyDown not found", e2);
			}
		}
	}
}
