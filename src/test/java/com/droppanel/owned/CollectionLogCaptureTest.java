package com.droppanel.owned;

import com.droppanel.layout.Owned;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CollectionLogCaptureTest
{
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final CollectionLogIndex index = mock(CollectionLogIndex.class);
	private final Runnable changed = mock(Runnable.class);
	private final Map<String, String> config = new HashMap<>();
	private final OwnedStore store = new OwnedStore(config::get, config::put, new Gson());
	private final CollectionLogSync sync = new CollectionLogSync(client, clientThread, store, name -> null, changed, index);
	private final Widget title = mock(Widget.class);

	@Before
	public void setUp()
	{
		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Chris");
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Widget frame = mock(Widget.class);
		when(frame.getDynamicChildren()).thenReturn(new Widget[]{null, title});
		when(title.getText()).thenReturn("Collection Log");
		when(client.getWidget(InterfaceID.Collection.FRAME)).thenReturn(frame);
		when(client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS)).thenReturn(mock(Widget.class));
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(1);
		when(index.pages()).thenReturn(Map.of("zulrah", Set.of(12922, 12932)));
		when(index.allItems()).thenReturn(Set.of(12922, 12932));
		store.recordLogPage("Zulrah", Map.of(12922, 3, 12932, 2));
	}

	private void capture(int itemId, int quantity)
	{
		ScriptEvent script = mock(ScriptEvent.class);
		when(script.getArguments()).thenReturn(new Object[]{4100, itemId, quantity});
		ScriptPreFired event = new ScriptPreFired(CollectionLogSync.DRAW_ITEM_SCRIPT);
		event.setScriptEvent(script);
		sync.onScriptPreFired(event);
	}

	private void finish()
	{
		sync.onScriptPostFired(new ScriptPostFired(ScriptID.COLLECTION_DRAW_LIST));
		when(client.getTickCount()).thenReturn(2);
		sync.onGameTick();
	}

	@Test
	public void fullSync_rejectsOtherPlayersLog()
	{
		when(title.getText()).thenReturn("Collection Log - Zezima");
		sync.startFullSync();
		capture(12922, 1);
		finish();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		verify(client, never()).runScript(anyInt());
		verifyNoInteractions(changed);
	}

	@Test
	public void fullSync_rejectsPohHostLogEvenWithoutOwnerInTitle()
	{
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		sync.startFullSync();
		verify(client, never()).runScript(anyInt());
	}

	@Test
	public void fullSync_rejectsPartialCapture()
	{
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(2);
		sync.startFullSync();
		capture(12922, 1);
		finish();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		assertEquals(new Owned(true, 2), store.owned(12932, true));
		verifyNoInteractions(changed);
	}

	@Test
	public void fullSync_rejectsClosedInterface()
	{
		sync.startFullSync();
		capture(12922, 1);
		when(client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS)).thenReturn(null);
		finish();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		verifyNoInteractions(changed);
	}

	@Test
	public void fullSync_rechecksOwnershipBeforeSaving()
	{
		sync.startFullSync();
		capture(12922, 1);
		when(title.getText()).thenReturn("Collection Log - Zezima");
		finish();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		verifyNoInteractions(changed);
	}

	@Test
	public void fullSync_acceptsCompleteCaptureAndIgnoresUnobtainedItems()
	{
		sync.startFullSync();
		capture(12922, 5);
		capture(12922, 5); // Shared pages may draw the same item more than once.
		capture(12932, 0);
		capture(-1, 1);
		finish();
		assertEquals(new Owned(true, 5), store.owned(12922, true));
		assertEquals(new Owned(true, 0), store.owned(12932, true));
		verify(changed).run();
	}

	@Test
	public void fullSync_requiresDrawCompletionEvenForEmptyAccount()
	{
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(0);
		sync.startFullSync();
		when(client.getTickCount()).thenReturn(2);
		sync.onGameTick();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		verifyNoInteractions(changed);
	}

	@Test
	public void stoppedSync_doesNotSavePendingCapture()
	{
		sync.startFullSync();
		capture(12922, 1);
		sync.stop();
		finish();
		assertEquals(new Owned(true, 3), store.owned(12922, true));
		verifyNoInteractions(changed);
	}
}
