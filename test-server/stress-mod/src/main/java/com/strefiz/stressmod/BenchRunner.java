package com.strefiz.stressmod;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

/**
 * Measures real MSPT (START_SERVER_TICK -> END_SERVER_TICK) after a warmup, plus CPU time per
 * thread group, GC activity and heap. Writes a JSON report to <gameDir>/stress-results/.
 *
 * <p>Do not spawn/clear entities while a benchmark is running.
 *
 * <p>Adapted for Minecraft 26.3: uses {@link net.minecraft.server.players.NameAndId} for OP
 * checks and {@link net.fabricmc.api.ModInitializer} for the entrypoint.
 */
public final class BenchRunner {

	private enum Phase { IDLE, WARMUP, MEASURE }

	private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
	private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

	private Phase phase = Phase.IDLE;
	private int warmupLeft;
	private double[] samples;
	private int n;
	private long tickStartNs;
	private long wallStartNs;
	private Map<Long, Long> cpuStart;
	private long[] gcCountStart;
	private long[] gcTimeStart;
	private String scenario = "";
	private int entities;
	private int warmupSec;
	private int durationSec;
	private SpawnManager spawner;

	public boolean running() { return phase != Phase.IDLE; }

	public boolean start(MinecraftServer server, SpawnManager spawner, int warmupSec, int durationSec) {
		if (running() || spawner.pendingEntities() > 0) {
			return false;
		}
		this.spawner = spawner;
		this.warmupSec = warmupSec;
		this.durationSec = durationSec;
		this.warmupLeft = warmupSec * 20;
		this.samples = new double[durationSec * 20];
		this.n = 0;
		this.scenario = spawner.describe();
		this.entities = spawner.alive(server.overworld());
		this.phase = warmupLeft > 0 ? Phase.WARMUP : Phase.MEASURE;
		if (phase == Phase.MEASURE) {
			beginMeasure();
		}
		return true;
	}

	public void onTickStart() {
		if (phase != Phase.IDLE) {
			tickStartNs = System.nanoTime();
		}
	}

	public void onTickEnd(MinecraftServer server) {
		switch (phase) {
			case IDLE -> { }
			case WARMUP -> {
				if (--warmupLeft <= 0) {
					beginMeasure();
				}
			}
			case MEASURE -> {
				samples[n++] = (System.nanoTime() - tickStartNs) / 1_000_000.0;
				if (n >= samples.length) {
					finish(server);
				}
			}
		}
	}

	private void beginMeasure() {
		phase = Phase.MEASURE;
		cpuStart = new java.util.HashMap<>();
		for (long id : threads.getAllThreadIds()) {
			long cpu = threads.getThreadCpuTime(id);
			if (cpu >= 0) {
				cpuStart.put(id, cpu);
			}
		}
		gcCountStart = new long[gcBeans.size()];
		gcTimeStart = new long[gcBeans.size()];
		for (int i = 0; i < gcBeans.size(); i++) {
			gcCountStart[i] = gcBeans.get(i).getCollectionCount();
			gcTimeStart[i] = gcBeans.get(i).getCollectionTime();
		}
		wallStartNs = System.nanoTime();
	}

