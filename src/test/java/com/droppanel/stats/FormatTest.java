package com.droppanel.stats;

import static org.junit.Assert.assertEquals;
import java.util.OptionalDouble;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void rate()
	{
		assertEquals("1/512", Format.rate(OptionalDouble.of(1 - Math.pow(1023 / 1024.0, 2))));
		assertEquals("1/6,554", Format.rate(OptionalDouble.of(1 / 6554.0)));
		assertEquals("1/3.5", Format.rate(OptionalDouble.of(1 / 3.5)));
		assertEquals("1/2", Format.rate(OptionalDouble.of(0.5)));
		assertEquals("Always", Format.rate(OptionalDouble.of(1.0)));
		assertEquals("?", Format.rate(OptionalDouble.empty()));
	}

	@Test
	public void percent()
	{
		assertEquals("63%", Format.percent(0.63248));
		assertEquals("<1%", Format.percent(0.004));
		assertEquals("0%", Format.percent(0.0));
		assertEquals(">99%", Format.percent(0.9996));
		assertEquals("100%", Format.percent(1.0));
	}
}
