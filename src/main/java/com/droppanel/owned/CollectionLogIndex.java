package com.droppanel.owned;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

/**
 * Which items are on which collection log page, read from the game's own enums and structs.
 * Must be built on the client thread.
 */
@Slf4j
public class CollectionLogIndex
{
	// Collection log structure in the game cache (tab structs: bosses, raids, clues, minigames, other),
	// as used by the Collection Log plugin.
	static final int[] TAB_STRUCTS = {471, 472, 473, 474, 475};
	static final int PARAM_TAB_PAGES = 683;
	static final int PARAM_PAGE_NAME = 689;
	static final int PARAM_PAGE_ITEMS = 690;
	static final String PETS_PAGE = "all pets";

	private volatile Map<String, Set<Integer>> pages = Collections.emptyMap();
	private volatile Set<Integer> all = Collections.emptySet();

	public void build(Client client)
	{
		Map<String, Set<Integer>> p = new HashMap<>();
		Set<Integer> a = new HashSet<>();
		try
		{
			for (int tabStruct : TAB_STRUCTS)
			{
				StructComposition tab = client.getStructComposition(tabStruct);
				EnumComposition pageEnum = client.getEnum(tab.getIntValue(PARAM_TAB_PAGES));
				for (int pageStruct : pageEnum.getIntVals())
				{
					StructComposition page = client.getStructComposition(pageStruct);
					String name = page.getStringValue(PARAM_PAGE_NAME);
					if (name == null || name.isEmpty())
					{
						continue;
					}
					Set<Integer> items = new HashSet<>();
					for (int id : client.getEnum(page.getIntValue(PARAM_PAGE_ITEMS)).getIntVals())
					{
						items.add(id);
					}
					p.merge(name.toLowerCase(Locale.ROOT), items, (x, y) ->
					{
						x.addAll(y);
						return x;
					});
					a.addAll(items);
				}
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Drop Panel: could not read collection log structure from the game cache", e);
			return; // Never publish a partial index as if it were the complete log.
		}
		pages = p;
		all = a;
		log.debug("collection log index: {} pages, {} items, zulrah={}", p.size(), a.size(), p.get("zulrah"));
	}

	public Optional<Set<Integer>> pageItems(String pageName)
	{
		return Optional.ofNullable(pages.get(pageName.toLowerCase(Locale.ROOT)));
	}

	/** Lower-cased page name to its item ids. */
	public Map<String, Set<Integer>> pages()
	{
		return pages;
	}

	public Set<Integer> allItems()
	{
		return all;
	}

	public Set<Integer> petIds()
	{
		return pages.getOrDefault(PETS_PAGE, Collections.emptySet());
	}
}
