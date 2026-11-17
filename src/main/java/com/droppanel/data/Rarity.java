package com.droppanel.data;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses OSRS Wiki rarity strings ("Always", "1/1,024", "4/128") into probabilities.
 */
public final class Rarity
{
	private static final Pattern FRACTION = Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)/([0-9][0-9,]*(?:\\.[0-9]+)?)$");

	private Rarity()
	{
	}

	/**
	 * @return the per-roll probability, or empty if the string is not a plain "Always" or "a/b"
	 */
	public static OptionalDouble parse(String rarity)
	{
		if (rarity == null)
		{
			return OptionalDouble.empty();
		}
		String s = rarity.trim();
		if (s.equalsIgnoreCase("Always"))
		{
			return OptionalDouble.of(1.0);
		}
		Matcher m = FRACTION.matcher(s);
		if (!m.matches())
		{
			return OptionalDouble.empty();
		}
		double a = Double.parseDouble(m.group(1).replace(",", ""));
		double b = Double.parseDouble(m.group(2).replace(",", ""));
		if (a <= 0 || b <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(Math.min(1.0, a / b));
	}

	/**
	 * Chance of at least one hit per kill, given a per-roll chance and the number of rolls per kill.
	 */
	public static double perKill(double perRoll, int rolls)
	{
		int r = rolls < 1 ? 1 : rolls;
		if (r == 1)
		{
			return perRoll;
		}
		return 1 - Math.pow(1 - perRoll, r);
	}
}
