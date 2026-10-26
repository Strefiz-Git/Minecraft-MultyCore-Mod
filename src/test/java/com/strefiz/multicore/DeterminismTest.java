package com.strefiz.multicore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.strefiz.multicore.config.ConfigMode;
import com.strefiz.multicore.config.MultiCoreConfig;
import com.strefiz.multicore.compat.ModClassification;
import com.strefiz.multicore.compat.ModCompatibility;
import com.strefiz.multicore.thread.Safety;
import com.strefiz.multicore.thread.TaskScheduler;
import com.strefiz.multicore.thread.ThreadChecker;
import com.strefiz.multicore.thread.WorkerPool;

/**
 * Integration tests verifying that in SAFE mode the mod does not alter game behaviour.
 *
 * <p>These tests exercise the full pipeline (config → pool → scheduler → offload → merge) without
 * touching Minecraft classes, proving the contract: {@code workerThreads = 0} is equivalent to
 * vanilla, and legacy mods are never executed off the main thread.
 */
class DeterminismTest {

	@Test
	void zeroWorkersMeansNoThreadsAndNoOffload() {
		MultiCoreConfig config = new MultiCoreConfig().withWorkerThreads(0);

		assertFalse(config.multithreaded());
		assertEquals(1, config.threadCount());
	}

	@Test
	void zeroWorkersSchedulerRunsInline() {
		TaskScheduler scheduler = new TaskScheduler(new WorkerPool("test", 0), ThreadChecker.debug(false));
		AtomicInteger workCount = new AtomicInteger();
		AtomicInteger mergeCount = new AtomicInteger();

		scheduler.submitOffload(() -> {
			workCount.incrementAndGet();
			return 42;
		}, value -> mergeCount.incrementAndGet());

		assertEquals(1, workCount.get(), "work must run inline when the pool is disabled");
		assertEquals(1, mergeCount.get(), "merge must be applied inline");
		assertFalse(scheduler.hasPending());
	}

	@Test
	void safeModeDoesNotExposeWorldStateOffloads() {
		// In SAFE mode, only PURE (non-world-state) offloads may run.
		// A WORLD_STATE offload must be skipped.
		TaskScheduler scheduler = new TaskScheduler(new WorkerPool("test", 2), ThreadChecker.debug(false));
		scheduler.submitOffload(() -> 1, v -> { }, com.strefiz.multicore.thread.DeadlinePolicy.SKIP);
		// The scheduler itself doesn't know about Safety; that's the OffloadRegistry's job.
		// Verify the registry enforces it.
		WorkerPool pool = new WorkerPool("test", 2);
		pool.start();
		TaskScheduler sched = new TaskScheduler(pool, ThreadChecker.debug(false));
		com.strefiz.multicore.thread.OffloadRegistry registry = new com.strefiz.multicore.thread.OffloadRegistry(sched);
		AtomicInteger ran = new AtomicInteger();
		registry.register("world-state", com.strefiz.multicore.thread.Safety.WORLD_STATE,
				() -> 1, v -> ran.incrementAndGet());
		registry.submitAll(com.strefiz.multicore.thread.Safety.PURE); // SAFE mode
		assertEquals(0, sched.drainToMain());
		assertEquals(0, ran.get(), "WORLD_STATE offload must not run in SAFE mode");
		pool.shutdown();
	}

	@Test
	void legacyModsAreNeverMarkedCompatible() {
		// A mod without the threadSafe flag must be LEGACY.
		ModCompatibility compat = new ModCompatibility();
		// No scan() (no Fabric Loader in test); manually classify
		compat.classify("legacy-mod", ModClassification.LEGACY);
		compat.classify("safe-mod", ModClassification.COMPATIBLE);

		assertTrue(compat.isLegacy("legacy-mod"));
		assertFalse(compat.isCompatible("legacy-mod"));
		assertTrue(compat.isCompatible("safe-mod"));
	}

	@Test
	void offloadOrderingPreservesSubmissionOrder() throws InterruptedException {
		WorkerPool pool = new WorkerPool("test", 4);
		pool.start();
		TaskScheduler scheduler = new TaskScheduler(pool, ThreadChecker.debug(false));
		java.util.List<Integer> order = new java.util.ArrayList<>();

		for (int i = 0; i < 10; i++) {
			final int value = i;
			scheduler.submitOffload(() -> {
				Thread.sleep((10 - value) * 5); // force out-of-order completion
				return value;
			}, order::add);
		}

		assertEquals(10, scheduler.drainToMain());
		assertEquals(java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), order,
				"merges must follow submission order, not completion order");
		pool.shutdown();
	}

	@Test
	void barrierWaitsForAllOffloadedWork() throws InterruptedException {
		WorkerPool pool = new WorkerPool("test", 2);
		pool.start();
		TaskScheduler scheduler = new TaskScheduler(pool, ThreadChecker.debug(false));
		AtomicInteger mergedCount = new AtomicInteger();

		scheduler.submitOffload(() -> {
			Thread.sleep(50);
			return "slow";
		}, value -> mergedCount.incrementAndGet());

		scheduler.submitOffload(() -> {
			Thread.sleep(10);
			return "fast";
		}, value -> mergedCount.incrementAndGet());

		assertEquals(2, scheduler.drainToMain());
		assertEquals(2, mergedCount.get(), "barrier must wait for all work before merging");
		pool.shutdown();
	}

	@Test
	void regionLocalDataIsIsolated() {
		com.strefiz.multicore.compat.RegionLocal<Integer> counter = com.strefiz.multicore.compat.RegionLocal.create();
		com.strefiz.multicore.region.Region regionA = new com.strefiz.multicore.region.Region();
		com.strefiz.multicore.region.Region regionB = new com.strefiz.multicore.region.Region();

		counter.set(regionA, 10);
		counter.set(regionB, 20);

		assertEquals(Integer.valueOf(10), counter.get(regionA));
		assertEquals(Integer.valueOf(20), counter.get(regionB));
	}

	@Test
	void configSnapshotIsImmutable() {
		MultiCoreConfig config = new MultiCoreConfig().withWorkerThreads(4).withMode(com.strefiz.multicore.config.ConfigMode.AGGRESSIVE);

		MultiCoreConfig normalized = config.normalized();
		MultiCoreConfig copy = normalized.withWorkerThreads(2);

		assertEquals(4, normalized.workerThreads());
		assertEquals(2, copy.workerThreads());
		assertEquals(4, config.workerThreads(), "original must not change");
	}
}
