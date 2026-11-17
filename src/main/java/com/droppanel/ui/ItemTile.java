package com.droppanel.ui;

import com.droppanel.layout.ItemView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Large tile: icon, drop rate, optional dryness line and an owned-count badge.
 */
class ItemTile extends JPanel
{
	static final Color ORANGE = new Color(0xff981f);
	static final Color GREEN = new Color(0x3fd45a);
	static final Color GREY_TEXT = new Color(0x777777);

	ItemTile(ItemView v, ItemManager itemManager)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));

		JLabel badge = new JLabel(v.getOwnedBadge() > 0 ? "x" + v.getOwnedBadge() : " ");
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setForeground(GREEN);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(badge, BorderLayout.EAST);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
		add(top);

		add(centered(icon(v, itemManager)));
		add(centered(text(v.getRateLine(), v.isGreyed() ? GREY_TEXT : Color.WHITE)));
		if (v.getDrynessLine() != null)
		{
			add(centered(text(v.getDrynessLine(), ORANGE)));
		}
		decorate(this, v);
	}

	private static JLabel text(String s, Color c)
	{
		JLabel l = new JLabel(s);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(c);
		return l;
	}

	private static JComponent centered(JComponent c)
	{
		c.setAlignmentX(CENTER_ALIGNMENT);
		if (c instanceof JLabel)
		{
			((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
		}
		return c;
	}

	/** Item icon label, greyed when not obtained, "?" when the item id is unknown. */
	static JLabel icon(ItemView v, ItemManager itemManager)
	{
		JLabel l = new JLabel();
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setPreferredSize(new Dimension(36, 32));
		if (v.getItemId() <= 0)
		{
			l.setText("?");
			l.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD));
			l.setForeground(GREY_TEXT);
			return l;
		}
		AsyncBufferedImage img = itemManager.getImage(v.getItemId());
		Runnable apply = () -> l.setIcon(new ImageIcon(v.isGreyed() ? ImageUtil.alphaOffset(ImageUtil.grayscaleImage(img), 0.35f) : img));
		img.onLoaded(apply);
		apply.run();
		return l;
	}

	/** Tooltip and click-to-wiki shared by tiles and squares. */
	static void decorate(JComponent c, ItemView v)
	{
		c.setToolTipText(v.getTooltipHtml());
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		MouseAdapter click = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(v.getWikiUrl());
			}
		};
		c.addMouseListener(click);
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof JComponent)
			{
				((JComponent) child).setToolTipText(v.getTooltipHtml());
				child.addMouseListener(click);
			}
		}
	}
}
