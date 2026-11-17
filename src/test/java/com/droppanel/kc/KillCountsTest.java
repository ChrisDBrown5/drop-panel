package com.droppanel.kc;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.junit.Test;

public class KillCountsTest
{
	private final Map<String, String> profile = new HashMap<>();
	private final BiFunction<String, String, String> get = (group, key) -> profile.get(group + "." + key);
	private final BiConsumer<String, String> set = (key, value) -> profile.put("drop-panel." + key, value);
	private final KillCounts kc = new KillCounts(get, set);

	@Test
	public void prefersRuneLiteBossKc()
	{
		profile.put("killcount.zulrah", "512");
		kc.recordKill("Zulrah");
		assertEquals(512, kc.kills("Zulrah"));
	}

	@Test
	public void fallsBackToOwnCount()
	{
		kc.recordKill("Hill Giant");
		kc.recordKill("Hill Giant");
		assertEquals(2, kc.kills("Hill Giant"));
		assertEquals("2", profile.get("drop-panel.ownkc.hill giant"));
	}

	@Test
	public void unknownMonster_isZero()
	{
		assertEquals(0, kc.kills("Goblin"));
	}

	@Test
	public void ownCountPersists()
	{
		kc.recordKill("Goblin");
		assertEquals(1, new KillCounts(get, set).kills("Goblin"));
	}

	@Test
	public void garbageValues_countAsZero()
	{
		profile.put("killcount.zulrah", "lots");
		assertEquals(0, kc.kills("Zulrah"));
	}
}
