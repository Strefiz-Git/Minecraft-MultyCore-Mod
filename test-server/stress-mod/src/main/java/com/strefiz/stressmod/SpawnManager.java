package com.strefiz.stressmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Spawns stress entities in configurable scenarios (dense, clusters) with forced chunks.
 *
 * <p>Adapted for Minecraft 26.3. Uses {@link EntitySpawnReason#COMMAND} for spawning.
 */
public final class SpawnManager {

	private final List<Mob> spawned = new ArrayList<>();
	private final List<ChunkPos> forcedChunks = new ArrayList<>();
	private int pendingEntities = 0;
	private int ratePerTick = 20;
	private EntityType<? extends Mob> kind = EntityType.ZOMBIE;
	private boolean noAi = false;
	private String scenario = "none";

	private final Random random = new Random(42L); // fixed seed for reproducibility

	public SpawnManager() {
	}

	/** @return how many entities are still pending spawn. */
	public int pendingEntities() {
		return pendingEntities;
	}

	/** @return a short description of the current scenario. */
	public String describe() {
		return scenario + " (" + spawned.size() + " entities, kind=" + kind +
				", noAi=" + noAi + ", rate=" + ratePerTick + "/tick)";
	}

	/** @return how many stress entities are currently alive. */
	public int alive(Level level) {
		int count = 0;
		for (Mob m : spawned) {
			if (m.isAlive()) count++;
		}
		return count;
	}

	/**
	 * Sets the spawn rate (entities per tick). Must be >= 1.
	 */
	public void setRate(int rate) {
		this.ratePerTick = Math.max(1, rate);
	}

	/**
	 * Sets the mob kind.
	 */
	public void setKind(String kindName) {
		switch (kindName.toLowerCase()) {
			case "zombie" -> this.kind = EntityType.ZOMBIE;
			case "villager" -> this.kind = EntityType.VILLAGER;
			case "chicken" -> this.kind = EntityType.CHICKEN;
			case "cow" -> this.kind = EntityType.COW;
			case "pig" -> this.kind = EntityType.PIG;
			default -> this.kind = EntityType.ZOMBIE;
		}
	}

	/**
	 * Toggles AI on/off for spawned entities.
	 */
	public void setNoAi(boolean noAi) {
		this.noAi = noAi;
	}

	/**
	 * Spawns entities in a dense cluster (all in the same area).
	 * All entities are in the same region → tests single-region performance.
	 */
	public void spawnDense(ServerLevel level, int count) {
		clearAll(level);
		scenario = "dense";
		int centerX = 0;
		int centerZ = 0;

		// Force the chunks
		ChunkPos center = new ChunkPos(centerX >> 4, centerZ >> 4);
		forceChunkArea(level, center, 4);

		pendingEntities = count;
		// Store spawn parameters for batch processing
		spawnBatch(level, count, centerX, centerZ, 0);
	}

	/**
	 * Spawns entities in independent clusters on a grid.
	 * Each cluster is far apart → tests multi-region parallelism.
	 *
	 * @param level the server level
	 * @param clusters number of clusters
	 * @param perCluster entities per cluster
	 * @param spacing block distance between cluster centers
	 */
	public void spawnClusters(ServerLevel level, int clusters, int perCluster, int spacing) {
		clearAll(level);
		scenario = "clusters(" + clusters + "x" + perCluster + ", spacing=" + spacing + ")";

		int total = clusters * perCluster;
		pendingEntities = total;

		int cols = (int) Math.ceil(Math.sqrt(clusters));
		for (int i = 0; i < clusters; i++) {
			int col = i % cols;
			int row = i / cols;
			int centerX = (col - cols / 2) * spacing;
			int centerZ = (row - cols / 2) * spacing;

			ChunkPos chunk = new ChunkPos(centerX >> 4, centerZ >> 4);
			forceChunkArea(level, chunk, 4);

			spawnBatch(level, perCluster, centerX, centerZ, i * perCluster);
		}
	}

	/**
	 * Spawns a batch of entities around a center point.
	 */
	private void spawnBatch(ServerLevel level, int count, int centerX, int centerZ, int startIndex) {
		int spawned = 0;
		int attempts = 0;
		while (spawned < count && attempts < count * 3) {
			attempts++;
			int dx = random.nextInt(32) - 16;
			int dz = random.nextInt(32) - 16;
			BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
					new BlockPos(centerX + dx, 0, centerZ + dz));

			Mob entity = kind.create(level, EntitySpawnReason.COMMAND);
			if (entity != null) {
				entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				entity.setPersistenceRequired();
				if (noAi) {
					entity.setNoAi(true);
				}
				level.addFreshEntity(entity);
				entity.setInvulnerable(true);
				STRESS_ENTITIES.add(entity);
				spawned++;
			}
		}
		pendingEntities = Math.max(0, pendingEntities - spawned);
	}

	/**
	 * Forces chunks in a square area to load and stay loaded.
	 */
	private void forceChunkArea(ServerLevel level, ChunkPos center, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
				level.setChunkForced(pos.x, pos.z, true);
				forcedChunks.add(pos);
			}
		}
	}

	/**
	 * Clears all stress entities and releases forced chunks.
	 */
	public void clearAll(ServerLevel level) {
		for (Mob entity : STRESS_ENTITIES) {
			if (entity.isAlive()) {
				entity.discard();
			}
		}
		STRESS_ENTITIES.clear();

		for (ChunkPos pos : forcedChunks) {
			level.setChunkForced(pos.x, pos.z, false);
		}
		forcedChunks.clear();
		pendingEntities = 0;
		scenario = "none";
	}

	/** @return how many stress entities are alive. */
	public int aliveCount() {
		int count = 0;
		for (Mob m : STRESS_ENTITIES) {
			if (m.isAlive()) count++;
		}
		return count;
	}

	/** @return the number of forced chunks. */
	public int forcedChunkCount() {
		return forcedChunks.size();
	}
}
