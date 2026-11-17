package com.droppanel.data;

import java.util.Optional;

/**
 * Key/value text storage for cached drop tables.
 */
public interface CacheStore
{
	Optional<String> read(String key);

	void write(String key, String value);
}