	private void finish(MinecraftServer server) {
		phase = Phase.IDLE;
		double wallMs = (System.nanoTime() - wallStartNs) / 1_000_000.0;

		double[] sorted = Arrays.copyOf(samples, n);
		Arrays.sort(sorted);
		double avg = Arrays.stream(sorted).average().orElse(0);
		int over50 = (int) Arrays.stream(sorted).filter(v -> v > 50.0).count();

		// CPU time per thread group (names without trailing numbers)
		Map<String, Double> cpuByGroup = new TreeMap<>();
		double totalCpuMs = 0;
		for (long id : threads.getAllThreadIds()) {
			long now = threads.getThreadCpuTime(id);
			ThreadInfo info = threads.getThreadInfo(id);
			if (now < 0 || info == null) {
				continue;
			}
			double ms = (now - cpuStart.getOrDefault(id, 0L)) / 1_000_000.0;
			String group = info.getThreadName().replaceAll("[-_ #]?\\d+$", "");
			cpuByGroup.merge(group, ms, Double::sum);
			totalCpuMs += ms;
		}

		StringBuilder gc = new StringBuilder();
		for (int i = 0; i < gcBeans.size(); i++) {
			if (i > 0) gc.append(",");
			gc.append("{\"name\":\"").append(esc(gcBeans.get(i).getName())).append("\",\"count\":")
					.append(gcBeans.get(i).getCollectionCount() - gcCountStart[i]).append(",\"timeMs\":")
					.append(gcBeans.get(i).getCollectionTime() - gcTimeStart[i]).append("}");
		}

		Runtime rt = Runtime.getRuntime();
		StringBuilder json = new StringBuilder("{\n");
		json.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
		json.append("  \"scenario\": \"").append(esc(scenario)).append("\",\n");
		json.append("  \"entitiesAlive\": ").append(entities).append(",\n");
		json.append("  \"warmupSec\": ").append(warmupSec).append(", \"durationSec\": ").append(durationSec).append(",\n");
		json.append("  \"ticks\": ").append(n).append(",\n");
		json.append(String.format(java.util.Locale.ROOT,
				"  \"mspt\": {\"avg\": %.2f, \"p50\": %.2f, \"p95\": %.2f, \"p99\": %.2f, \"max\": %.2f},\n",
				avg, pct(sorted, 0.50), pct(sorted, 0.95), pct(sorted, 0.99), sorted.length == 0 ? 0 : sorted[sorted.length - 1]));
		json.append("  \"ticksOver50ms\": ").append(over50).append(",\n");
		json.append(String.format(java.util.Locale.ROOT, "  \"avgCoresBusy\": %.2f,\n", totalCpuMs / wallMs));
		json.append("  \"cpuMsByThreadGroup\": {");
		boolean first = true;
		for (var e : cpuByGroup.entrySet()) {
			if (e.getValue() < 1.0) continue;
			json.append(first ? "" : ", ").append("\"").append(esc(e.getKey())).append("\": ")
					.append(String.format(java.util.Locale.ROOT, "%.0f", e.getValue()));
			first = false;
		}
		json.append("},\n");
		json.append("  \"gc\": [").append(gc).append("],\n");
		json.append("  \"heapUsedMb\": ").append((rt.totalMemory() - rt.freeMemory()) >> 20)
				.append(", \"heapMaxMb\": ").append(rt.maxMemory() >> 20).append(",\n");
		json.append("  \"availableProcessors\": ").append(rt.availableProcessors()).append(",\n");
		json.append("  \"players\": ").append(server.getPlayerList().getPlayerCount()).append(",\n");
		json.append("  \"multicoreConfigRaw\": \"").append(esc(readMulticoreConfig())).append("\"\n}\n");

		Path file = null;
		try {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("stress-results");
			Files.createDirectories(dir);
			file = dir.resolve("bench-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".json");
			Files.writeString(file, json.toString());
		} catch (IOException ex) {
			StressMod.LOGGER.error("Could not write benchmark report", ex);
		}

		String msg = String.format(java.util.Locale.ROOT,
				"Bench done: avg %.1f ms | p95 %.1f | p99 %.1f | max %.1f | >50ms: %d | cores busy ~%.2f | %s",
				avg, pct(sorted, 0.95), pct(sorted, 0.99), sorted.length == 0 ? 0 : sorted[sorted.length - 1],
				over50, totalCpuMs / wallMs, file == null ? "(report not saved)" : file.getFileName());
		StressMod.LOGGER.info(msg);
		server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
	}

	private static double pct(double[] sorted, double p) {
		if (sorted.length == 0) return 0;
		int idx = Math.max(0, (int) Math.ceil(p * sorted.length) - 1);
		return sorted[idx];
	}

	private static String readMulticoreConfig() {
		try {
			Path cfg = FabricLoader.getInstance().getConfigDir().resolve("multicore.json");
			return Files.exists(cfg) ? Files.readString(cfg) : "(config/multicore.json not found)";
		} catch (IOException e) {
			return "(could not read config)";
		}
	}

	private static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "  ");
	}
}
