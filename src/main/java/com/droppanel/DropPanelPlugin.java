package com.droppanel;

import com.droppanel.data.CacheStore;
import com.droppanel.data.Drop;
import com.droppanel.data.DropTable;
import com.droppanel.data.DropTableCache;
import com.droppanel.data.FilepathCacheStore;
import com.droppanel.data.WikiClient;
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
import net.runelite.api.Client;
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

	private DropPanel panel;
	private NavigationButton navButton;
	private DropTableCache cache;
	private final CollectionLogIndex index = new CollectionLogIndex();
	private volatile OwnedStore ownedStore;
	private CollectionLogSync sync;
	private SyncButton syncButton;
	private KillCounts killCounts;

	/** NPC ids with a wiki fetch in flight, so repeated targeting doesn't refetch. */
	private final Set<Integer> fetching = ConcurrentHashMap.newKeySet();
	private volatile int currentNpcId = -1;
	private volatile DropTable currentTable;

	@Override
	protected void startUp()
	{
		panel = new DropPanel(itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Drop Panel")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		cache = new DropTableCache(openCacheStore(), gson, System::currentTimeMillis);
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
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				index.build(client);
			}
			syncButton.add(); // in case the log is already open
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		syncButton.remove();
		syncButton = null;
		navButton = null;
		panel = null;
		sync = null;
		ownedStore = null;
		currentTable = null;
		currentNpcId = -1;
		fetching.clear();
	}

	private CacheStore openCacheStore()
	{
		try
		{
			return new FilepathCacheStore(getPluginDirectory().join("tables"));
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Drop Panel: plugin data directory unavailable, drop tables will not be cached on disk", e);
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
		if (e.getSource() != client.getLocalPlayer())
		{
			return;
		}
		Actor target = e.getTarget();
		if (!(target instanceof NPC))
		{
			return; // keep the last target when combat ends
		}
		NPC npc = (NPC) target;
		if (!isAttackable(npc))
		{
			return;
		}
		int id = npc.getId();
		if (id == currentNpcId && currentTable != null)
		{
			return;
		}
		currentNpcId = id;
		currentTable = null;
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

	/** Show the cached table at once (if any), and fetch from the wiki when missing or stale. Called on the client thread. */
	private void load(int npcId, String npcName)
	{
		String name = npcName == null ? "Unknown" : npcName;
		executor.execute(() ->
		{
			Optional<DropTable> cached = cache.get(npcId);
			if (cached.isPresent())
			{
				setTable(npcId, cached.get());
				if (!cache.isStale(cached.get()))
				{
					return;
				}
			}
			else
			{
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null && currentNpcId == npcId)
					{
						panel.showLoading(name);
					}
				});
			}
			if (!fetching.add(npcId))
			{
				return;
			}
			wikiClient.fetch(npcId, result ->
			{
				fetching.remove(npcId);
				if (result.isPresent())
				{
					executor.execute(() -> cache.put(npcId, result.get()));
					setTable(npcId, result.get());
				}
				else if (!cached.isPresent())
				{
					SwingUtilities.invokeLater(() ->
					{
						if (panel != null && currentNpcId == npcId)
						{
							panel.showMessage("No drop data for " + name);
						}
					});
				}
			});
		});
	}

	private void setTable(int npcId, DropTable table)
	{
		if (currentNpcId != npcId)
		{
			return; // player moved on to another target
		}
		currentTable = table;
		rebuild();
	}

	/** Recompute the panel from the current table. Safe to call from any thread. */
	private void rebuild()
	{
		clientThread.invokeLater(() ->
		{
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
				if (panel != null && currentTable == table)
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
