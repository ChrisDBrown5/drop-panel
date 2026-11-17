package com.droppanel.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.LongSupplier;

/**
 * Drop tables cached per NPC id, considered stale after 7 days.
 */
public class DropTableCache
{
	static final long MAX_AGE_MILLIS = 7L * 24 * 3600 * 1000;

	private final CacheStore store;
	private final Gson gson;
	private final LongSupplier clock;

	public DropTableCache(CacheStore store, Gson gson, LongSupplier clock)
	{
		this.store = store;
		this.gson = gson;
		this.clock = clock;
	}

	public Optional<DropTable> get(int npcId)
	{
		Optional<String> raw = store.read(Integer.toString(npcId));
		if (!raw.isPresent())
		{
			return Optional.empty();
		}
		try
		{
			TableDto dto = gson.fromJson(raw.get(), TableDto.class);
			if (dto == null || dto.drops == null || dto.monsterName == null)
			{
				return Optional.empty();
			}
			List<Drop> drops = new ArrayList<>();
			for (DropDto d : dto.drops)
			{
				drops.add(new Drop(d.itemName, d.itemId, d.pKill == null ? OptionalDouble.empty() : OptionalDouble.of(d.pKill), d.avgQuantity));
			}
			return Optional.of(new DropTable(dto.monsterName, drops, dto.fetchedAtMillis));
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return Optional.empty();
		}
	}

	public boolean isStale(DropTable t)
	{
		return clock.getAsLong() - t.getFetchedAtMillis() > MAX_AGE_MILLIS;
	}

	public void put(int npcId, DropTable t)
	{
		TableDto dto = new TableDto();
		dto.monsterName = t.getMonsterName();
		dto.fetchedAtMillis = t.getFetchedAtMillis();
		dto.drops = new ArrayList<>();
		for (Drop d : t.getDrops())
		{
			DropDto x = new DropDto();
			x.itemName = d.getItemName();
			x.itemId = d.getItemId();
			x.pKill = d.getPKill().isPresent() ? d.getPKill().getAsDouble() : null;
			x.avgQuantity = d.getAvgQuantity();
			dto.drops.add(x);
		}
		store.write(Integer.toString(npcId), gson.toJson(dto));
	}

	private static final class TableDto
	{
		String monsterName;
		long fetchedAtMillis;
		List<DropDto> drops;
	}

	private static final class DropDto
	{
		String itemName;
		int itemId;
		Double pKill;
		double avgQuantity;
	}
}
