package com.droppanel.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.Test;

public class TargetStoreTest
{
	private final Map<String, String> files = new HashMap<>();
	private final CacheStore store = new CacheStore()
	{
		@Override
		public Optional<String> read(String key)
		{
			return Optional.ofNullable(files.get(key));
		}

		@Override
		public void write(String key, String value)
		{
			files.put(key, value);
		}
	};
	private final Gson gson = new Gson();

	private static DropTable table(String name)
	{
		return new DropTable(name, Arrays.asList(
			new Drop("Tanzanite fang", 12922, OptionalDouble.of(1 / 512.0), 1),
			new Drop("Mystery", -1, OptionalDouble.empty(), 2.5)), 123);
	}

	@Test
	public void roundTripTable()
	{
		TargetStore s = new TargetStore(store, gson, 50);
		s.putTable(2042, table("Zulrah"));
		TargetStore.Entry e = s.get(2042).get();
		assertFalse(e.isNoData());
		assertEquals(table("Zulrah"), e.getTable());
	}

	@Test
	public void expectedQuantity_survivesDiskReload()
	{
		Drop drop = new Drop("Multiple rolls", 1, OptionalDouble.of(0.75), 1, 1.0);
		DropTable original = new DropTable("Monster", Arrays.asList(drop), 123);
		new TargetStore(store, gson, 50).putTable(1, original);
		assertEquals(original, new TargetStore(store, gson, 50).get(1).get().getTable());
	}

	@Test
	public void legacyCache_withoutExpectedQuantity_stillLoads()
	{
		files.put("targets", "[{\"npcId\":1,\"npcName\":\"Monster\",\"drops\":[{\"itemName\":\"Coins\",\"itemId\":995,\"pKill\":0.5,\"avgQuantity\":20}]}]");
		Drop drop = new TargetStore(store, gson, 50).get(1).get().getTable().getDrops().get(0);
		assertEquals(10, drop.getExpectedQuantityPerKill(), 0);
	}

	@Test
	public void noDataEntry()
	{
		TargetStore s = new TargetStore(store, gson, 50);
		s.putNoData(5, "Guard");
		TargetStore.Entry e = s.get(5).get();
		assertTrue(e.isNoData());
		assertNull(e.getTable());
		assertEquals("Guard", e.getNpcName());
		assertFalse(s.get(6).isPresent());
	}

	@Test
	public void evictsLeastRecentlyUsed()
	{
		TargetStore s = new TargetStore(store, gson, 3);
		s.putTable(1, table("A"));
		s.putTable(2, table("B"));
		s.putTable(3, table("C"));
		s.get(1);
		s.putTable(4, table("D"));
		assertFalse(s.get(2).isPresent());
		assertTrue(s.get(1).isPresent());
		assertTrue(s.get(3).isPresent());
		assertTrue(s.get(4).isPresent());
	}

	@Test
	public void getRefreshesRecency()
	{
		TargetStore s = new TargetStore(store, gson, 2);
		s.putTable(1, table("A"));
		s.putTable(2, table("B"));
		s.get(1);
		s.putTable(3, table("C"));
		assertTrue(s.get(1).isPresent());
		assertFalse(s.get(2).isPresent());
	}

	@Test
	public void persistsAcrossInstances()
	{
		TargetStore s = new TargetStore(store, gson, 2);
		s.putTable(1, table("A"));
		s.putNoData(2, "Guard");
		s.get(1);
		// Before reload, 2 is the least recently used. That order must survive the reload.
		TargetStore reloaded = new TargetStore(store, gson, 2);
		reloaded.putTable(3, table("C"));
		assertFalse(reloaded.get(2).isPresent());
		assertEquals(table("A"), reloaded.get(1).get().getTable());

		TargetStore again = new TargetStore(store, gson, 2);
		assertTrue(again.get(3).isPresent());
		assertTrue(again.get(1).isPresent());
	}

	@Test
	public void corruptFile_startsEmpty()
	{
		files.put("targets", "{definitely not a list");
		TargetStore s = new TargetStore(store, gson, 50);
		assertFalse(s.get(1).isPresent());
	}
}
