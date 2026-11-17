package com.droppanel.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public class WikiQueriesTest
{
	@Test
	public void escapesApostrophes()
	{
		assertTrue(WikiQueries.dropsByPage("Larran's key").contains("'Larran\\'s key'"));
	}

	@Test
	public void itemIds_usesOr()
	{
		assertTrue(WikiQueries.itemIds(Arrays.asList("A", "B")).contains("bucket.Or({'page_name','A'},{'page_name','B'})"));
	}

	@Test
	public void monsterQuery()
	{
		assertEquals("bucket('infobox_monster').select('page_name','page_name_sub','id').where('id','2042').run()",
			WikiQueries.monsterByNpcId(2042));
	}

	@Test
	public void urlIsEncoded()
	{
		String url = WikiQueries.url("bucket('x').run()");
		assertTrue(url.startsWith("https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query="));
		assertTrue(url.contains("bucket%28%27x%27%29.run%28%29"));
	}
}
