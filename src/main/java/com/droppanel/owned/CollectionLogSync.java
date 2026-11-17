package com.droppanel.owned;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * Reads collection log pages as the player opens them, and the "new item" chat message.
 * The plugin forwards its events here.
 */
@Slf4j
public class CollectionLogSync
{
	private static final Pattern NEW_ITEM = Pattern.compile("^New item added to your collection log: (.+)$");

	private final Client client;
	private final ClientThread clientThread;
	private final OwnedStore store;
	/** Resolves an item name to an id via the current drop table, or null. */
	private final Function<String, Integer> nameToId;
	private final Runnable changed;

	public CollectionLogSync(Client client, ClientThread clientThread, OwnedStore store, Function<String, Integer> nameToId,
		Runnable changed)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.store = store;
		this.nameToId = nameToId;
		this.changed = changed;
	}

	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}
		// Let the script finish populating the page before reading it.
		clientThread.invokeLater(this::readOpenPage);
	}

	private void readOpenPage()
	{
		String localName = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
		if (!isOwnLog(frameTitle(), localName))
		{
			log.debug("skipping collection log sync: not the local player's log");
			return;
		}
		String title = pageTitle();
		Widget items = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (title == null || items == null || items.getDynamicChildren() == null)
		{
			return;
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (Widget w : items.getDynamicChildren())
		{
			int id = w.getItemId();
			if (id <= 0)
			{
				continue;
			}
			boolean obtained = w.getOpacity() == 0;
			counts.put(id, obtained ? Math.max(1, w.getItemQuantity()) : 0);
		}
		if (counts.isEmpty())
		{
			return;
		}
		log.debug("collection log page '{}' synced: {}", title, counts);
		store.recordLogPage(title, counts);
		changed.run();
	}

	private String pageTitle()
	{
		for (int component : new int[]{InterfaceID.Collection.HEADER, InterfaceID.Collection.HEADER_TEXT})
		{
			Widget w = client.getWidget(component);
			if (w == null)
			{
				continue;
			}
			Widget[] kids = w.getDynamicChildren();
			if (kids != null)
			{
				for (Widget k : kids)
				{
					String t = Text.removeTags(k.getText() == null ? "" : k.getText()).trim();
					if (!t.isEmpty())
					{
						return t;
					}
				}
			}
			String t = Text.removeTags(w.getText() == null ? "" : w.getText()).trim();
			if (!t.isEmpty())
			{
				return t;
			}
		}
		return null;
	}

	/**
	 * A log opened from a POH adventure log is titled "Collection Log - &lt;player&gt;"; only the local player's log is synced.
	 */
	static boolean isOwnLog(String frameTitle, String localName)
	{
		if (frameTitle == null)
		{
			return true;
		}
		String t = Text.removeTags(frameTitle).trim();
		int dash = t.indexOf(" - ");
		if (dash < 0)
		{
			return true;
		}
		String owner = t.substring(dash + 3).trim();
		return localName != null && Text.standardize(owner).equals(Text.standardize(localName));
	}

	private String frameTitle()
	{
		Widget frame = client.getWidget(InterfaceID.Collection.FRAME);
		if (frame == null)
		{
			return null;
		}
		for (Widget[] group : new Widget[][]{frame.getDynamicChildren(), frame.getStaticChildren()})
		{
			if (group == null)
			{
				continue;
			}
			for (Widget w : group)
			{
				String t = w.getText();
				if (t != null && Text.removeTags(t).startsWith("Collection Log"))
				{
					return t;
				}
			}
		}
		return null;
	}

	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		Matcher m = NEW_ITEM.matcher(Text.removeTags(e.getMessage()).trim());
		if (!m.matches())
		{
			return;
		}
		Integer id = nameToId.apply(m.group(1).trim());
		if (id != null && id > 0)
		{
			store.recordNewLogItem(id);
			changed.run();
		}
	}
}
