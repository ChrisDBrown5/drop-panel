package com.droppanel.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.Test;

public class DropTableCacheTest
{
	private static final long WEEK = 7L * 24 * 3600 * 1000;

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
	private long clock;
	private final DropTableCache cache = new DropTableCache(store, new Gson(), () -> clock);

	private static DropTable tableAt(long t)
	{
		return new DropTable("Zulrah", Arrays.asList(
			new Drop("Tanzanite fang", 12922, OptionalDouble.of(1 / 512.0), 1),
			new Drop("Mystery", -1, OptionalDouble.empty(), 2.5)), t);
	}

	@Test
	public void roundTrip()
	{
		DropTable table = tableAt(123);
		cache.put(2042, table);
		assertEquals(table, cache.get(2042).get());
	}

	@Test
	public void missingTable_reportsEmpty()
	{
		assertFalse(cache.get(1).isPresent());
	}

	@Test
	public void staleAfterSevenDays()
	{
		cache.put(1, tableAt(0));
		clock = WEEK + 1;
		assertTrue(cache.isStale(cache.get(1).get()));
	}

	@Test
	public void freshWithinSevenDays()
	{
		cache.put(1, tableAt(0));
		clock = WEEK - 1;
		assertFalse(cache.isStale(cache.get(1).get()));
	}

	@Test
	public void corruptJson_treatedAsMissing()
	{
		store.write("2042", "{not json");
		assertFalse(cache.get(2042).isPresent());
	}
}
