package com.droppanel.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.droppanel.data.Drop;
import java.util.OptionalDouble;
import org.junit.Test;

public class ItemViewTest
{
	private static ItemView view(Section s, double p, Owned owned, int kills)
	{
		return view(s, p, owned, kills, true);
	}

	private static ItemView view(Section s, double p, Owned owned, int kills, boolean logItem)
	{
		Drop d = new Drop("Serpentine visage", 12927, OptionalDouble.of(p), 1);
		return ItemView.of(new Classified(d, s, logItem), owned, kills);
	}

	@Test
	public void unique_notOwned()
	{
		ItemView v = view(Section.UNIQUE, 1 / 512.0, new Owned(true, 0), 512);
		assertEquals("1/512", v.getRateLine());
		assertEquals("63% have it", v.getDrynessLine());
		assertTrue(v.isGreyed());
		assertEquals(0, v.getOwnedBadge());
	}

	@Test
	public void unique_ownedTwice()
	{
		ItemView v = view(Section.UNIQUE, 1 / 512.0, new Owned(true, 2), 512);
		assertEquals("26% have 2+", v.getDrynessLine());
		assertFalse(v.isGreyed());
		assertEquals(2, v.getOwnedBadge());
		assertTrue(v.getTooltipHtml().contains("26% of players have 2+ by 512 kills"));
		assertTrue(v.getTooltipHtml().contains("You: own x2"));
	}

	@Test
	public void tertiary_hasNoDrynessLineButTooltipHasIt()
	{
		ItemView v = view(Section.TERTIARY, 1 / 4000.0, new Owned(true, 0), 512);
		assertNull(v.getDrynessLine());
		assertTrue(v.getTooltipHtml(), v.getTooltipHtml().contains("12% of players have it by 512 kills"));
		assertTrue(v.getTooltipHtml().contains("You: not obtained"));
		assertTrue(v.getTooltipHtml().contains("Serpentine visage"));
		assertTrue(v.getTooltipHtml().contains("Click to open wiki page"));
	}

	@Test
	public void notSynced_tooltipSaysSync()
	{
		ItemView v = view(Section.UNIQUE, 1 / 512.0, new Owned(false, 0), 512);
		assertFalse(v.isGreyed());
		assertTrue(v.getTooltipHtml().contains("Open your collection log to sync"));
	}

	@Test
	public void zeroKills_line()
	{
		assertEquals("0% have it", view(Section.UNIQUE, 1 / 512.0, new Owned(true, 0), 0).getDrynessLine());
	}

	@Test
	public void unknownRate_noDrynessLine()
	{
		Drop d = new Drop("Mystery", 1, OptionalDouble.empty(), 1);
		ItemView v = ItemView.of(new Classified(d, Section.UNIQUE, true), new Owned(true, 0), 10);
		assertEquals("?", v.getRateLine());
		assertNull(v.getDrynessLine());
	}

	@Test
	public void wikiUrl()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Larran%27s_key", ItemView.wikiUrl("Larran's key"));
	}

	@Test
	public void greyOnlyForCollectionLogItems()
	{
		ItemView v = view(Section.RARE, 1 / 5009.0, new Owned(true, 0), 100, false);
		assertFalse(v.isGreyed());
	}

	@Test
	public void namesAreHtmlEscaped()
	{
		Drop d = new Drop("A <b> & c", 1, OptionalDouble.of(.5), 1);
		ItemView v = ItemView.of(new Classified(d, Section.OTHER, false), new Owned(true, 0), 1);
		assertTrue(v.getTooltipHtml().contains("A &lt;b&gt; &amp; c"));
	}

	@Test
	public void nonLogItem_tooltipSaysSeenSinceInstall()
	{
		ItemView never = view(Section.RARE, 1 / 5009.0, new Owned(true, 0), 100, false);
		assertTrue(never.getTooltipHtml(), never.getTooltipHtml().contains("Seen since install: 0"));
		assertFalse(never.getTooltipHtml().contains("not obtained"));
		ItemView twice = view(Section.RARE, 1 / 5009.0, new Owned(true, 2), 100, false);
		assertTrue(twice.getTooltipHtml().contains("Seen since install: 2"));
	}
}
