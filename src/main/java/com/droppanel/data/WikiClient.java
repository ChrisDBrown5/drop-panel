package com.droppanel.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches a drop table from the OSRS Wiki: NPC id -> monster page -> drop lines -> item ids.
 * All calls are asynchronous on OkHttp's thread pool; the callback also runs there.
 */
@Slf4j
@Singleton
public class WikiClient
{
	private static final String USER_AGENT = "RuneLite Drop Panel plugin";
	private static final int ITEM_BATCH = 50;

	private final OkHttpClient http;

	@Inject
	public WikiClient(OkHttpClient http)
	{
		this.http = http;
	}

	public void fetch(int npcId, Consumer<Optional<DropTable>> callback)
	{
		AtomicBoolean called = new AtomicBoolean();
		Consumer<Optional<DropTable>> done = result ->
		{
			if (called.compareAndSet(false, true))
			{
				callback.accept(result);
			}
		};
		get(WikiQueries.monsterByNpcId(npcId), done, monsterJson ->
		{
			Optional<MonsterRef> ref = WikiParser.monster(monsterJson);
			if (!ref.isPresent())
			{
				done.accept(Optional.empty());
				return;
			}
			MonsterRef m = ref.get();
			get(WikiQueries.dropsByPage(m.getPageName()), done, dropsJson ->
			{
				List<Drop> drops = WikiParser.drops(dropsJson, m.getPageNameSub());
				if (drops.isEmpty())
				{
					done.accept(Optional.empty());
					return;
				}
				List<String> names = new ArrayList<>();
				drops.forEach(d -> names.add(d.getItemName()));
				fetchIds(names, 0, new HashMap<>(), done, ids ->
					done.accept(Optional.of(new DropTable(m.getPageName(), WikiParser.withIds(drops, ids), System.currentTimeMillis()))));
			});
		});
	}

	private void fetchIds(List<String> names, int from, Map<String, Integer> acc, Consumer<Optional<DropTable>> fail,
		Consumer<Map<String, Integer>> then)
	{
		if (from >= names.size())
		{
			then.accept(acc);
			return;
		}
		List<String> batch = names.subList(from, Math.min(names.size(), from + ITEM_BATCH));
		get(WikiQueries.itemIds(batch), fail, json ->
		{
			acc.putAll(WikiParser.itemIds(json));
			fetchIds(names, from + ITEM_BATCH, acc, fail, then);
		});
	}

	private void get(String query, Consumer<Optional<DropTable>> fail, Consumer<String> onBody)
	{
		Request req = new Request.Builder()
			.url(WikiQueries.url(query))
			.header("User-Agent", USER_AGENT)
			.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("wiki request failed", e);
				fail.accept(Optional.empty());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("wiki request returned {}", response.code());
						fail.accept(Optional.empty());
						return;
					}
					onBody.accept(body.string());
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("wiki response handling failed", e);
					fail.accept(Optional.empty());
				}
			}
		});
	}
}
