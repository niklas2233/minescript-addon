package com.niklas2233.minescriptaddon;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The reflective lookups that differ between 26.x versions resolve against the version CI builds for. */
class VersionCompatTest {
	@Test
	void alertLookupsResolve() {
		assertDoesNotThrow(MinescriptAlert::toastsGetter);
		var flash = assertDoesNotThrow(MinescriptAlert::flashMethod);
		if (flash.getParameterCount() == 2) assertDoesNotThrow(() -> flash.getDeclaringClass().getField("SDL_FLASH_UNTIL_FOCUSED"));
	}

	@Test
	void inputLookupsResolve() {
		assertEquals(MethodType.methodType(boolean.class, com.mojang.blaze3d.platform.Window.class, int.class),
				MinescriptInputs.IS_KEY_DOWN.type());
		assertNotEquals(-1, MinescriptInputs.LEFT);
		assertNotEquals(-1, MinescriptInputs.RIGHT);
		assertNotEquals(-1, MinescriptInputs.MIDDLE);
	}
}
