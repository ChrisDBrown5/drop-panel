package com.droppanel.data;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds OSRS Wiki Bucket API queries.
 */
public final class WikiQueries
{
	static final String API = "https://oldschool.runescape.wiki/api.php";

	private WikiQueries()
	{
	}

	public static String monsterByNpcId(int npcId)
	{
		return "bucket('infobox_monster').select('page_name','page_name_sub','id').where('id','" + npcId + "').run()";
	}

	public static String dropsByPage(String pageName)
	{
		return "bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json').where('page_name',"
			+ lit(pageName) + ").limit(500).run()";
	}

	/**
	 * Item id lookup for up to 50 item page names.
	 */
	public static String itemIds(List<String> names)
	{
		String or = names.stream().map(n -> "{'page_name'," + lit(n) + "}").collect(Collectors.joining(","));
		return "bucket('infobox_item').select('page_name','item_id','default_version').where(bucket.Or(" + or + ")).limit(500).run()";
	}

	public static String url(String query)
	{
		try
		{
			return API + "?action=bucket&format=json&query=" + URLEncoder.encode(query, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String lit(String s)
	{
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}
}
