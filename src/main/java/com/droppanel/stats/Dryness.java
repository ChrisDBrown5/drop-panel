package com.droppanel.stats;

/**
 * Binomial "how many players would have it by now" maths.
 */
public final class Dryness
{
	private Dryness()
	{
	}

	/**
	 * P(X >= k) for X ~ Binomial(kills, pKill). k below 1 is treated as 1.
	 */
	public static double atLeast(int kills, double pKill, int k)
	{
		int need = Math.max(k, 1);
		if (kills <= 0 || need > kills || pKill <= 0)
		{
			return 0;
		}
		if (pKill >= 1)
		{
			return 1;
		}
		// Sum P(X = i) for i < need, building each term from the previous one.
		double term = Math.exp(kills * Math.log1p(-pKill));
		double below = 0;
		double ratio = pKill / (1 - pKill);
		for (int i = 0; i < need; i++)
		{
			below += term;
			term = term * (kills - i) / (i + 1) * ratio;
		}
		return Math.max(0, Math.min(1, 1 - below));
	}
}
