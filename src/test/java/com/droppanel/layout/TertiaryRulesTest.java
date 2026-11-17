package com.droppanel.layout;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Set;
import org.junit.Test;

public class TertiaryRulesTest
{
	@Test
	public void tertiary_byNameAndPet()
	{
		assertTrue(TertiaryRules.isTertiary("Clue scroll (elite)", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Jar of swamp", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Brimstone key", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Ensouled abyssal head", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Dark totem base", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Scroll box (hard)", 1, Set.of()));
		assertTrue(TertiaryRules.isTertiary("Pet snakeling", 12921, Set.of(12921)));
		assertFalse(TertiaryRules.isTertiary("Tanzanite fang", 12922, Set.of(12921)));
		assertFalse(TertiaryRules.isTertiary("Rune kiteshield", 1185, Set.of()));
	}
}
