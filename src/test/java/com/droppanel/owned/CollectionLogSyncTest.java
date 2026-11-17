package com.droppanel.owned;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CollectionLogSyncTest
{
	@Test
	public void ownLog_isSynced()
	{
		assertTrue(CollectionLogSync.isOwnLog("Collection Log", "Chris"));
		assertTrue(CollectionLogSync.isOwnLog(null, "Chris"));
		assertTrue(CollectionLogSync.isOwnLog("Collection Log - Chris", "Chris"));
	}

	@Test
	public void someoneElsesLog_isNotSynced()
	{
		assertFalse(CollectionLogSync.isOwnLog("Collection Log - Zezima", "Chris"));
		assertFalse(CollectionLogSync.isOwnLog("<col=ff981f>Collection Log - Zezima</col>", "Chris"));
	}
}
