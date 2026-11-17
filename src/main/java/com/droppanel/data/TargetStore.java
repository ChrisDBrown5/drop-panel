package com.droppanel.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.Value;

/**
 * Drop tables (and "no wiki data" results) for the most recently used NPC ids, persisted as one entry.
 * Entries never expire on their own; only eviction past capacity or an explicit put replaces them.
 */
public class TargetStore
{
	static final String KEY = "targets";
	private static final Type LIST = new TypeToken<List<EntryDto>>()
	{
	}.getType();

	@Value
	public static class Entry
	{
		/** Null when the wiki had no data for this NPC. */
		DropTable table;
		String npcName;
		boolean noData;
	}

	private final CacheStore store;
	private final Gson gson;
	private final LinkedHashMap<Integer, Entry> entries;

	public TargetStore(CacheStore store, Gson gson, int capacity)
	{
		this.store = store;
		this.gson = gson;
		this.entries = new LinkedHashMap<Integer, Entry>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, TargetStore.Entry> eldest)
			{
				return size() > capacity;
			}
		};
		load();
	}

	public synchronized Optional<Entry> get(int npcId)
	{
		Entry e = entries.get(npcId);
		if (e != null)
		{
			persist(); // recency changed
		}
		return Optional.ofNullable(e);
	}

	public synchronized void putTable(int npcId, DropTable table)
	{
		entries.put(npcId, new Entry(table, table.getMonsterName(), false));
		persist();
	}

	public synchronized void putNoData(int npcId, String npcName)
	{
		entries.put(npcId, new Entry(null, npcName, true));
		persist();
	}

	private void load()
	{
		Optional<String> raw = store.read(KEY);
		if (!raw.isPresent())
		{
			return;
		}
		try
		{
			List<EntryDto> list = gson.fromJson(raw.get(), LIST);
			if (list == null)
			{
				return;
			}
			for (EntryDto d : list)
			{
				if (d == null)
				{
					continue;
				}
				entries.put(d.npcId, d.noData ? new Entry(null, d.npcName, true) : new Entry(toTable(d), d.npcName, false));
			}
		}
		catch (JsonParseException | IllegalStateException | NullPointerException e)
		{
			entries.clear();
		}
	}

	private void persist()
	{
		List<EntryDto> list = new ArrayList<>();
		for (Map.Entry<Integer, Entry> e : entries.entrySet())
		{
			EntryDto d = new EntryDto();
			d.npcId = e.getKey();
			d.npcName = e.getValue().getNpcName();
			d.noData = e.getValue().isNoData();
			DropTable t = e.getValue().getTable();
			if (t != null)
			{
				d.fetchedAtMillis = t.getFetchedAtMillis();
				d.drops = new ArrayList<>();
				for (Drop drop : t.getDrops())
				{
					DropDto x = new DropDto();
					x.itemName = drop.getItemName();
					x.itemId = drop.getItemId();
					x.pKill = drop.getPKill().isPresent() ? drop.getPKill().getAsDouble() : null;
					x.avgQuantity = drop.getAvgQuantity();
					x.expectedQuantityPerKill = drop.getExpectedQuantityPerKill();
					d.drops.add(x);
				}
			}
			list.add(d);
		}
		store.write(KEY, gson.toJson(list));
	}

	private static DropTable toTable(EntryDto d)
	{
		List<Drop> drops = new ArrayList<>();
		for (DropDto x : d.drops)
		{
			OptionalDouble p = x.pKill == null ? OptionalDouble.empty() : OptionalDouble.of(x.pKill);
			drops.add(new Drop(x.itemName, x.itemId, p, x.avgQuantity,
				x.expectedQuantityPerKill == null ? p.orElse(0) * x.avgQuantity : x.expectedQuantityPerKill));
		}
		return new DropTable(d.npcName, drops, d.fetchedAtMillis);
	}

	private static final class EntryDto
	{
		int npcId;
		String npcName;
		boolean noData;
		long fetchedAtMillis;
		List<DropDto> drops;
	}

	private static final class DropDto
	{
		String itemName;
		int itemId;
		Double pKill;
		double avgQuantity;
		Double expectedQuantityPerKill;
	}
}
