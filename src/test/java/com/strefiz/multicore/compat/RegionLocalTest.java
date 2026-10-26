package com.strefiz.multicore.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.strefiz.multicore.region.Region;

/**
 * Unit tests for {@link RegionLocal}.
 */
class RegionLocalTest {

	@Test
	void getSetRoundTrip() {
		RegionLocal<Integer> ticks = RegionLocal.create();
		Region region = new Region();

		assertNull(ticks.get(region));

		ticks.set(region, 42);
		assertEquals(Integer.valueOf(42), ticks.get(region));

		ticks.set(region, 7);
		assertEquals(Integer.valueOf(7), ticks.get(region));
	}

	@Test
	void regionsAreIndependent() {
		RegionLocal<String> label = RegionLocal.create();
		Region regionA = new Region();
		Region regionB = new Region();

		label.set(regionA, "a");
		label.set(regionB, "b");

		assertEquals("a", label.get(regionA));
		assertEquals("b", label.get(regionB));
	}
}
