package com.droppanel.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.droppanel.data.Drop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.Test;

public class ClassifierTest
{
	private final Map<Integer, Integer> prices = new HashMap<>();

	private static Drop d(String name, int id, double p, double qty)
	{
		return new Drop(name, id, OptionalDouble.of(p), qty);
	}

	private ClassifyContext ctx(Set<Integer> monsterLog, boolean pageKnown, Set<Integer> allLog, Set<Integer> pets)
	{
		return new ClassifyContext(monsterLog, pageKnown, allLog, pets, id -> prices.getOrDefault(id, 0));
	}

	private static Section sectionOf(Map<Section, List<Classified>> m, String name)
	{
		for (Map.Entry<Section, List<Classified>> e : m.entrySet())
		{
			for (Classified c : e.getValue())
			{
				if (c.getDrop().getItemName().equals(name))
				{
					return e.getKey();
				}
			}
		}
		throw new AssertionError("not found: " + name);
	}

	/** 15 filler low-value drops so a table is not "simple" by size. */
	private static List<Drop> filler()
	{
		List<Drop> l = new ArrayList<>();
		for (int i = 0; i < 15; i++)
		{
			l.add(d("Junk " + i, 1000 + i, 1 / 20.0, 1));
		}
		return l;
	}

	@Test
	public void uniques_fromMonsterLogPage()
	{
		List<Drop> drops = new ArrayList<>(Arrays.asList(d("Tanzanite fang", 12922, 1 / 512.0, 1), d("Pet snakeling", 12921, 1 / 4000.0, 1)));
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(12922, 12921), true, Set.of(12922, 12921), Set.of(12921)));
		assertEquals(Section.UNIQUE, sectionOf(m, "Tanzanite fang"));
		assertEquals(Section.TERTIARY, sectionOf(m, "Pet snakeling"));
		assertTrue(m.get(Section.TERTIARY).get(0).isCollectionLogItem());
	}

	@Test
	public void uniques_fallbackToAnyLogPage_whenPageUnknown()
	{
		List<Drop> drops = new ArrayList<>(Arrays.asList(d("Abyssal whip", 4151, 1 / 512.0, 1)));
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(), false, Set.of(4151), Set.of()));
		assertEquals(Section.UNIQUE, sectionOf(m, "Abyssal whip"));
	}

	@Test
	public void rare_atOrBelow1in5000()
	{
		List<Drop> drops = new ArrayList<>(Arrays.asList(d("Dragon spear", 1249, 1 / 5009.0, 1), d("Rune spear", 1247, 1 / 4999.0, 1),
			d("Abyssal whip", 4151, 1 / 512.0, 1)));
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(4151), true, Set.of(4151), Set.of()));
		assertEquals(Section.RARE, sectionOf(m, "Dragon spear"));
		assertFalse(sectionOf(m, "Rune spear") == Section.RARE);
	}

	@Test
	public void highValue_isTenPercentOfMaxNonUniqueEv()
	{
		prices.put(1, 1000);
		prices.put(2, 100);
		prices.put(3, 99);
		prices.put(4151, 1_000_000_000);
		List<Drop> drops = new ArrayList<>(Arrays.asList(d("A", 1, 1, 1), d("B", 2, 1, 1), d("C", 3, 1, 1), d("Abyssal whip", 4151, 1, 1)));
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(4151), true, Set.of(4151), Set.of()));
		assertEquals(Section.HIGH_VALUE, sectionOf(m, "A"));
		assertEquals(Section.HIGH_VALUE, sectionOf(m, "B"));
		assertEquals(Section.OTHER, sectionOf(m, "C"));
	}

	@Test
	public void highValue_usesAllRollsRatherThanChanceOfAtLeastOne()
	{
		prices.put(1, 1000);
		prices.put(2, 100);
		List<Drop> drops = filler();
		drops.add(d("Reference", 1, 1, 1));
		// Two independent 50% rolls yield one item per kill on average, but only a 75% chance of any.
		drops.add(new Drop("Multiple rolls", 2, OptionalDouble.of(0.75), 1, 1.0));
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(), true, Set.of(), Set.of()));
		assertEquals(Section.HIGH_VALUE, sectionOf(m, "Multiple rolls"));
	}

	@Test
	public void unknownRate_goesToLowAndHasNoEv()
	{
		prices.put(5, 1_000_000);
		List<Drop> drops = new ArrayList<>(Arrays.asList(new Drop("Mystery", 5, OptionalDouble.empty(), 1), d("Abyssal whip", 4151, 1 / 512.0, 1)));
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(4151), true, Set.of(4151), Set.of()));
		assertEquals(Section.OTHER, sectionOf(m, "Mystery"));
	}

	@Test
	public void simple_fewerThan15NoUniques_isUniform()
	{
		prices.put(1000, 50_000);
		List<Drop> drops = filler().subList(0, 9);
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(), true, Set.of(), Set.of()));
		assertEquals(1, m.size());
		assertEquals(9, m.get(Section.UNIFORM).size());
	}

	@Test
	public void simple_manyButAllLowValue_isUniform()
	{
		List<Drop> drops = new ArrayList<>(filler());
		drops.addAll(filler());
		Map<Section, List<Classified>> m = Classifier.classify(drops, ctx(Set.of(), true, Set.of(), Set.of()));
		assertEquals(Arrays.asList(Section.UNIFORM), new ArrayList<>(m.keySet()));
	}

	@Test
	public void notSimple_whenAnyUnique()
	{
		List<Drop> drops = filler().subList(0, 4);
		List<Drop> all = new ArrayList<>(drops);
		all.add(d("Abyssal whip", 4151, 1 / 512.0, 1));
		Map<Section, List<Classified>> m = Classifier.classify(all, ctx(Set.of(4151), true, Set.of(4151), Set.of()));
		assertFalse(m.containsKey(Section.UNIFORM));
		assertEquals(Arrays.asList(Section.UNIQUE, Section.OTHER), new ArrayList<>(m.keySet()));
	}
}
