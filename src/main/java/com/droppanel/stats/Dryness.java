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
		// Sum P(X = i) for i < need in log space, so large kills * pKill cannot underflow.
		double logRatio = Math.log(pKill) - Math.log1p(-pKill);
		double logTerm = kills * Math.log1p(-pKill);
		double maxLog = Double.NEGATIVE_INFINITY;
		double[] logs = new double[need];
		for (int i = 0; i < need; i++)
		{
			logs[i] = logTerm;
			maxLog = Math.max(maxLog, logTerm);
			logTerm += Math.log(kills - i) - Math.log(i + 1) + logRatio;
		}
		double scaled = 0;
		for (double l : logs)
		{
			scaled += Math.exp(l - maxLog);
		}
		double below = Math.exp(maxLog) * scaled;
		return Math.max(0, Math.min(1, 1 - below));
	}
}
