package com.strefiz.multicore.compat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds callbacks that must run on the server main thread.
 *
 * <p>In SAFE mode, legacy mods (those that did not declare {@code threadSafe}) keep running on the
 * main thread. If any code running off the main thread needs to touch legacy mod state or world
 * state, it enqueues the work here and {@link #drainToMain()} applies it on the main thread before
 * the tick ends.
 *
 * <p>Uses a {@link ConcurrentLinkedQueue} so enqueuing from worker threads is safe.
 *
 * <p>This is the safety net that keeps unmodified mods working while the rest of the server uses
 * worker threads.
 */
public final class LegacyHandoff {

	private static final Logger LOGGER = LoggerFactory.getLogger("multicore/legacy");

	private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

	private LegacyHandoff() {
	}

	/**
	 * Creates a new empty handoff queue.
	 *
	 * @return a new {@link LegacyHandoff}
	 */
	public static LegacyHandoff create() {
		return new LegacyHandoff();
	}

	/** @return whether there are callbacks waiting for the main thread. */
	public boolean hasPending() {
		return !pending.isEmpty();
	}

	/** @return the number of callbacks queued for the next drain. */
	public int pendingCount() {
		return pending.size();
	}

	/**
	 * Enqueues a callback to run on the calling thread's counterpart main thread.
	 *
	 * <p>Safe to call from any thread. The actual execution happens on the main thread when
	 * {@link #drainToMain()} is called.
	 *
	 * @param callback the work to run on the main thread, never {@code null}
	 */
	public void enqueue(Runnable callback) {
		pending.add(callback);
	}

	/**
	 * Applies every queued callback on the calling thread, in order.
	 *
	 * <p>Must be called on the main thread (typically at {@code END_SERVER_TICK}); a warning is
	 * logged otherwise.
	 *
	 * @return the number of callbacks applied
	 */
	public int drainToMain() {
		if (!com.strefiz.multicore.thread.MainThread.isCurrent()) {
			LOGGER.warn("LegacyHandoff.drainToMain() was called off the main thread.");
		}
		int applied = 0;
		Runnable callback;
		while ((callback = pending.poll()) != null) {
			try {
				callback.run();
				applied++;
			} catch (RuntimeException e) {
				LOGGER.error("A legacy handoff callback failed; continuing.", e);
			}
		}
		return applied;
	}
}
