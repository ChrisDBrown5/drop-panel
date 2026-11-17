package com.droppanel.owned;

import com.droppanel.layout.Owned;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the player owns: collection log counts (from syncing log pages) and drops the plugin has seen.
 * Persisted per account through the supplied load/save functions.
 */
public class OwnedStore
{
	static final String CL_COUNTS = "clCounts";
	static final String SYNCED_PAGES = "syncedPages";
	static final String SEEN = "seen";

	private static final Type COUNTS = new TypeToken<Map<Integer, Integer>>()
	{
	}.getType();
	private static final Type PAGES = new TypeToken<Map<String, Set<Integer>>>()
	{
	}.getType();

	private final BiConsumer<String, String> save;
	private final Gson gson;

	private final Map<Integer, Integer> clCounts;
	private final Map<String, Set<Integer>> syncedPages;
	private final Map<Integer, Integer> seen;
	private final Set<Integer> syncedItems = new HashSet<>();

	public OwnedStore(Function<String, String> load, BiConsumer<String, String> save, Gson gson)
	{
		this.save = save;
		this.gson = gson;
		this.clCounts = read(load, CL_COUNTS, COUNTS);
		this.syncedPages = read(load, SYNCED_PAGES, PAGES);
		this.seen = read(load, SEEN, COUNTS);
		syncedPages.values().forEach(syncedItems::addAll);
	}

	public synchronized void recordLogPage(String pageName, Map<Integer, Integer> itemCounts)
	{
		syncedPages.put(key(pageName), new HashSet<>(itemCounts.keySet()));
		syncedItems.addAll(itemCounts.keySet());
		clCounts.putAll(itemCounts);
		persist(CL_COUNTS, clCounts);
		persist(SYNCED_PAGES, syncedPages);
	}

	public synchronized void recordNewLogItem(int itemId)
	{
		clCounts.merge(itemId, 1, Math::max);
		persist(CL_COUNTS, clCounts);
	}

	public synchronized void recordLoot(int itemId, int times)
	{
		seen.merge(itemId, times, Integer::sum);
		persist(SEEN, seen);
		if (syncedItems.contains(itemId))
		{
			clCounts.merge(itemId, times, Integer::sum);
			persist(CL_COUNTS, clCounts);
		}
	}

	public synchronized Owned owned(int itemId, boolean collectionLogItem)
	{
		if (collectionLogItem)
		{
			return new Owned(syncedItems.contains(itemId), clCounts.getOrDefault(itemId, 0));
		}
		return new Owned(true, seen.getOrDefault(itemId, 0));
	}

	public synchronized Optional<Set<Integer>> syncedPageItems(String pageName)
	{
		Set<Integer> s = syncedPages.get(key(pageName));
		return s == null ? Optional.empty() : Optional.of(new HashSet<>(s));
	}

	private static String key(String pageName)
	{
		return pageName.toLowerCase(Locale.ROOT);
	}

	private void persist(String key, Object value)
	{
		save.accept(key, gson.toJson(value));
	}

	private Map<?, ?> readRaw(Function<String, String> load, String key, Type type)
	{
		String raw = load.apply(key);
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(raw, type);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private <K, V> Map<K, V> read(Function<String, String> load, String key, Type type)
	{
		Map<?, ?> m = readRaw(load, key, type);
		return m == null ? new HashMap<>() : new HashMap<>((Map<K, V>) m);
	}
}
