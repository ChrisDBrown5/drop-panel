package com.droppanel.owned;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns the items captured during a full collection log sync into per-page counts.
 */
public final class FullSync
{
	private FullSync()
	{
	}

	/**
	 * @param pages    page name to the item ids on that page
	 * @param captured obtained item id to quantity, as drawn by the log's search
	 * @return page name to (item id to quantity, 0 when not obtained)
	 */
	public static Map<String, Map<Integer, Integer>> pageCounts(Map<String, Set<Integer>> pages, Map<Integer, Integer> captured)
	{
		Map<String, Map<Integer, Integer>> out = new HashMap<>();
		pages.forEach((page, items) ->
		{
			Map<Integer, Integer> counts = new HashMap<>();
			for (int id : items)
			{
				counts.put(id, captured.getOrDefault(id, 0));
			}
			out.put(page, counts);
		});
		return out;
	}
}
