package com.droppanel.data;

import lombok.Value;

/**
 * Which wiki page (and page variant) describes an NPC.
 */
@Value
public class MonsterRef
{
	String pageName;
	String pageNameSub;
}
