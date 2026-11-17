package com.droppanel.owned;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class FullSyncTest
{
	@Test
	public void capturedItemsGetQuantity_othersZero()
	{
		Map<String, Map<Integer, Integer>> out = FullSync.pageCounts(
			Map.of("zulrah", Set.of(12922, 12932, 12921)),
			Map.of(12922, 1, 12932, 3));
		assertEquals(Map.of(12922, 1, 12932, 3, 12921, 0), out.get("zulrah"));
	}

	@Test
	public void itemOnTwoPages_countedOnBoth()
	{
		Map<String, Map<Integer, Integer>> out = FullSync.pageCounts(
			Map.of("zulrah", Set.of(12921), "all pets", Set.of(12921, 13262)),
			Map.of(12921, 1));
		assertEquals(1, (int) out.get("zulrah").get(12921));
		assertEquals(1, (int) out.get("all pets").get(12921));
		assertEquals(0, (int) out.get("all pets").get(13262));
	}

	@Test
	public void emptyIndex_recordsNothing()
	{
		assertTrue(FullSync.pageCounts(Collections.emptyMap(), Map.of(12922, 1)).isEmpty());
	}
}
