package com.droppanel.stats;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DrynessTest
{
	@Test
	public void atLeastOne_zulrah512()
	{
		assertEquals(0.63248, Dryness.atLeast(512, 1 / 512.0, 1), 1e-4);
	}

	@Test
	public void atLeastTwo_zulrah512()
	{
		assertEquals(0.26424, Dryness.atLeast(512, 1 / 512.0, 2), 1e-4);
	}

	@Test
	public void atLeast_zeroKills_isZero()
	{
		assertEquals(0.0, Dryness.atLeast(0, 0.5, 1), 0);
	}

	@Test
	public void atLeast_certainDrop()
	{
		assertEquals(1.0, Dryness.atLeast(3, 1.0, 3), 0);
		assertEquals(0.0, Dryness.atLeast(2, 1.0, 3), 0);
	}

	@Test
	public void atLeast_tinyP_largeN_isStable()
	{
		assertEquals(1 - Math.exp(-50000 / 19796.13), Dryness.atLeast(50000, 1 / 19796.13, 1), 1e-4);
	}

	@Test
	public void atLeast_kLessThanOne_treatedAsOne()
	{
		assertEquals(Dryness.atLeast(10, .1, 1), Dryness.atLeast(10, .1, 0), 0);
	}

	@Test
	public void atLeast_largeKillsTimesP_doesNotUnderflow()
	{
		assertEquals(0.506887, Dryness.atLeast(3000, 0.3, 900), 1e-4);
		assertEquals(0.002442, Dryness.atLeast(5000, 0.5, 2600), 1e-5);
	}
}
