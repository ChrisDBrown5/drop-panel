package com.droppanel.layout;

import com.droppanel.data.Drop;
import com.droppanel.stats.Dryness;
import com.droppanel.stats.Format;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import lombok.Value;

/**
 * Everything one tile or square displays.
 */
@Value
public class ItemView
{
	int itemId;
	String name;
	Section section;
	String rateLine;
	/** Only set for uniques with a known rate. */
	String drynessLine;
	/** Owned count to badge, 0 for none. */
	int ownedBadge;
	boolean greyed;
	String tooltipHtml;
	String wikiUrl;

	public static ItemView of(Classified c, Owned owned, int kills)
	{
		Drop d = c.getDrop();
		String rate = Format.rate(d.getPKill());
		int count = owned.getCount();

		String pct = null;
		if (d.getPKill().isPresent())
		{
			pct = Format.percent(Dryness.atLeast(kills, d.getPKill().getAsDouble(), Math.max(count, 1)));
		}
		String have = count <= 1 ? "have it" : "have " + count + "+";

		String dryness = c.getSection() == Section.UNIQUE && pct != null ? pct + " " + have : null;
		boolean greyed = c.isCollectionLogItem() && owned.isKnown() && count == 0;

		StringBuilder tip = new StringBuilder("<html><b>").append(escape(d.getItemName())).append("</b><br>").append(rate);
		if (pct != null)
		{
			tip.append(" · ").append(pct).append(" of players ").append(have).append(" by ").append(kills).append(" kills");
		}
		tip.append("<br>");
		if (!owned.isKnown())
		{
			tip.append("Open your collection log to sync");
		}
		else if (count > 0)
		{
			tip.append("You: own x").append(count);
		}
		else
		{
			tip.append("You: not obtained");
		}
		tip.append("<br><i>Click to open wiki page</i></html>");

		return new ItemView(d.getItemId(), d.getItemName(), c.getSection(), rate, dryness, count, greyed, tip.toString(),
			wikiUrl(d.getItemName()));
	}

	public static String wikiUrl(String itemName)
	{
		try
		{
			return "https://oldschool.runescape.wiki/w/" + URLEncoder.encode(itemName.replace(' ', '_'), "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
