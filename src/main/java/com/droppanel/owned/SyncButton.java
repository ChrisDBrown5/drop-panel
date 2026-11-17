package com.droppanel.owned;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

/**
 * A "Sync Drop Panel" button inside the collection log, left of where WikiSync puts its own button.
 * Only the widgets created here are ever hidden; other plugins' children of the same parent are left alone.
 */
public class SyncButton
{
	/** Script that (re)builds the collection log interface. */
	static final int COLLECTION_LOG_SETUP = 7797;
	static final String TEXT = "Sync Drop Panel";
	private static final int X_FROM_RIGHT = 109;
	private static final int WIDTH = 90;
	private static final int BG = 0x3a3329;
	private static final int BG_HOVER = 0x4f463a;
	private static final int TEXT_COLOUR = 0xff981f;

	private final Client client;
	private final ClientThread clientThread;
	private final Runnable onClick;
	private final List<Widget> created = new ArrayList<>();

	public SyncButton(Client client, ClientThread clientThread, Runnable onClick)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.onClick = onClick;
	}

	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() == COLLECTION_LOG_SETUP)
		{
			remove();
			add();
		}
	}

	/** Must run on the client thread. */
	public void add()
	{
		Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
		Widget search = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
		if (parent == null || search == null)
		{
			return;
		}
		int y = search.getOriginalY();
		int h = search.getOriginalHeight();

		Widget bg = parent.createChild(-1, WidgetType.RECTANGLE)
			.setFilled(true)
			.setTextColor(BG)
			.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
			.setYPositionMode(search.getYPositionMode())
			.setPos(X_FROM_RIGHT, y)
			.setSize(WIDTH, h);
		bg.revalidate();

		Widget text = parent.createChild(-1, WidgetType.TEXT)
			.setText(TEXT)
			.setTextColor(TEXT_COLOUR)
			.setFontId(FontID.PLAIN_11)
			.setTextShadowed(true)
			.setXTextAlignment(WidgetTextAlignment.CENTER)
			.setYTextAlignment(WidgetTextAlignment.CENTER)
			.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
			.setYPositionMode(search.getYPositionMode())
			.setPos(X_FROM_RIGHT, y)
			.setSize(WIDTH, h);
		text.setHasListener(true);
		text.setAction(0, TEXT);
		text.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
		text.setOnMouseOverListener((JavaScriptCallback) ev -> bg.setTextColor(BG_HOVER));
		text.setOnMouseLeaveListener((JavaScriptCallback) ev -> bg.setTextColor(BG));
		text.revalidate();

		created.add(bg);
		created.add(text);
		parent.revalidate();
	}

	/** Hides only this button's widgets. Safe from any thread. */
	public void remove()
	{
		clientThread.invoke(() ->
		{
			for (Widget w : created)
			{
				w.setHidden(true);
			}
			created.clear();
		});
	}
}
