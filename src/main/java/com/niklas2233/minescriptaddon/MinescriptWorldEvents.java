package com.niklas2233.minescriptaddon;

import com.google.gson.Gson;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Sounds the client plays and particles the server sends, for world_events.py. Only observes: the mixins never
 * change or cancel what the game plays or shows, and a failure here is logged once and otherwise ignored.
 */
public class MinescriptWorldEvents implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("minescriptaddon");
	private static final Gson GSON = new Gson();
	static final EventLog<EventLog.Sound> SOUNDS = new EventLog<>();
	static final EventLog<EventLog.Particle> PARTICLES = new EventLog<>();
	// ClientboundLevelParticlesPacket has getters up to 26.2 and is a record (x(), count()...) in 26.3.
	static final Method PARTICLE = packetGetter("getParticle", "particle"), X = packetGetter("getX", "x"),
			Y = packetGetter("getY", "y"), Z = packetGetter("getZ", "z"), COUNT = packetGetter("getCount", "count");
	private static boolean warned;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SOUNDS.clear();
			PARTICLES.clear();
		});
	}

	/** From SoundManager.play/playDelayed. volume/pitch: what was asked for (1 if the instance doesn't say). */
	public static void onSound(SoundInstance sound, float volume, float pitch) {
		try {
			SOUNDS.add(seq -> new EventLog.Sound(seq, System.currentTimeMillis(), sound.getIdentifier().toString(),
					sound.getSource().getName(), sound.getX(), sound.getY(), sound.getZ(), volume, pitch, sound.isRelative()));
		} catch (RuntimeException e) {
			warnOnce(e);
		}
	}

	/** From ClientPacketListener.handleParticleEvent, on the render thread. */
	public static void onParticles(ClientboundLevelParticlesPacket packet) {
		try {
			var type = ((ParticleOptions) PARTICLE.invoke(packet)).getType();
			String id = BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString();
			double x = (double) X.invoke(packet), y = (double) Y.invoke(packet), z = (double) Z.invoke(packet);
			int count = (int) COUNT.invoke(packet);
			PARTICLES.add(seq -> new EventLog.Particle(seq, System.currentTimeMillis(), id, x, y, z, count));
		} catch (ReflectiveOperationException | RuntimeException e) {
			warnOnce(e);
		}
	}

	// --- Bridge API for world_events.py. Seqs cross as int (see MinescriptChat); radius < 0: no near filter.

	public static int latestSoundSeq() {
		return (int) SOUNDS.latestSeq();
	}

	public static int latestParticleSeq() {
		return (int) PARTICLES.latestSeq();
	}

	/** {"latest": seq, "events": [{"seq", "time", "id", "category", "x", "y", "z", "volume", "pitch", "relative"}]} */
	public static String sounds(int after, double x, double y, double z, double radius) {
		return GSON.toJson(SOUNDS.since(after, x, y, z, radius));
	}

	/** {"latest": seq, "events": [{"seq", "time", "id", "x", "y", "z", "count"}]} */
	public static String particles(int after, double x, double y, double z, double radius) {
		return GSON.toJson(PARTICLES.since(after, x, y, z, radius));
	}

	private static Method packetGetter(String... names) {
		for (String name : names) {
			try {
				return ClientboundLevelParticlesPacket.class.getMethod(name);
			} catch (NoSuchMethodException ignored) {
			}
		}
		throw new IllegalStateException("ClientboundLevelParticlesPacket has none of " + String.join(", ", names));
	}

	private static void warnOnce(Exception e) {
		if (warned) return;
		warned = true;
		LOGGER.warn("world_events: couldn't record an event (further failures are not logged)", e);
	}
}
