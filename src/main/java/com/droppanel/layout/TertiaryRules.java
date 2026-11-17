package com.droppanel.layout;

import java.util.Locale;
import java.util.Set;

/**
 * Tertiary drops: rolled separately from the main table (pets, clues, keys, totems...).
 * The wiki's drop data does not mark these, so they are recognised by name and by the pet list.
 */
public final class TertiaryRules
{
	private static final String[] PREFIXES = {
		"clue scroll (", "jar of ", "ensouled ", "dark totem ", "champion scroll", "scroll box (",
	};
	private static final Set<String> NAMES = Set.of(
		"brimstone key", "larran's key", "mossy key", "giant key", "ancient shard", "clue bottle"
	);

	private TertiaryRules()
	{
	}

	public static boolean isTertiary(String itemName, int itemId, Set<Integer> petIds)
	{
		if (petIds.contains(itemId))
		{
			return true;
		}
		String n = itemName.toLowerCase(Locale.ROOT);
		if (NAMES.contains(n))
		{
			return true;
		}
		for (String p : PREFIXES)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}
}
