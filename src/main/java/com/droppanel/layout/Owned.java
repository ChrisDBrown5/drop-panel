package com.droppanel.layout;

import lombok.Value;

/**
 * How many of an item the player has.
 */
@Value
public class Owned
{
	/** False when this is a collection log item whose page has not been synced yet. */
	boolean known;
	int count;
}
