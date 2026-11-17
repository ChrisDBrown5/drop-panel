package com.droppanel;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Drop Panel",
	internalName = "drop-panel",
	description = "Drop table, odds and dryness for what you're attacking",
	tags = {"drop", "loot", "dry", "collection", "wiki"}
)
public class DropPanelPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
	}

	@Override
	protected void shutDown()
	{
	}
}
