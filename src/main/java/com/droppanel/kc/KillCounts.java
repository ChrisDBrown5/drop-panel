package com.droppanel.kc;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Kill counts: RuneLite's stored boss KC when it has one, otherwise kills this plugin has counted.
 */
public class KillCounts
{
	static final String RUNELITE_GROUP = "killcount";
	static final String OWN_GROUP = "drop-panel";
	static final String OWN_PREFIX = "ownkc.";

	private final BiFunction<String, String, String> getProfile;
	private final BiConsumer<String, String> setOwn;

	/**
	 * @param getProfile reads an RS-profile config value by (group, key)
	 * @param setOwn     writes an RS-profile config value in this plugin's group by key
	 */
	public KillCounts(BiFunction<String, String, String> getProfile, BiConsumer<String, String> setOwn)
	{
		this.getProfile = getProfile;
		this.setOwn = setOwn;
	}

	public synchronized int kills(String monsterName)
	{
		int runelite = parse(getProfile.apply(RUNELITE_GROUP, key(monsterName)));
		return runelite > 0 ? runelite : own(monsterName);
	}

	public synchronized void recordKill(String monsterName)
	{
		setOwn.accept(OWN_PREFIX + key(monsterName), Integer.toString(own(monsterName) + 1));
	}

	private int own(String monsterName)
	{
		return parse(getProfile.apply(OWN_GROUP, OWN_PREFIX + key(monsterName)));
	}

	private static String key(String monsterName)
	{
		return monsterName.toLowerCase(Locale.ROOT);
	}

	private static int parse(String s)
	{
		if (s == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Integer.parseInt(s.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
