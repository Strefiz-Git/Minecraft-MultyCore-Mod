package com.strefiz.multicore.compat;

import com.strefiz.multicore.region.Region;

/**
 * Run a task on a specific region's owner thread, delivered at the end of the region's tick.
 *
 * <p>This is Folia's {@link RegionizedTaskQueue} analogue: instead of accessing another region's
 * state directly, you schedule a task to run on that region's thread.
 *
 * <p>This class is deliberately free of Minecraft classes so it is unit-testable without the game.
 */
public final class RunOnRegion {

	private RunOnRegion() {
	}

	/**
	 * Schedule a task to run on the given region's owner thread at the end of its tick.
	 *
	 * <p>Safe to call from any thread. The task is enqueued as a cross-region message and delivered
	 * by the region's owner thread during the barrier drain.
	 *
	 * @param region the region whose thread should run the task
	 * @param task the task to run on the region thread
	 */
	public static void runOnRegion(Region region, Runnable task) {
		region.messageQueue().enqueue(new com.strefiz.multicore.region.RegionMessage<>(
				null, null, task, task));
	}
}
