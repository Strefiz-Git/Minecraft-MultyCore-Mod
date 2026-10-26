package com.strefiz.multicore.region;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Per-region queue of cross-region messages. Messages are enqueued by any thread and drained by the
 * target region's owner thread at the end of its tick.
 *
 * <p>This is Folia's {@link RegionizedTaskQueue} analogue: instead of accessing another region's
 * state directly, you enqueue a message and the owning region applies it on its own thread.
 *
 * <p>Uses a {@link ConcurrentLinkedQueue} so enqueuing from worker threads is safe.
 */
public final class RegionMessageQueue {

	private final Queue<RegionMessage<?>> queue = new ConcurrentLinkedQueue<>();

	private RegionMessageQueue() {
	}

	/** Creates a new empty message queue. */
	public static RegionMessageQueue create() {
		return new RegionMessageQueue();
	}

	/** @return whether there are messages waiting to be delivered. */
	public boolean hasPending() {
		return !queue.isEmpty();
	}

	/** @return the number of messages queued. */
	public int pendingCount() {
		return queue.size();
	}

	/**
	 * Enqueues a message to be delivered on the target region's thread.
	 *
	 * <p>Safe to call from any thread.
	 *
	 * @param message the message to enqueue
	 */
	public void enqueue(RegionMessage<?> message) {
		queue.add(message);
	}

	/**
	 * Drains and delivers every queued message on the calling thread.
	 *
	 * <p>Must be called on the region's owner thread.
	 *
	 * @return the number of messages delivered
	 */
	public int drain() {
		int delivered = 0;
		RegionMessage<?> message;
		while ((message = queue.poll()) != null) {
			try {
				message.deliver();
				delivered++;
			} catch (RuntimeException e) {
				// Log via the mixin layer; keep draining so one bad message doesn't block others.
				System.err.println("[MultiCore] Region message delivery failed: " + e);
			}
		}
		return delivered;
	}
}
