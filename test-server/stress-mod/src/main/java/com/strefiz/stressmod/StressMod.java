package com.strefiz.stressmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stress test mod for MultiCore benchmarking.
 *
 * <p>Spawns entities across multiple chunks to simulate heavy server load, especially useful
 * for testing the MultiCore mod with multiple independent regions.
 *
 * <p>Uses {@link net.fabricmc.api.ModInitializer} (common entrypoint) so it runs on dedicated
 * and integrated servers. Permission checks use the 26.3 {@link net.minecraft.server.players.PlayerList}
 * API.
 */
public class StressMod implements ModInitializer {

	public static final String MOD_ID = "stress_mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final SpawnManager spawner = new SpawnManager();
	private final BenchRunner bench = new BenchRunner();

	@Override
	public void onInitialize() {
		LOGGER.info("Stress Mod initializing...");
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root =
					Commands.literal("stress")
							.then(Commands.literal("spawn")
									.then(Commands.literal("dense")
											.then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
													.executes(context -> {
														if (!isOp(context.getSource())) return 0;
														spawnDense(context.getSource(),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"));
														return 1;
													})))
									.then(Commands.literal("clusters")
											.then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
													.then(Commands.argument("perCluster", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
															.then(Commands.argument("spacing", com.mojang.brigadier.arguments.IntegerArgumentType.integer(64, 4096))
																	.executes(context -> {
																		if (!isOp(context.getSource())) return 0;
																		spawnClusters(context.getSource(),
																				com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "n"),
																				com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "perCluster"),
																				com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "spacing"));
																		return 1;
																	}))))
											.executes(context -> {
												if (!isOp(context.getSource())) return 0;
												spawnClusters(context.getSource(),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "n"),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "perCluster"),
														1024);
												return 1;
											})))
							.then(Commands.literal("clear")
									.executes(context -> {
										if (!isOp(context.getSource())) return 0;
										clearEntities(context.getSource());
										return 1;
									}))
							.then(Commands.literal("status")
									.executes(context -> {
										if (!isOp(context.getSource())) return 0;
										showStatus(context.getSource());
										return 1;
									}))
							.then(Commands.literal("chunks")
									.then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
											.executes(context -> {
												if (!isOp(context.getSource())) return 0;
												generateChunks(context.getSource(),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"));
												return 1;
											})))
							.then(Commands.literal("set")
									.then(Commands.literal("rate")
											.then(Commands.argument("rate", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
													.executes(context -> {
														if (!isOp(context.getSource())) return 0;
														this.spawner.setRate(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rate"));
														context.getSource().sendSystemMessage(Component.literal("Spawn rate set to " + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rate")));
														return 1;
													})))
									.then(Commands.literal("kind")
											.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
													.executes(context -> {
														if (!isOp(context.getSource())) return 0;
														this.spawner.setKind(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind"));
														context.getSource().sendSystemMessage(Component.literal("Mob kind set to " + com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind")));
														return 1;
													})))
									.then(Commands.literal("noai")
											.then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
													.executes(context -> {
														if (!isOp(context.getSource())) return 0;
														this.spawner.setNoAi(com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value"));
														context.getSource().sendSystemMessage(Component.literal("No-AI set to " + com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")));
														return 1;
													}))))
							.then(Commands.literal("bench")
									.then(Commands.argument("warmup", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 60))
											.then(Commands.argument("duration", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 300))
													.executes(context -> {
														if (!isOp(context.getSource())) return 0;
														MinecraftServer server = context.getSource().getServer();
														boolean started = this.bench.start(server, spawner,
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "warmup"),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "duration"));
														if (!started) {
															context.getSource().sendSystemMessage(Component.literal("Bench could not start (entities still pending spawn)."));
														} else {
															context.getSource().sendSystemMessage(Component.literal("Bench started: warmup=" +
																	com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "warmup") +
																	"s, duration=" +
																	com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "duration") + "s"));
														}
														return 1;
													}))));
			dispatcher.register(root);
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server -> this.bench.onTickStart());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> this.bench.onTickEnd(server));
	}

	private boolean isOp(CommandSourceStack source) {
		if (source.getEntity() == null) return true; // console
		if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
			NameAndId nameAndId = new NameAndId(player.getGameProfile());
			return source.getServer().getPlayerList().isOp(nameAndId);
		}
		return false;
	}

	private void spawnDense(CommandSourceStack source, int count) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		this.spawner.spawnDense(level, count);
		source.sendSystemMessage(Component.literal("Spawning " + count + " entities densely. Pending: " + this.spawner.pendingEntities()));
	}

	private void spawnClusters(CommandSourceStack source, int clusters, int perCluster, int spacing) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		this.spawner.spawnClusters(level, clusters, perCluster, spacing);
		source.sendSystemMessage(Component.literal("Spawning " + clusters + " clusters of " + perCluster +
				" entities, spacing=" + spacing + ". Pending: " + this.spawner.pendingEntities()));
	}

	private void clearEntities(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		this.spawner.clearAll(level);
		source.sendSystemMessage(Component.literal("Cleared all stress entities."));
	}

	private void showStatus(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal("Stress status: " + this.spawner.describe() +
				" | alive=" + this.spawner.aliveCount() +
				" | forced chunks=" + this.spawner.forcedChunkCount()));
	}

	private void generateChunks(CommandSourceStack source, int radius) {
		MinecraftServer server = source.getServer();
		ServerLevel level = server.overworld();
		int generated = 0;

		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			net.minecraft.world.level.ChunkPos center = player.chunkPosition();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(center.x() + dx, center.z() + dz);
					if (level.getChunkSource().getChunk(pos.x(), pos.z(), false) == null) {
						level.getChunkSource().getChunk(pos.x(), pos.z(), true);
						generated++;
					}
				}
			}
		}

		source.sendSystemMessage(Component.literal("Generated " + generated + " chunks in radius " + radius));
	}
}
