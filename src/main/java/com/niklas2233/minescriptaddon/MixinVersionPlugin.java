package com.niklas2233.minescriptaddon;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * For minescriptaddon.overlay.mixins.json: applies the Hud* mixins on 26.2+ and the Gui* ones on 26.1.x (where
 * those members still live on Gui), so neither variant logs a failure. Checks for Hud.class as a resource,
 * which doesn't load the class.
 */
public class MixinVersionPlugin implements IMixinConfigPlugin {
	private static final boolean HAS_HUD =
			MixinVersionPlugin.class.getClassLoader().getResource("net/minecraft/client/gui/Hud.class") != null;

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (simple.startsWith("Hud")) return HAS_HUD;
		if (simple.startsWith("Gui")) return !HAS_HUD;
		return true;
	}

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
