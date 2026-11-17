package com.droppanel;

import com.droppanel.data.CacheStore;
import com.droppanel.data.Drop;
import com.droppanel.data.DropTable;
import com.droppanel.data.FilepathCacheStore;
import com.droppanel.data.TargetStore;
import com.droppanel.data.WikiClient;
import com.droppanel.ignore.IgnoreList;
import com.droppanel.kc.KillCounts;
import com.droppanel.layout.ClassifyContext;
import com.droppanel.layout.Classified;
import com.droppanel.layout.Classifier;
import com.droppanel.layout.ItemView;
import com.droppanel.layout.PanelModel;
import com.droppanel.layout.Section;
import com.droppanel.owned.CollectionLogIndex;
import com.droppanel.owned.CollectionLogSync;
import com.droppanel.owned.OwnedStore;
import com.droppanel.owned.SyncButton;
import com.droppanel.ui.DropPanel;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Drop Panel",
	internalName = "drop-panel",
	description = "Drop table, odds and dryness for what you're attacking",
	tags = {"drop", "loot", "dry", "collection", "wiki"}
)
public class DropPanelPlugin extends Plugin
{
	static final String CONFIG_GROUP = "drop-panel";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Gson gson;
	@Inject
	private WikiClient wikiClient;
	@Inject
	private ScheduledExecutorService executor;

	static final int STORE_CAPACITY = 50;
	static final int AUTO_IGNORE_AFTER = 5;
	static final String IGNORED_KEY = "ignored";
	static final String IGNORE_OPTION = "Ignore Drop Checks";
	static final String UNIGNORE_OPTION = "Unignore Drop Checks";

	private DropPanel panel;
	private NavigationButton navButton;
	/** Created on the executor; every use is also on the executor, so it is ready before first use. */
	private TargetStore store;
	private IgnoreList ignoreList;
	private final CollectionLogIndex index = new CollectionLogIndex();
	private volatile OwnedStore ownedStore;
	private CollectionLogSync sync;
	private SyncButton syncButton;
	private KillCounts killCounts;
	/** Each enable cycle has its own identity; queued work from old cycles must be ignored. */
	private volatile Object lifecycle;

	/** NPC ids with a wiki fetch in flight, so repeated targeting doesn't refetch. */
	private final Set<Integer> fetching = ConcurrentHashMap.newKeySet();
	private volatile boolean locked;
	private volatile int currentNpcId = -1;
	private volatile String currentName;
	private volatile DropTable currentTable;
	/** True when the current NPC is known to have no wiki drop data. */
	private volatile boolean currentNoData;
	/** The NPC actor last targeted (client thread only), so re-clicking the same NPC is not a new targeting. */
	private NPC lastTargetActor;

	@Override
	protected void startUp()
	{
		Object cycle = new Object();
		lifecycle = cycle;
		ignoreList = new IgnoreList(() -> configManager.getConfiguration(CONFIG_GROUP, IGNORED_KEY),
			value -> configManager.setConfiguration(CONFIG_GROUP, IGNORED_KEY, value), AUTO_IGNORE_AFTER);
		panel = new DropPanel(itemManager, new DropPanel.Callbacks()
		{
			@Override
			public void lockChanged(boolean isLocked)
			{
				locked = isLocked;
			}

			@Override
			public void refresh()
			{
				refreshCurrent();
			}

			@Override
			public void addIgnore(String name)
			{
				ignoreList.add(name);
				showIgnored();
			}

			@Override
			public void removeIgnore(String name)
			{
				ignoreList.remove(name);
				showIgnored();
			}
		});
		panel.setIgnored(ignoreList.names());
		navButton = NavigationButton.builder()
			.tooltip("Drop Panel")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		executor.execute(() ->
		{
			if (lifecycle == cycle)
			{
				store = new TargetStore(openCacheStore(), gson, STORE_CAPACITY);
			}
		});
		killCounts = new KillCounts(configManager::getRSProfileConfiguration,
			(key, value) -> configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value));
		resetOwnedStore();

