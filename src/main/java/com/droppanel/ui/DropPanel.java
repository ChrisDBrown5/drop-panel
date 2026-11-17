package com.droppanel.ui;

import com.droppanel.layout.ItemView;
import com.droppanel.layout.PanelModel;
import com.droppanel.layout.Section;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: a header (monster, kills, lock, refresh), the drop sections, and the ignored-NPC list.
 * All public methods must be called on the Swing EDT.
 */
public class DropPanel extends PluginPanel
{
	public interface Callbacks
	{
		void lockChanged(boolean locked);

		void refresh();

		void addIgnore(String name);

		void removeIgnore(String name);
	}

	private static final Color SECTION_GREY = new Color(0x8f8f8f);
	private static final String LOCKED = "🔒";
	private static final String UNLOCKED = "🔓";

	private final ItemManager itemManager;
	private final Callbacks callbacks;
	private final JPanel content = new JPanel();
	private final JPanel heading = new JPanel(new BorderLayout());
	private final JPanel ignoredList = new JPanel();
	private boolean locked;

	public DropPanel(ItemManager itemManager, Callbacks callbacks)
	{
		this.itemManager = itemManager;
		this.callbacks = callbacks;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		heading.setOpaque(false);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(heading);
		add(content);
		add(ignoreSection());

		showMessage("Attack a monster to see its drops");
	}

	public boolean isLocked()
	{
		return locked;
	}

	public void showMessage(String text)
	{
		content.removeAll();
		JLabel l = new JLabel("<html><div style='width:170px'>" + escape(text) + "</div></html>");
		l.setForeground(SECTION_GREY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(l);
		refresh();
	}

	public void showLoading(String monsterName)
	{
		setHeader(monsterName, "loading drops...");
		content.removeAll();
		refresh();
	}

	public void showStatus(String monsterName, String message)
	{
		setHeader(monsterName, "");
		showMessage(message);
	}

	private void setHeader(String name, String sub)
	{
		heading.removeAll();
		heading.add(header(name, sub, true), BorderLayout.CENTER);
	}

	public void show(PanelModel m)
	{
		content.removeAll();
		setHeader(m.getMonsterName(), m.getKills() + " kills");
		for (Map.Entry<Section, List<ItemView>> e : m.getSections().entrySet())
		{
			content.add(sectionLabel(e.getKey().getLabel()));
			boolean small = e.getKey() == Section.OTHER;
			JPanel grid = new JPanel(new GridLayout(0, small ? 6 : 3, 4, 4));
			grid.setOpaque(false);
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (ItemView v : e.getValue())
			{
				grid.add(small ? new ItemSquare(v, itemManager) : new ItemTile(v, itemManager));
			}
			content.add(grid);
		}
		refresh();
	}

	/** Replaces the ignored-NPC rows. */
	public void setIgnored(List<String> names)
	{
		ignoredList.removeAll();
		if (names.isEmpty())
		{
			JLabel none = new JLabel("None");
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(SECTION_GREY);
			ignoredList.add(none);
		}
		for (String name : names)
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 2));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel l = new JLabel(name);
			l.setForeground(Color.WHITE);
			JButton x = smallButton("×", "Stop ignoring " + name);
			x.addActionListener(ev -> callbacks.removeIgnore(name));
			row.add(l, BorderLayout.CENTER);
			row.add(x, BorderLayout.EAST);
			ignoredList.add(row);
		}
		refresh();
	}

	private JPanel header(String name, String sub, boolean withButtons)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 4));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel n = new JLabel(name);
		n.setFont(FontManager.getRunescapeBoldFont());
		n.setForeground(Color.WHITE);
		JLabel s = new JLabel(sub);
		s.setFont(FontManager.getRunescapeSmallFont());
		s.setForeground(ItemTile.ORANGE);
		text.add(n);
		text.add(s);
		p.add(text, BorderLayout.CENTER);

		if (withButtons)
		{
			JPanel buttons = new JPanel(new GridLayout(1, 2, 2, 0));
			buttons.setOpaque(false);
			JButton lock = smallButton(locked ? LOCKED : UNLOCKED, lockTooltip());
			lock.addActionListener(ev ->
			{
				locked = !locked;
				lock.setText(locked ? LOCKED : UNLOCKED);
				lock.setToolTipText(lockTooltip());
				callbacks.lockChanged(locked);
			});
			JButton refresh = smallButton("⟳", "Refresh drop data from the wiki");
			refresh.addActionListener(ev -> callbacks.refresh());
			buttons.add(lock);
			buttons.add(refresh);
			p.add(buttons, BorderLayout.EAST);
		}
		return p;
	}

	private String lockTooltip()
	{
		return locked ? "Locked: click to follow your target again" : "Lock the panel to this monster";
	}

	private JPanel ignoreSection()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		p.add(sectionLabel("Ignored NPCs"));

		JPanel addRow = new JPanel(new BorderLayout(4, 0));
		addRow.setOpaque(false);
		addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JTextField field = new JTextField();
		field.setToolTipText("NPC name to ignore");
		JButton add = new JButton("Add");
		add.setMargin(new Insets(2, 6, 2, 6));
		Runnable doAdd = () ->
		{
			String name = field.getText().trim();
			if (!name.isEmpty())
			{
				callbacks.addIgnore(name);
				field.setText("");
			}
		};
		add.addActionListener(ev -> doAdd.run());
		field.addActionListener(ev -> doAdd.run());
		addRow.add(field, BorderLayout.CENTER);
		addRow.add(add, BorderLayout.EAST);
		p.add(addRow);

		ignoredList.setLayout(new BoxLayout(ignoredList, BoxLayout.Y_AXIS));
		ignoredList.setOpaque(false);
		ignoredList.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoredList.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		p.add(ignoredList);
		return p;
	}

	private static JButton smallButton(String text, String tooltip)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(0, 4, 0, 4));
		b.setFocusPainted(false);
		return b;
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

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void refresh()
	{
		revalidate();
		repaint();
	}
}
