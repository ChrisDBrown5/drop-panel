package com.droppanel.layout;

import java.util.Set;
import java.util.function.IntUnaryOperator;
import lombok.Value;

/**
 * Everything the classifier needs beyond the drop table itself.
 */
@Value
public class ClassifyContext
{
	/** Items on this monster's collection log page; empty when no page matched. */
	Set<Integer> monsterLogItems;
	boolean monsterPageKnown;
	Set<Integer> allLogItems;
	Set<Integer> petIds;
	/** GE price per item id, 0 if unknown. */
	IntUnaryOperator price;
}
