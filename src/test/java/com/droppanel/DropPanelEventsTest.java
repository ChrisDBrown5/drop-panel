package com.droppanel;

import com.droppanel.data.DropTable;
import com.droppanel.data.TargetStore;
import com.droppanel.data.WikiClient;
import com.droppanel.owned.SyncButton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DropPanelEventsTest
{
	private final DropPanelPlugin plugin = new DropPanelPlugin();
	private final ClientThread clientThread = mock(ClientThread.class);
	private final WikiClient wiki = mock(WikiClient.class);
	private final TargetStore store = mock(TargetStore.class);
	private final Queue<Runnable> clientTasks = new ArrayDeque<>();
	private final AtomicReference<Consumer<WikiClient.Result>> response = new AtomicReference<>();
	private final DropTable table = new DropTable("Zulrah", Collections.emptyList(), 1);

	@Before
	public void setUp() throws Exception
	{
		set("lifecycle", new Object());
		set("currentNpcId", 2042);
		set("currentName", "Zulrah");
		set("clientThread", clientThread);
		set("wikiClient", wiki);
		set("store", store);
		set("clientToolbar", mock(ClientToolbar.class));
		set("syncButton", mock(SyncButton.class));
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		set("executor", executor);
		doAnswer(invocation ->
		{
			clientTasks.add(invocation.getArgument(0));
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(executor).execute(any(Runnable.class));
		doAnswer(invocation ->
		{
			response.set(invocation.getArgument(1));
			return null;
		}).when(wiki).fetch(eq(2042), any());
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = DropPanelPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private Object get(String name) throws Exception
	{
		Field field = DropPanelPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}

	private void fetch() throws Exception
	{
		Method method = DropPanelPlugin.class.getDeclaredMethod("fetch", int.class, String.class, boolean.class);
		method.setAccessible(true);
		method.invoke(plugin, 2042, "Zulrah", true);
	}

	private void drainClientTasks()
	{
		while (!clientTasks.isEmpty())
		{
			clientTasks.remove().run();
		}
	}

	@Test
	public void successfulRefresh_clearsNoDataState() throws Exception
	{
		set("currentNoData", true);
		fetch();
		response.get().accept(new WikiClient.Result(WikiClient.Outcome.TABLE, table));
		drainClientTasks();
		assertFalse((Boolean) get("currentNoData"));
		assertSame(table, get("currentTable"));
		verify(store).putTable(2042, table);
	}

	@Test
	public void shutdown_invalidatesQueuedWikiResponseEvenAfterReenable() throws Exception
	{
		fetch();
		response.get().accept(new WikiClient.Result(WikiClient.Outcome.TABLE, table));
		plugin.shutDown();
		set("lifecycle", new Object());
		set("currentNpcId", 2042);
		drainClientTasks();
		assertNull(get("currentTable"));
		verifyNoInteractions(store);
	}
}
