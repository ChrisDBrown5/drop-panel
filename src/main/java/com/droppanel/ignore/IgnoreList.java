package com.droppanel.ignore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * NPC names the panel should never follow or look up. Matching is by trimmed, lower-cased name.
 */
public class IgnoreList
{
	private final Consumer<String> save;
	private final int autoThreshold;
	/** normalized name -> display name */
	private final Map<String, String> names = new LinkedHashMap<>();
	/** normalized name -> times targeted with no wiki data (this session only) */
	private final Map<String, Integer> noDataTargets = new HashMap<>();

	public IgnoreList(Supplier<String> load, Consumer<String> save, int autoThreshold)
	{
		this.save = save;
		this.autoThreshold = autoThreshold;
		String raw = load.get();
		if (raw != null)
		{
			for (String line : raw.split("\n"))
			{
				addInternal(line);
			}
		}
	}

	public synchronized boolean isIgnored(String name)
	{
		String key = normalize(name);
		return key != null && names.containsKey(key);
	}

	public synchronized void add(String name)
	{
		if (addInternal(name))
		{
			persist();
		}
	}

	public synchronized void remove(String name)
	{
		String key = normalize(name);
		if (key != null && names.remove(key) != null)
		{
			noDataTargets.remove(key);
			persist();
		}
	}

	/** Display names, sorted case-insensitively. */
	public synchronized List<String> names()
	{
		List<String> out = new ArrayList<>(names.values());
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/**
	 * Counts one targeting of an NPC that has no wiki drop data.
	 *
	 * @return true if this call pushed it over the threshold and it is now ignored
	 */
	public synchronized boolean recordNoDataTarget(String name)
	{
		String key = normalize(name);
		if (key == null || names.containsKey(key))
		{
			return false;
		}
		int n = noDataTargets.merge(key, 1, Integer::sum);
		if (n >= autoThreshold)
		{
			add(name);
			return true;
		}
		return false;
	}

	private boolean addInternal(String name)
	{
		String key = normalize(name);
		if (key == null || names.containsKey(key))
		{
			return false;
		}
		names.put(key, name.trim());
		return true;
	}

	private void persist()
	{
		save.accept(String.join("\n", names.values()));
	}

	private static String normalize(String name)
	{
		if (name == null)
		{
			return null;
		}
		String t = name.trim().toLowerCase(Locale.ROOT);
		return t.isEmpty() ? null : t;
	}
}
