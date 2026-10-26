package com.strefiz.multicore.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.strefiz.multicore.region.Region;

/**
 * Unit tests for {@link RunOnRegion}.
 */
class RunOnRegionTest {

	@Test
	void runOnRegionEnqueuesTask() {
		Region region = new Region();
		List<String> delivered = new ArrayList<>();

		RunOnRegion.runOnRegion(region, () -> delivered.add("ran"));

		assertTrue(region.messageQueue().hasPending());
		assertEquals(1, region.messageQueue().pendingCount());

		int deliveredCount = region.messageQueue().drain();
		assertEquals(1, deliveredCount);
		assertEquals(List.of("ran"), delivered);
		assertFalse(region.messageQueue().hasPending());
	}
}
