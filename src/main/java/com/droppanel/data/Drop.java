package com.droppanel.data;

import java.util.OptionalDouble;
import lombok.Value;
import lombok.AllArgsConstructor;

/**
 * One item on a monster's drop table, with all wiki lines for that item merged.
 */
@Value
@AllArgsConstructor
public class Drop
{
	String itemName;
	/** Game item id, or -1 if the wiki lookup did not find one. */
	int itemId;
	/** Chance per kill of receiving this item at least once; empty if the wiki rarity could not be parsed. */
	OptionalDouble pKill;
	double avgQuantity;
	/** Expected total quantity per kill, including all rolls; used for value classification. */
	double expectedQuantityPerKill;

	public Drop(String itemName, int itemId, OptionalDouble pKill, double avgQuantity)
	{
		this(itemName, itemId, pKill, avgQuantity, pKill.orElse(0) * avgQuantity);
	}
}
