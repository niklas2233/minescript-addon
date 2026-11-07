package com.niklas2233.minescriptaddon.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface SoundInstanceAccessor {
	@Accessor("volume")
	float minescriptaddon$volume();

	@Accessor("pitch")
	float minescriptaddon$pitch();
}
