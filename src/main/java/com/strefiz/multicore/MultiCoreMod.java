package com.strefiz.multicore;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.strefiz.multicore.command.MultiCoreCommand;
import com.strefiz.multicore.command.StatusSource;
import com.strefiz.multicore.compat.LegacyHandoff;
import com.strefiz.multicore.compat.ModClassification;
import com.strefiz.multicore.compat.ModCompatibility;
import com.strefiz.multicore.compat.MultiCoreCompat;
import com.strefiz.multicore.config.ConfigMode;
import com.strefiz.multicore.config.ConfigStore;
import com.strefiz.multicore.config.MultiCoreConfig;
import com.strefiz.multicore.metrics.MetricsRollup;
import com.strefiz.multicore.metrics.TickMetrics;
import com.strefiz.multicore.region.Region;
import com.strefiz.multicore.region.RegionMessageQueue;
import com.strefiz.multicore.region.RegionScheduler;
import com.strefiz.multicore.region.RegionSection;
import com.strefiz.multicore.thread.MainThread;
import com.strefiz.multicore.thread.OffloadRegistry;
import com.strefiz.multicore.thread.Safety;
import com.strefiz.multicore.thread.TaskScheduler;
import com.strefiz.multicore.thread.ThreadChecker;
import com.strefiz.multicore.thread.WorkerPool;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the MultiCore mod.
 *
 * <p>Uses the common {@code main} entrypoint (not the dedicated-server one) so the mod also runs
 * on the <em>integrated server</em> (singleplayer/LAN): there the entrypoint fires on the client and
 * the server events below are dispatched by the integrated server. On a dedicated server the same
 * entrypoint fires on the server. The Mod Menu screen edits the same {@code config/multicore.json}
 * in both cases.
 *
 * <p>Bootstraps the configuration, the worker pool, the scheduler, the metrics and the
 * {@code /multicore} command, and wires the lifecycle:
 *
 * <ul>
 *   <li>{@code SERVER_STARTING} — capture the main thread, load the config, start the pool;</li>
 *   <li>{@code START_SERVER_TICK} — start measuring and submit the offloads;</li>
 *   <li>{@code END_SERVER_TICK} — merge offloaded results on the main thread, stop measuring;</li>
 *   <li>{@code SERVER_STOPPED} — shut the pool down.</li>
 * </ul>
 *
 * <p>It also implements {@link StatusSource} so the command reads the live state from one place.
 */
public class MultiCoreMod implements ModInitializer, StatusSource, MultiCoreCompat.Handler {

	/** Mod id, used for logging and config lookup. */
	public static final String MOD_ID = "multicore";

	/** Logger for the whole mod. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final TickMetrics metrics = new TickMetrics();
	private final AtomicReference<String> rollup = new AtomicReference<>("");

	private MultiCoreConfig config;
	private WorkerPool pool;
	private TaskScheduler scheduler;
	private OffloadRegistry offloads;
	private ModCompatibility compat;
	private LegacyHandoff legacyHandoff;
	private ThreadChecker threadChecker = ThreadChecker.debug(false);
	private RegionScheduler regionScheduler;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
				MultiCoreCommand.register(dispatcher, this));
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
		ServerTickEvents.START_SERVER_TICK.register(this::onStartTick);
		ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
	}

	private void onServerStarting(MinecraftServer server) {
		MainThread.capture();
		config = ConfigStore.defaultStore().load();
		pool = new WorkerPool("multicore-worker", config.workerThreads());
		scheduler = new TaskScheduler(pool, threadChecker);
		offloads = new OffloadRegistry(scheduler);
		offloads.register("metrics-rollup", Safety.PURE,
				() -> new MetricsRollup(metrics).hottest(5), rollup::set);
		compat = ModCompatibility.scan();
		legacyHandoff = LegacyHandoff.create();
		MultiCoreCompat.setHandler(this);
		threadChecker = ThreadChecker.debug(config.debug());
		regionScheduler = new RegionScheduler(pool, scheduler, threadChecker);
		pool.start();

		if (config.multithreaded()) {
			LOGGER.info("MultiCore active: {} worker thread(s), mode={}.",
					config.workerThreads(), config.mode());
		} else {
			LOGGER.info("MultiCore loaded with 0 workers — behaving like vanilla.");
		}
	}

	private void onStartTick(MinecraftServer server) {
		metrics.startTick();
		if (offloads != null) {
			Safety maxAllowed = config != null && config.mode() == ConfigMode.AGGRESSIVE
					? Safety.WORLD_STATE : Safety.PURE;
			offloads.submitAll(maxAllowed);
		}
	}

	private void onEndTick(MinecraftServer server) {
		if (scheduler != null) {
			scheduler.drainToMain();
		}
		if (regionScheduler != null) {
			regionScheduler.awaitBarrier();
		}
		if (legacyHandoff != null) {
			legacyHandoff.drainToMain();
		}
		metrics.endTick();
	}

	private void onServerStopped(MinecraftServer server) {
		if (pool != null) {
			pool.shutdown();
		}
	}

	@Override
	public String mode() {
		return config == null ? ConfigMode.SAFE.name() : config.mode().name();
	}

	@Override
	public int workerThreads() {
		return config == null ? 0 : config.workerThreads();
	}

	@Override
	public int threadCount() {
		return config == null ? 1 : config.threadCount();
	}

	@Override
	public TickMetrics.Snapshot metrics() {
		return metrics.snapshot();
	}

	@Override
	public Map<String, Double> threadTimesMs() {
		return metrics.threadTimesMs();
	}

	@Override
	public String metricsRollup() {
		return rollup.get();
	}

	@Override
	public String compatSummary() {
		if (compat == null) {
			return "";
		}
		return String.format("legacy=%d compatible=%d of %d",
				compat.legacyCount(), compat.compatibleCount(), compat.size());
	}

	@Override
	public String compatDump() {
		if (compat == null) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		text.append("MultiCore compatibility dump (").append(compat.size()).append(" mods):");
		for (Map.Entry<String, ModClassification> entry : compat.classifications().entrySet()) {
			text.append(String.format("%n  %s: %s", entry.getKey(), entry.getValue()));
		}
		return text.toString();
	}

	@Override
	public String regionSummary() {
		if (regionScheduler == null) {
			return "";
		}
		return regionScheduler.hasTickingRegions() ? "ticking regions active" : "no regions ticking this tick";
	}

	// --- MultiCoreCompat.Handler implementation ---

	@Override
	public void runOnMain(Runnable callback) {
		if (legacyHandoff != null) {
			legacyHandoff.enqueue(callback);
		} else {
			callback.run();
		}
	}

	@Override
	public boolean isThreadSafe() {
		return config != null && config.mode() == ConfigMode.AGGRESSIVE;
	}
}
