package com.droppanel.layout;

import com.droppanel.data.Drop;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts a drop table into panel sections.
 */
public final class Classifier
{
	static final double RARE_THRESHOLD = 1 / 5000.0;
	static final double HIGH_VALUE_FRACTION = 0.10;
	static final int SIMPLE_TABLE_SIZE = 15;

	private Classifier()
	{
	}

	public static Map<Section, List<Classified>> classify(List<Drop> drops, ClassifyContext ctx)
	{
		Section[] sections = new Section[drops.size()];
		boolean[] logItem = new boolean[drops.size()];
		double[] ev = new double[drops.size()];
		double maxEv = 0;

		for (int i = 0; i < drops.size(); i++)
		{
			Drop d = drops.get(i);
			int id = d.getItemId();
			boolean inLog = ctx.isMonsterPageKnown() ? ctx.getMonsterLogItems().contains(id) : ctx.getAllLogItems().contains(id);
			logItem[i] = ctx.getAllLogItems().contains(id);
			ev[i] = d.getExpectedQuantityPerKill() * ctx.getPrice().applyAsInt(id);

			if (TertiaryRules.isTertiary(d.getItemName(), id, ctx.getPetIds()))
			{
				sections[i] = Section.TERTIARY;
			}
			else if (inLog)
			{
				sections[i] = Section.UNIQUE;
			}
			else if (d.getPKill().isPresent() && d.getPKill().getAsDouble() <= RARE_THRESHOLD)
			{
				sections[i] = Section.RARE;
			}

			if (sections[i] != Section.UNIQUE)
			{
				maxEv = Math.max(maxEv, ev[i]);
			}
		}

		boolean anyUnique = false;
		boolean anyHigh = false;
		for (int i = 0; i < drops.size(); i++)
		{
			if (sections[i] == null)
			{
				sections[i] = ev[i] > 0 && ev[i] >= HIGH_VALUE_FRACTION * maxEv ? Section.HIGH_VALUE : Section.OTHER;
			}
			anyUnique |= sections[i] == Section.UNIQUE;
			anyHigh |= sections[i] == Section.HIGH_VALUE;
		}

		boolean simple = !anyUnique && (drops.size() < SIMPLE_TABLE_SIZE || !anyHigh);

		Map<Section, List<Classified>> grouped = new EnumMap<>(Section.class);
		for (int i = 0; i < drops.size(); i++)
		{
			Section s = simple ? Section.UNIFORM : sections[i];
			grouped.computeIfAbsent(s, k -> new ArrayList<>()).add(new Classified(drops.get(i), s, logItem[i]));
		}
		// EnumMap iterates in declaration order, which is display order; copy to a LinkedHashMap for callers.
		return new LinkedHashMap<>(grouped);
	}
}
