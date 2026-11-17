package com.droppanel.ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class IgnoreListTest
{
	private String saved;
	private final IgnoreList list = new IgnoreList(() -> saved, s -> saved = s, 5);

	@Test
	public void ignore_isCaseInsensitive()
	{
		list.add("Goblin");
		assertTrue(list.isIgnored("goblin"));
		assertTrue(list.isIgnored(" GOBLIN "));
		assertFalse(list.isIgnored("Hill Giant"));
		assertFalse(list.isIgnored(null));
	}

	@Test
	public void addRemove()
	{
		list.add("Goblin");
		list.add("goblin");
		assertEquals(Collections.singletonList("Goblin"), list.names());
		list.remove("GOBLIN");
		assertFalse(list.isIgnored("Goblin"));
		assertTrue(list.names().isEmpty());
	}

	@Test
	public void blankNamesRejected()
	{
		list.add("   ");
		list.add(null);
		assertTrue(list.names().isEmpty());
	}

	@Test
	public void autoIgnoreOnFifth()
	{
		for (int i = 1; i <= 4; i++)
		{
			assertFalse("call " + i, list.recordNoDataTarget("Guard"));
		}
		assertFalse(list.isIgnored("Guard"));
		assertTrue(list.recordNoDataTarget("guard"));
		assertTrue(list.isIgnored("Guard"));
		assertFalse(list.recordNoDataTarget("Guard"));
	}

	@Test
	public void persists()
	{
		list.add("Goblin");
		list.add("Hill Giant");
		IgnoreList reloaded = new IgnoreList(() -> saved, s -> saved = s, 5);
		assertTrue(reloaded.isIgnored("goblin"));
		assertTrue(reloaded.isIgnored("hill giant"));
	}

	@Test
	public void namesSorted()
	{
		list.add("zombie");
		list.add("Abyssal demon");
		list.add("goblin");
		assertEquals(Arrays.asList("Abyssal demon", "goblin", "zombie"), list.names());
	}
}
