package com.droppanel.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Turns OSRS Wiki Bucket API responses into plugin data.
 */
public final class WikiParser
{
	private WikiParser()
	{
	}

	public static Optional<MonsterRef> monster(String json)
	{
		JsonArray rows = rows(json);
		if (rows.size() == 0)
		{
			return Optional.empty();
		}
		JsonObject r = rows.get(0).getAsJsonObject();
		return Optional.of(new MonsterRef(str(r, "page_name"), str(r, "page_name_sub")));
	}

	/**
	 * Drops for one page variant, merged by item name in first-seen order. Item ids are left as -1.
	 */
	public static List<Drop> drops(String json, String pageNameSub)
	{
		JsonArray rows = rows(json);
		String chosen = pickVariant(rows, pageNameSub);
		if (chosen == null)
		{
			return new ArrayList<>();
		}

		Map<String, Acc> merged = new LinkedHashMap<>();
		for (JsonElement e : rows)
		{
			JsonObject r = e.getAsJsonObject();
			if (!chosen.equals(str(r, "page_name_sub")))
			{
				continue;
			}
			JsonObject d;
			try
			{
				d = new JsonParser().parse(str(r, "drop_json")).getAsJsonObject();
			}
			catch (RuntimeException ex)
			{
				continue;
			}
			String name = str(r, "item_name");
			if (name.isEmpty())
			{
				name = str(d, "Dropped item");
			}
			if (name.isEmpty())
			{
				continue;
			}

			OptionalDouble perRoll = Rarity.parse(str(d, "Rarity"));
			OptionalDouble pKill = perRoll.isPresent()
				? OptionalDouble.of(Rarity.perKill(perRoll.getAsDouble(), intOr(d, "Rolls", 1)))
				: OptionalDouble.empty();
			double low = doubleOr(d, "Quantity Low", Double.NaN);
			double high = doubleOr(d, "Quantity High", Double.NaN);
			double avgQ = Double.isNaN(low) || Double.isNaN(high) ? 1 : (low + high) / 2;

			merged.computeIfAbsent(name, n -> new Acc()).add(pKill, avgQ);
		}

		List<Drop> out = new ArrayList<>();
		merged.forEach((name, acc) -> out.add(acc.toDrop(name)));
		return out;
	}

	/**
	 * Lower-cased item page name to item id, preferring the default version of versioned items.
	 */
	public static Map<String, Integer> itemIds(String json)
	{
		Map<String, Integer> ids = new HashMap<>();
		Map<String, Boolean> fromDefault = new HashMap<>();
		for (JsonElement e : rows(json))
		{
			JsonObject r = e.getAsJsonObject();
			String key = str(r, "page_name").toLowerCase(Locale.ROOT);
			JsonElement idEl = r.get("item_id");
			if (key.isEmpty() || idEl == null)
			{
				continue;
			}
			Integer id = firstInt(idEl);
			if (id == null)
			{
				continue;
			}
			boolean isDefault = r.has("default_version");
			if (!ids.containsKey(key) || (isDefault && !fromDefault.get(key)))
			{
				ids.put(key, id);
				fromDefault.put(key, isDefault);
			}
		}
		return ids;
	}

	public static List<Drop> withIds(List<Drop> drops, Map<String, Integer> ids)
	{
		List<Drop> out = new ArrayList<>(drops.size());
		for (Drop d : drops)
		{
			Integer id = ids.get(d.getItemName().toLowerCase(Locale.ROOT));
			out.add(new Drop(d.getItemName(), id == null ? -1 : id, d.getPKill(), d.getAvgQuantity()));
		}
		return out;
	}

	private static String pickVariant(JsonArray rows, String wanted)
	{
		List<String> subs = new ArrayList<>();
		for (JsonElement e : rows)
		{
			String s = str(e.getAsJsonObject(), "page_name_sub");
			if (!subs.contains(s))
			{
				subs.add(s);
			}
		}
		if (subs.isEmpty())
		{
			return null;
		}
		if (wanted != null && subs.contains(wanted))
		{
			return wanted;
		}
		if (wanted != null)
		{
			int hash = wanted.indexOf('#');
			String base = hash >= 0 ? wanted.substring(0, hash) : wanted;
			if (subs.contains(base))
			{
				return base;
			}
		}
		for (String s : subs)
		{
			if (s.endsWith("#Standard"))
			{
				return s;
			}
		}
		return subs.get(0);
	}

	private static JsonArray rows(String json)
	{
		try
		{
			JsonElement root = new JsonParser().parse(json);
			if (root.isJsonObject() && root.getAsJsonObject().has("bucket") && root.getAsJsonObject().get("bucket").isJsonArray())
			{
				return root.getAsJsonObject().getAsJsonArray("bucket");
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through to empty
		}
		return new JsonArray();
	}

	private static String str(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull())
		{
			return "";
		}
		if (e.isJsonArray())
		{
			JsonArray a = e.getAsJsonArray();
			return a.size() == 0 ? "" : a.get(0).getAsString();
		}
		return e.getAsString();
	}

	private static int intOr(JsonObject o, String key, int def)
	{
		double d = doubleOr(o, key, Double.NaN);
		return Double.isNaN(d) ? def : (int) d;
	}

	private static double doubleOr(JsonObject o, String key, double def)
	{
		try
		{
			JsonElement e = o.get(key);
			return e == null || e.isJsonNull() ? def : e.getAsDouble();
		}
		catch (RuntimeException ex)
		{
			return def;
		}
	}

	private static Integer firstInt(JsonElement e)
	{
		try
		{
			if (e.isJsonArray())
			{
				return e.getAsJsonArray().size() == 0 ? null : Integer.parseInt(e.getAsJsonArray().get(0).getAsString().trim());
			}
			return Integer.parseInt(e.getAsString().trim());
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static final class Acc
	{
		double pSum;
		boolean unknown;
		double weightedQ;
		double weight;
		double plainQ;
		int count;

		void add(OptionalDouble p, double q)
		{
			count++;
			plainQ += q;
			if (p.isPresent())
			{
				pSum += p.getAsDouble();
				weightedQ += p.getAsDouble() * q;
				weight += p.getAsDouble();
			}
			else
			{
				unknown = true;
			}
		}

		Drop toDrop(String name)
		{
			OptionalDouble p = unknown ? OptionalDouble.empty() : OptionalDouble.of(Math.min(1, pSum));
			double q = !unknown && weight > 0 ? weightedQ / weight : plainQ / count;
			return new Drop(name, -1, p, q);
		}
	}
}
