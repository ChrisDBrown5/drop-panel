package com.droppanel.ui;

import com.droppanel.layout.ItemView;
import com.droppanel.layout.PanelModel;
import com.droppanel.layout.Section;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel. All public methods must be called on the Swing EDT.
 */
public class DropPanel extends PluginPanel
{
	private static final Color SECTION_GREY = new Color(0x8f8f8f);

	private final ItemManager itemManager;

	public DropPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		showMessage("Attack a monster to see its drops");
	}

	public void showMessage(String text)
	{
		removeAll();
		JLabel l = new JLabel("<html><div style='width:170px'>" + text + "</div></html>");
		l.setForeground(SECTION_GREY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(l);
		refresh();
	}

	public void showLoading(String monsterName)
	{
		removeAll();
		add(header(monsterName, "loading drops..."));
		refresh();
	}

	public void show(PanelModel m)
	{
		removeAll();
		add(header(m.getMonsterName(), m.getKills() + " kills"));
		for (Map.Entry<Section, List<ItemView>> e : m.getSections().entrySet())
		{
			add(sectionLabel(e.getKey().getLabel()));
			boolean small = e.getKey() == Section.OTHER;
			JPanel grid = new JPanel(new GridLayout(0, small ? 6 : 3, 4, 4));
			grid.setOpaque(false);
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (ItemView v : e.getValue())
			{
				grid.add(small ? new ItemSquare(v, itemManager) : new ItemTile(v, itemManager));
			}
			add(grid);
		}
		refresh();
	}

	private JPanel header(String name, String sub)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel n = new JLabel(name);
		n.setFont(FontManager.getRunescapeBoldFont());
		n.setForeground(Color.WHITE);
		JLabel s = new JLabel(sub);
		s.setFont(FontManager.getRunescapeSmallFont());
		s.setForeground(ItemTile.ORANGE);
		p.add(n);
		p.add(s);
		return p;
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(SECTION_GREY);
		l.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private void refresh()
	{
		revalidate();
		repaint();
	}
}
