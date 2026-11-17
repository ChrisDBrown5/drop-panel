package com.droppanel.layout;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * What the side panel shows for one monster.
 */
@Value
public class PanelModel
{
	String monsterName;
	int kills;
	/** Sections in display order; empty sections are absent. */
	Map<Section, List<ItemView>> sections;
}
