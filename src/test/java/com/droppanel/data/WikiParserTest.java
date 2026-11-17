package com.droppanel.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import com.google.gson.JsonParseException;
import org.junit.Test;

public class WikiParserTest
{
	static String fx(String name)
	{
		try (InputStream in = WikiParserTest.class.getResourceAsStream("/wiki/" + name))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static Drop find(List<Drop> drops, String name)
	{
		return drops.stream().filter(x -> x.getItemName().equals(name)).findFirst().get();
	}

	@Test
	public void monster_zulrah()
	{
		assertEquals(new MonsterRef("Zulrah", "Zulrah#Serpentine"), WikiParser.monster(fx("monster_2042.json")).get());
	}

	@Test
	public void monster_none()
	{
		assertFalse(WikiParser.monster("{\"bucket\":[]}").isPresent());
	}

	@Test
	public void pickVariant_fallsBackToBasePage()
	{
		List<Drop> d = WikiParser.drops(fx("drops_Zulrah.json"), "Zulrah#Serpentine");
		Drop fang = find(d, "Tanzanite fang");
		assertEquals(1 - Math.pow(1023 / 1024.0, 2), fang.getPKill().getAsDouble(), 1e-12);
		assertEquals(-1, fang.getItemId());
	}

	@Test
	public void variant_exactMatchOnly()
	{
		long swords = WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard").stream()
			.filter(x -> x.getItemName().equals("Black shortsword")).count();
		assertEquals(1, swords);
		assertEquals(4 / 128.0, find(WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard"), "Black shortsword").getPKill().getAsDouble(), 1e-12);
	}

	@Test
	public void variant_unknownSubFallsBackToStandard()
	{
		// A variant with no drop rows of its own falls back to #Standard
		List<Drop> d = WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Some new variant");
		assertEquals(4 / 128.0, find(d, "Black shortsword").getPKill().getAsDouble(), 1e-12);
	}

	@Test
	public void rareDropTableRowsIncluded()
	{
		assertTrue(WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard").stream()
			.anyMatch(x -> x.getItemName().equals("Dragon spear")));
	}

	@Test
	public void duplicatesMerged_coins()
	{
		List<Drop> d = WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard");
		assertEquals(1, d.stream().filter(x -> x.getItemName().equals("Coins")).count());
		// 35+9+7+6+1 out of 128 from the main table plus the RDT coin line
		assertEquals(58 / 128.0 + 1 / 390.1, find(d, "Coins").getPKill().getAsDouble(), 1e-6);
	}

	@Test
	public void quantityAveraged()
	{
		// Two lines: Always 100-299, and 5/249 (2 rolls) of 500; averaged weighted by chance
		Drop scales = find(WikiParser.drops(fx("drops_Zulrah.json"), "Zulrah"), "Zulrah's scales");
		double p2 = Rarity.perKill(5 / 249.0, 2);
		assertEquals((199.5 + p2 * 500) / (1 + p2), scales.getAvgQuantity(), 1e-9);
		assertEquals(1.0, scales.getPKill().getAsDouble(), 0);
		assertEquals(199.5 + 2 * 5 / 249.0 * 500, scales.getExpectedQuantityPerKill(), 1e-9);
		Drop fang = find(WikiParser.drops(fx("drops_Zulrah.json"), "Zulrah"), "Tanzanite fang");
		assertEquals(2 / 1024.0, fang.getExpectedQuantityPerKill(), 1e-12);
	}

	@Test(expected = JsonParseException.class)
	public void malformedResponse_isNotNoData()
	{
		WikiParser.monster("<html>Temporarily unavailable</html>");
	}

	@Test(expected = JsonParseException.class)
	public void apiError_isNotNoData()
	{
		WikiParser.monster("{\"error\":{\"code\":\"ratelimited\"},\"bucket\":[]}");
	}

	@Test(expected = JsonParseException.class)
	public void missingBucket_isNotNoData()
	{
		WikiParser.drops("{}", "Zulrah");
	}

	@Test(expected = JsonParseException.class)
	public void invalidDropRow_isNotNoData()
	{
		WikiParser.drops("{\"bucket\":[{\"page_name_sub\":\"Zulrah\",\"drop_json\":\"invalid\"}]}", "Zulrah");
	}

	@Test
	public void parseDrops_noRows_returnsEmpty()
	{
		assertTrue(WikiParser.drops("{\"bucket\":[]}", "X").isEmpty());
	}

	@Test
	public void itemIds_prefersDefaultVersion()
	{
		Map<String, Integer> m = WikiParser.itemIds(fx("items_sample.json"));
		assertEquals(12921, (int) m.get("pet snakeling"));
		assertEquals(995, (int) m.get("coins"));
	}

	@Test
	public void withIds_fillsKnownAndLeavesUnknown()
	{
		List<Drop> in = Arrays.asList(new Drop("Coins", -1, OptionalDouble.of(.5), 10), new Drop("Mystery", -1, OptionalDouble.empty(), 1));
		List<Drop> out = WikiParser.withIds(in, Map.of("coins", 995));
		assertEquals(995, out.get(0).getItemId());
		assertEquals(-1, out.get(1).getItemId());
	}
}
