package com.droppanel.stats;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Display formatting for drop rates and percentages.
 */
public final class Format
{
	private Format()
	{
	}

	/**
	 * "1/512", "1/6,554", "1/3.5", "Always", or "?" when unknown.
	 */
	public static String rate(OptionalDouble pKill)
	{
		if (!pKill.isPresent() || pKill.getAsDouble() <= 0)
		{
			return "?";
		}
		double p = pKill.getAsDouble();
		if (p >= 1)
		{
			return "Always";
		}
		double x = 1 / p;
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
		if (x < 10)
		{
			return "1/" + new DecimalFormat("0.#", symbols).format(x);
		}
		return "1/" + new DecimalFormat("#,##0", symbols).format(Math.round(x));
	}

	/**
	 * Whole-percent display: "0%", "<1%", "63%", ">99%", "100%".
	 */
	public static String percent(double fraction)
	{
		if (fraction <= 0)
		{
			return "0%";
		}
		if (fraction >= 1)
		{
			return "100%";
		}
		if (fraction < 0.005)
		{
			return "<1%";
		}
		if (fraction >= 0.995)
		{
			return ">99%";
		}
		return Math.round(fraction * 100) + "%";
	}
}
