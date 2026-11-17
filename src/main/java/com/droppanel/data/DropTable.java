package com.droppanel.data;

import java.util.List;
import lombok.Value;

/**
 * A monster's drop table as fetched from the wiki.
 */
@Value
public class DropTable
{
	/** Wiki page name, e.g. "Zulrah". Also used to match the collection log page and kill count. */
	String monsterName;
	List<Drop> drops;
	long fetchedAtMillis;
}
