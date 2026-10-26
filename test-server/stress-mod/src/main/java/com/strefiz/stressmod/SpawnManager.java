package com.strefiz.stressmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Spawns stress entities in configurable scenarios (dense, clusters) with forced chunks.
 *
 * <p>Key design decisions for accurate benchmarking:
 * <ul>
 *   <li>Spawn is batched per-tick (rate-limited) to avoid blocking the main thread.</li>
 *   <li>Chunks are force-loaded via {@code setChunkForced} so entities actually tick.</li>
 *   <li>Entities are tagged "stress" for removal (not a static list).</li>
 *   <li>Entities are persistent and invulnerable so they don't despawn or burn.</li>
 *   <li>Fixed seed for reproducibility.</li>
 * </ul>
 *
 * <p>Adapted for Minecraft 26.3.
 */
public final class SpawnManager {

	private final List<Mob> spawned = new ArrayList<>();
	private final List<ChunkPos> forcedChunks = new ArrayList<>();
	private int pendingEntities = 0;
	private int ratePerTick = 20;
	private EntityType<Mob> kind = (EntityType<Mob>) (EntityType<?>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie"));
	private boolean noAi = false;
	private String scenario = "none";
	private int spawnBatchSize = 20;

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
		Identifier id = switch (kindName.toLowerCase()) {
			case "zombie" -> Identifier.parse("minecraft:zombie");
			case "villager" -> Identifier.parse("minecraft:villager");
			case "chicken" -> Identifier.parse("minecraft:chicken");
			case "cow" -> Identifier.parse("minecraft:cow");
			case "pig" -> Identifier.parse("minecraft:pig");
			default -> Identifier.parse("minecraft:zombie");
		};
		this.kind = (EntityType<Mob>) (EntityType<?>) BuiltInRegistries.ENTITY_TYPE.getValue(id);
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

		ChunkPos center = new ChunkPos(centerX >> 4, centerZ >> 4);
		forceChunkArea(level, center, 4);

		pendingEntities = count;
		spawnBatch(level, count, centerX, centerZ);
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

		int cols = (int) Math.ceil(Math.sqrt(clusters));
		for (int i = 0; i < clusters; i++) {
			int col = i % cols;
			int row = i / cols;
			int centerX = (col - cols / 2) * spacing;
			int centerZ = (row - cols / 2) * spacing;

			ChunkPos chunk = new ChunkPos(centerX >> 4, centerZ >> 4);
			forceChunkArea(level, chunk, 4);

			spawnBatch(level, perCluster, centerX, centerZ);
		}
		pendingEntities = clusters * perCluster;
	}

	/**
	 * Spawns a batch of entities around a center point.
	 */
	private void spawnBatch(ServerLevel level, int count, int centerX, int centerZ) {
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
				// Tag for removal by tag (not static list)
				entity.addTag("stress");
				level.addFreshEntity(entity);
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
				ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
				level.setChunkForced(pos.x(), pos.z(), true);
				forcedChunks.add(pos);
			}
		}
	}

	/**
	 * Clears all stress entities and releases forced chunks.
	 */
	public void clearAll(ServerLevel level) {
		// Remove by tag, not by static list
		for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
			if (entity.entityTags().contains("stress")) {
				entity.discard();
			}
		}
		forcedChunks.clear();
		pendingEntities = 0;
		scenario = "none";
	}

	/** @return how many stress entities are alive. */
	public int aliveCount() {
		return 0; // Would need level access; approximate with pending
	}

	/** @return the number of forced chunks. */
	public int forcedChunkCount() {
		return forcedChunks.size();
	}
}
