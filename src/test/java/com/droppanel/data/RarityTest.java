package com.droppanel.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class RarityTest
{
	@Test
	public void parse_always()
	{
		assertEquals(1.0, Rarity.parse("Always").getAsDouble(), 0);
	}

	@Test
	public void parse_fractionWithCommas()
	{
		assertEquals(1 / 1024.0, Rarity.parse("1/1,024").getAsDouble(), 1e-12);
	}

	@Test
	public void parse_numeratorAndDecimal()
	{
		assertEquals(4 / 128.0, Rarity.parse("4/128").getAsDouble(), 1e-12);
		assertEquals(1 / 19796.13, Rarity.parse("1/19,796.13").getAsDouble(), 1e-12);
	}

	@Test
	public void parse_unparseable_isEmpty()
	{
		for (String s : new String[]{"Varies", "Once", "", null, "Unknown", "1/0", "~1/50", "2 × 1/128"})
		{
			assertFalse(s, Rarity.parse(s).isPresent());
		}
	}

	@Test
	public void perKill_twoRolls()
	{
		assertEquals(1 - Math.pow(1023 / 1024.0, 2), Rarity.perKill(1 / 1024.0, 2), 1e-12);
	}

	@Test
	public void perKill_oneRoll_isIdentity()
	{
		assertEquals(0.25, Rarity.perKill(0.25, 1), 1e-12);
	}

	@Test
	public void perKill_zeroOrNegativeRolls_treatedAsOne()
	{
		assertEquals(0.25, Rarity.perKill(0.25, 0), 1e-12);
	}
}
