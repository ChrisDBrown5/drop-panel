package com.droppanel.ui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DropPanelTest
{
	private JButton button(Container parent, String tooltip)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && tooltip.equals(((JButton) child).getToolTipText()))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton found = button((Container) child, tooltip);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void loadingAndErrorStates_keepRefreshAndUnlockAvailable() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DropPanel.Callbacks callbacks = mock(DropPanel.Callbacks.class);
			DropPanel panel = new DropPanel(mock(ItemManager.class), callbacks);
			panel.showLoading("Zulrah");
			assertNotNull(button(panel, "Refresh drop data from the wiki"));
			JButton lock = button(panel, "Lock the panel to this monster");
			assertNotNull(lock);
			lock.doClick();
			panel.showStatus("Zulrah", "No drop data for Zulrah");
			JButton refresh = button(panel, "Refresh drop data from the wiki");
			assertNotNull(refresh);
			refresh.doClick();
			JButton unlock = button(panel, "Locked: click to follow your target again");
			assertNotNull(unlock);
			unlock.doClick();
			verify(callbacks).refresh();
			verify(callbacks).lockChanged(true);
			verify(callbacks).lockChanged(false);
		});
	}
}
