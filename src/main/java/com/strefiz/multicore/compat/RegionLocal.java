package com.strefiz.multicore.compat;

import com.strefiz.multicore.region.Region;

/**
 * Region-local storage for mod authors. Data stored here is only ever accessed by the owning region
 * thread while that region is ticking, so no synchronization is needed.
 *
 * <p>This is Folia's {@link RegionizedData} analogue: instead of sharing mutable state across region
 * threads, each region keeps its own isolated map.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 *   RegionLocal&lt;Integer&gt; tickCount = RegionLocal.create();
 *   // Inside region tick:
 *   tickCount.set(tickCount.getOrDefault(0) + 1);
 * </pre>
 *
 * <p>This class is deliberately free of Minecraft classes so it is unit-testable without the game.
 */
public final class RegionLocal<T> {

	private final Object key = new Object();

	private RegionLocal() {
	}

	/** Creates a new region-local storage slot. */
	public static <T> RegionLocal<T> create() {
		return new RegionLocal<>();
	}

	/**
	 * Gets the value stored for the current region.
	 *
	 * @param region the region whose local storage to read
	 * @return the stored value, or {@code null} if none
	 */
	public T get(Region region) {
		return (T) region.getRegionLocal(key);
	}

	/**
	 * Sets the value stored for the current region.
	 *
	 * @param region the region whose local storage to write
	 * @param value the value to store
	 */
	public void set(Region region, T value) {
		region.putRegionLocal(key, value);
	}
}