		syncButton = new SyncButton(client, clientThread, () ->
		{
			CollectionLogSync s = sync;
			if (s != null)
			{
				s.startFullSync();
			}
		});
		SyncButton button = syncButton;
		clientThread.invokeLater(() ->
		{
			if (lifecycle != cycle)
			{
				return;
			}
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				index.build(client);
			}
			button.add(); // in case the log is already open
		});
	}

	@Override
	protected void shutDown()
	{
		lifecycle = null;
		if (sync != null)
		{
			sync.stop();
		}
		clientToolbar.removeNavigation(navButton);
		syncButton.remove();
		syncButton = null;
		navButton = null;
		panel = null;
		sync = null;
		ownedStore = null;
		currentTable = null;
		currentNpcId = -1;
		currentName = null;
		currentNoData = false;
		locked = false;
		lastTargetActor = null;
		fetching.clear();
	}

	/** Runs on the executor. One file, targets.json, in the plugin data directory; the v1 per-NPC folder is removed. */
	private CacheStore openCacheStore()
	{
		try
		{
			Filepath dir = getPluginDirectory();
			Filepath legacy = dir.join("tables");
			if (legacy.isDirectory())
			{
				legacy.deleteRecursively();
			}
			return new FilepathCacheStore(dir);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Drop Panel: plugin data directory unavailable, drop tables will not be saved to disk", e);
			Map<String, String> mem = new ConcurrentHashMap<>();
			return new CacheStore()
			{
				@Override
				public Optional<String> read(String key)
				{
					return Optional.ofNullable(mem.get(key));
				}

				@Override
				public void write(String key, String value)
				{
					mem.put(key, value);
				}
			};
		}
	}

	private void resetOwnedStore()
	{
		if (sync != null)
		{
			sync.stop();
		}
		ownedStore = new OwnedStore(key -> configManager.getRSProfileConfiguration(CONFIG_GROUP, key),
			(key, value) -> configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value), gson);
		sync = new CollectionLogSync(client, clientThread, ownedStore, this::itemIdByName, this::rebuild, index);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		resetOwnedStore();
		rebuild();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN && index.allItems().isEmpty())
		{
			index.build(client);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged e)
	{
		if (locked || e.getSource() != client.getLocalPlayer())
		{
			return;
		}
		Actor target = e.getTarget();
		if (!(target instanceof NPC))
		{
			return; // keep the last target when combat ends
		}
		NPC npc = (NPC) target;
		if (!isAttackable(npc) || ignoreList.isIgnored(npc.getName()))
		{
			return;
		}
		if (npc == lastTargetActor)
		{
			return; // re-clicking the same NPC is not a new targeting
		}
		lastTargetActor = npc;

		int id = npc.getId();
		if (id == currentNpcId)
		{
			if (currentNoData)
			{
				countNoDataTarget(npc.getName());
			}
			if (currentTable != null || currentNoData || fetching.contains(id))
			{
				return; // already showing (or fetching) this monster
			}
		}
		currentNpcId = id;
		currentName = npc.getName();
		currentTable = null;
		currentNoData = false;
		load(id, npc.getName());
	}

	private static boolean isAttackable(NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		if (comp == null)
		{
			comp = npc.getComposition();
		}
		if (comp == null || comp.getActions() == null)
		{
			return false;
		}
		for (String action : comp.getActions())
		{
			if ("Attack".equalsIgnoreCase(action))
			{
				return true;
			}
		}
		return false;
	}

	/** Shows the stored entry if there is one; otherwise asks the wiki once. Called on the client thread. */
	private void load(int npcId, String npcName)
	{
		String name = npcName == null ? "Unknown" : npcName;
		Object cycle = lifecycle;
		executor.execute(() ->
		{
			if (cycle == null || lifecycle != cycle)
			{
				return;
			}
			Optional<TargetStore.Entry> entry = store.get(npcId);
			clientThread.invokeLater(() ->
			{
				if (lifecycle != cycle || currentNpcId != npcId)
				{
					return;
				}
				if (entry.isPresent())
				{
					if (entry.get().isNoData())
					{
						markNoData(npcId, name);
						countNoDataTarget(name);
					}
					else
					{
						setTable(npcId, entry.get().getTable());
					}
					return;
				}
				onEdt(npcId, () -> panel.showLoading(name));
				fetch(npcId, name, false);
			});
		});
	}

	/** The refresh button: ask the wiki again for the current monster, replacing what is stored. */
	private void refreshCurrent()
	{
		Object cycle = lifecycle;
		clientThread.invokeLater(() ->
		{
			if (cycle != null && lifecycle == cycle)
			{
				refreshCurrentOnClientThread();
			}
		});
	}

	private void refreshCurrentOnClientThread()
	{
		int npcId = currentNpcId;
		String name = currentName;
		if (npcId < 0 || name == null)
		{
			return;
		}
		if (currentTable == null)
		{
			onEdt(npcId, () -> panel.showLoading(name));
		}
		fetch(npcId, name, true);
	}

	private void fetch(int npcId, String name, boolean isRefresh)
	{
		Object cycle = lifecycle;
		if (!fetching.add(npcId))
		{
			return;
		}
		wikiClient.fetch(npcId, result -> clientThread.invokeLater(() ->
		{
			if (cycle == null || lifecycle != cycle)
			{
				return;
			}
			fetching.remove(npcId);
			switch (result.getOutcome())
			{
				case TABLE:
					executor.execute(() ->
					{
						if (lifecycle == cycle)
						{
							store.putTable(npcId, result.getTable());
						}
					});
					setTable(npcId, result.getTable());
					break;
				case NO_DATA:
					executor.execute(() ->
					{
						if (lifecycle == cycle)
						{
							store.putNoData(npcId, name);
						}
					});
					markNoData(npcId, name);
					if (!isRefresh)
					{
						countNoDataTarget(name);
					}
					break;
				case ERROR:
				default:
					if (currentNpcId == npcId && currentTable == null)
					{
						onEdt(npcId, () -> panel.showStatus(name, "Couldn't reach the OSRS Wiki for " + name + ". Press the refresh button to try again."));
					}
					else if (isRefresh)
					{
						console("Drop Panel: couldn't reach the OSRS Wiki - kept the saved drops for " + name);
					}
					break;
			}
		}));
	}

	private void markNoData(int npcId, String name)
	{
		if (currentNpcId != npcId)
		{
			return;
		}
		currentTable = null;
		currentNoData = true;
		onEdt(npcId, () -> panel.showStatus(name, "No drop data for " + name));
	}

	/** Client thread. */
	private void countNoDataTarget(String name)
	{
		if (ignoreList.recordNoDataTarget(name))
		{
			console("Drop Panel: auto-ignored " + name + " (no wiki drop data after " + AUTO_IGNORE_AFTER + " targets)");
			showIgnored();
		}
	}

	private void showIgnored()
	{
		Object cycle = lifecycle;
		List<String> names = ignoreList.names();
		SwingUtilities.invokeLater(() ->
		{
			if (cycle != null && lifecycle == cycle && panel != null)
			{
				panel.setIgnored(names);
			}
		});
	}

	/** Runs a panel update on the EDT if the panel still exists and the given NPC is still current. */
	private void onEdt(int npcId, Runnable r)
	{
		Object cycle = lifecycle;
		SwingUtilities.invokeLater(() ->
		{
			if (cycle != null && lifecycle == cycle && panel != null && currentNpcId == npcId)
			{
				r.run();
			}
		});
	}

	private void console(String message)
	{
		Object cycle = lifecycle;
		clientThread.invokeLater(() ->
		{
			if (cycle != null && lifecycle == cycle)
			{
				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
			}
		});
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		MenuEntry entry = e.getMenuEntry();
		if (entry.getType() != MenuAction.EXAMINE_NPC || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		NPC npc = entry.getNpc();
		if (npc == null || npc.getName() == null)
		{
			return;
		}
		String name = npc.getName();
		boolean ignored = ignoreList.isIgnored(name);
		client.getMenu().createMenuEntry(-1)
			.setOption(ignored ? UNIGNORE_OPTION : IGNORE_OPTION)
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(me ->
			{
				if (ignoreList.isIgnored(name))
				{
					ignoreList.remove(name);
				}
				else
				{
					ignoreList.add(name);
				}
				showIgnored();
			});
	}

	private void setTable(int npcId, DropTable table)
	{
		if (currentNpcId != npcId)
		{
			return; // player moved on to another target
		}
		currentTable = table;
		currentNoData = false;
		rebuild();
	}

	/** Recompute the panel from the current table. Safe to call from any thread. */
	private void rebuild()
	{
		Object cycle = lifecycle;
		clientThread.invokeLater(() ->
		{
			if (cycle == null || lifecycle != cycle)
			{
				return;
			}
			DropTable table = currentTable;
			OwnedStore owned = ownedStore;
			if (table == null || owned == null)
			{
				return;
			}
			Optional<Set<Integer>> page = index.pageItems(table.getMonsterName());
			if (!page.isPresent())
			{
				page = owned.syncedPageItems(table.getMonsterName());
			}
			Set<Integer> allLog = new HashSet<>(index.allItems());
			page.ifPresent(allLog::addAll);

			Map<Integer, Integer> prices = new java.util.HashMap<>();
			for (Drop d : table.getDrops())
			{
				if (d.getItemId() > 0)
				{
					prices.put(d.getItemId(), (int) Math.min(Integer.MAX_VALUE, itemManager.getItemPrice(d.getItemId())));
				}
			}
			ClassifyContext ctx = new ClassifyContext(page.orElse(Collections.emptySet()), page.isPresent(), allLog,
				index.petIds(), id -> prices.getOrDefault(id, 0));

			int kills = killCounts.kills(table.getMonsterName());
			Map<Section, List<ItemView>> sections = new LinkedHashMap<>();
			for (Map.Entry<Section, List<Classified>> e : Classifier.classify(table.getDrops(), ctx).entrySet())
			{
				List<ItemView> views = new ArrayList<>();
				for (Classified c : e.getValue())
				{
					views.add(ItemView.of(c, owned.owned(c.getDrop().getItemId(), c.isCollectionLogItem()), kills));
				}
				sections.put(e.getKey(), views);
			}
			PanelModel model = new PanelModel(table.getMonsterName(), kills, sections);
			SwingUtilities.invokeLater(() ->
			{
				if (lifecycle == cycle && panel != null && currentTable == table && ownedStore == owned)
				{
					panel.show(model);
				}
			});
		});
	}

	private Integer itemIdByName(String name)
	{
		DropTable table = currentTable;
		if (table == null)
		{
			return null;
		}
		String n = name.toLowerCase(Locale.ROOT);
		for (Drop d : table.getDrops())
		{
			if (d.getItemName().toLowerCase(Locale.ROOT).equals(n) && d.getItemId() > 0)
			{
				return d.getItemId();
			}
		}
		return null;
	}

	/** Counts kills (including ones that drop nothing) of NPCs the player was fighting. */
	@Subscribe
	public void onActorDeath(ActorDeath e)
	{
		if (!(e.getActor() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) e.getActor();
		Player me = client.getLocalPlayer();
		if (me == null || (me.getInteracting() != npc && npc.getInteracting() != me))
		{
			return;
		}
		DropTable table = currentTable;
		boolean isCurrent = npc.getId() == currentNpcId && table != null;
		String name = isCurrent ? table.getMonsterName() : npc.getName();
		if (name != null)
		{
			killCounts.recordKill(name);
		}
		if (isCurrent)
		{
			rebuild();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived e)
	{
		NPC npc = e.getNpc();
		boolean isCurrent = npc != null && npc.getId() == currentNpcId && currentTable != null;
		Set<Integer> distinct = new HashSet<>();
		for (ItemStack s : e.getItems())
		{
			// Noted drops (e.g. noted logs or ore) count as the regular item on the drop table.
			distinct.add(itemManager.canonicalize(s.getId()));
		}
		OwnedStore owned = ownedStore;
		if (owned != null)
		{
			distinct.forEach(id -> owned.recordLoot(id, 1));
		}
		if (isCurrent)
		{
			rebuild();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		CollectionLogSync s = sync;
		if (s != null)
		{
			s.onScriptPostFired(e);
		}
		SyncButton b = syncButton;
		if (b != null)
		{
			b.onScriptPostFired(e);
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired e)
	{
		CollectionLogSync s = sync;
		if (s != null)
		{
			s.onScriptPreFired(e);
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		CollectionLogSync s = sync;
		if (s != null)
		{
			s.onGameTick();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		CollectionLogSync s = sync;
		if (s != null)
		{
			s.onChatMessage(e);
		}
		if (e.getMessage() != null && e.getMessage().contains("kill count is"))
		{
			// RuneLite's chat commands plugin stores boss KC from this message; re-read it after it does.
			clientThread.invokeLater(this::rebuild);
		}
	}
}
