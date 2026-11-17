package com.droppanel.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Stores each cache entry as {@code <key>.json} in the plugin's data directory.
 */
@Slf4j
public class FilepathCacheStore implements CacheStore
{
	private final Filepath dir;

	public FilepathCacheStore(Filepath dir)
	{
		this.dir = dir;
	}

	@Override
	public Optional<String> read(String key)
	{
		try
		{
			Filepath f = dir.joinSegment(key + ".json");
			if (!f.isFile())
			{
				return Optional.empty();
			}
			try (var in = f.openInputStream())
			{
				return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		catch (IOException | IllegalArgumentException e)
		{
			log.debug("drop table cache read failed for {}", key, e);
			return Optional.empty();
		}
	}

	@Override
	public void write(String key, String value)
	{
		try
		{
			dir.createDirectories();
			dir.joinSegment(key + ".json").write(value);
		}
		catch (IOException | IllegalArgumentException e)
		{
			log.debug("drop table cache write failed for {}", key, e);
		}
	}
}
