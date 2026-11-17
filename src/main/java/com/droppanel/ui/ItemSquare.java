package com.droppanel.ui;

import com.droppanel.layout.ItemView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Small icon-only square for low-value drops.
 */
class ItemSquare extends JPanel
{
	ItemSquare(ItemView v, ItemManager itemManager)
	{
		super(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(31, 31));
		add(ItemTile.icon(v, itemManager), BorderLayout.CENTER);
		ItemTile.decorate(this, v);
	}
}
