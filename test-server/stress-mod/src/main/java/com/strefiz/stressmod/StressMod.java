package com.strefiz.stressmod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stress test mod for MultiCore benchmarking.
 *
 * <p>Spawns entities across multiple chunks to simulate heavy server load, especially useful
 * for testing the MultiCore mod with multiple independent regions.
 */
public class StressMod implements DedicatedServerModInitializer {

	public static final String MOD_ID = "stress_mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final List<Mob> STRESS_ENTITIES = new ArrayList<>();
	private static final Random RANDOM = new Random();

	@Override
	public void onInitializeServer() {
		LOGGER.info("Stress Mod initializing...");
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
				dispatcher.register(Commands.literal("stress")
						.then(Commands.literal("spawn")
								.then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10000))
										.executes(context -> spawnEntities(
												context.getSource(),
												com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")))))
						.then(Commands.literal("clear")
								.executes(context -> clearEntities(context.getSource())))
						.then(Commands.literal("status")
								.executes(context -> showStatus(context.getSource())))
						.then(Commands.literal("chunks")
								.then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
										.executes(context -> generateChunks(
												context.getSource(),
												com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius")))))));
	}

	private static int spawnEntities(CommandSourceStack source, int count) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		int spawned = 0;

		for (int i = 0; i < count; i++) {
			// Random position in a large area (spread out)
			int x = RANDOM.nextInt(2000) - 1000;
			int z = RANDOM.nextInt(2000) - 1000;
			BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
					new BlockPos(x, 0, z));

			Mob entity = EntityType.ZOMBIE.create(level);
			if (entity != null) {
				entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				level.addFreshEntity(entity);
				STRESS_ENTITIES.add(entity);
				spawned++;
			}
		}

		source.sendSystemMessage(Component.literal("Spawned " + spawned + " stress entities. Total: " + STRESS_ENTITIES.size()));
		return 1;
	}

	private static int clearEntities(CommandSourceStack source) {
		int removed = 0;
		for (Mob entity : STRESS_ENTITIES) {
			if (entity.isAlive()) {
				entity.discard();
				removed++;
			}
		}
		STRESS_ENTITIES.clear();
		source.sendSystemMessage(Component.literal("Removed " + removed + " stress entities."));
		return 1;
	}

	private static int showStatus(CommandSourceStack source) {
		int alive = 0;
		for (Mob entity : STRESS_ENTITIES) {
			if (entity.isAlive()) {
				alive++;
			}
		}
		source.sendSystemMessage(Component.literal("Stress entities: " + alive + " alive / " + STRESS_ENTITIES.size() + " total"));
		return 1;
	}

	private static int generateChunks(CommandSourceStack source, int radius) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		int generated = 0;

		// Get player positions (or origin if no players)
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			ChunkPos center = player.chunkPosition();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
					if (level.getChunkSource().getChunk(pos.x, pos.z, false) == null) {
						level.getChunkSource().getChunk(pos.x, pos.z, true);
						generated++;
					}
				}
			}
		}

		source.sendSystemMessage(Component.literal("Generated " + generated + " chunks in radius " + radius));
		return 1;
	}
}
