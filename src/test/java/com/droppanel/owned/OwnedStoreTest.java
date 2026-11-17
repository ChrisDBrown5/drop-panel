package com.droppanel.owned;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.droppanel.layout.Owned;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.Test;

public class OwnedStoreTest
{
	private final Map<String, String> config = new HashMap<>();
	private final Function<String, String> load = config::get;
	private final BiConsumer<String, String> save = config::put;
	private final Gson gson = new Gson();
	private final OwnedStore store = new OwnedStore(load, save, gson);

	@Test
	public void unsyncedPage_isNotGreyed()
	{
		assertFalse(store.owned(12922, true).isKnown());
	}

	@Test
	public void syncedPage_reportsCounts()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0, 12932, 2));
		assertEquals(new Owned(true, 0), store.owned(12922, true));
		assertEquals(new Owned(true, 2), store.owned(12932, true));
	}

	@Test
	public void newLogItem_setsAtLeastOne()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0));
		store.recordNewLogItem(12922);
		store.recordNewLogItem(12922);
		assertEquals(1, store.owned(12922, true).getCount());
	}

	@Test
	public void loot_incrementsSeenAndSyncedLog()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 1));
		store.recordLoot(12922, 1);
		store.recordLoot(11286, 1);
		assertEquals(2, store.owned(12922, true).getCount());
		assertEquals(new Owned(true, 1), store.owned(11286, false));
	}

	@Test
	public void loot_doesNotMakeUnsyncedLogItemKnown()
	{
		store.recordLoot(12922, 1);
		assertFalse(store.owned(12922, true).isKnown());
	}

	@Test
	public void persistsAcrossInstances()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 3));
		assertEquals(3, new OwnedStore(load, save, gson).owned(12922, true).getCount());
	}

	@Test
	public void pageItemsRemembered()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0));
		assertEquals(Set.of(12922), store.syncedPageItems("zulrah").get());
	}

	@Test
	public void corruptConfig_startsEmpty()
	{
		config.put("clCounts", "{nope");
		assertFalse(new OwnedStore(load, save, gson).owned(12922, true).isKnown());
	}

	@Test
	public void chatThenLoot_firstCopyCountsOnce()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0));
		store.recordNewLogItem(12922);
		store.recordLoot(12922, 1);
		assertEquals(1, store.owned(12922, true).getCount());
	}

	@Test
	public void lootThenChat_firstCopyCountsOnce_andLaterLootStillCounts()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0));
		store.recordLoot(12922, 1);
		store.recordNewLogItem(12922);
		assertEquals(1, store.owned(12922, true).getCount());
		store.recordLoot(12922, 1);
		assertEquals(2, store.owned(12922, true).getCount());
	}

	@Test
	public void chatThenLoot_thenSecondDropCounts()
	{
		store.recordLogPage("Zulrah", Map.of(12922, 0));
		store.recordNewLogItem(12922);
		store.recordLoot(12922, 1);
		store.recordLoot(12922, 1);
		assertEquals(2, store.owned(12922, true).getCount());
	}
}
