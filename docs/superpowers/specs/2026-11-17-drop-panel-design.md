# Drop Panel: RuneLite plugin design

Date: 2026-11-17
Status: awaiting approval

## Intent

A RuneLite side panel showing, for the monster you are currently attacking:

- its drops and the per-kill chance of each,
- how lucky or dry you are (at your kill count, what % of players would have the drop by now),
- whether you already own each drop, and how many times.

Similar plugins (Loot Lookup, Collection Log Luck) exist, but the user finds them too bulky: one panel-wide row per item. This plugin's reason for existing is **compactness**. Important drops get small tiles, unimportant ones get tiny squares, and the details sit in a hover box.

## Goals

1. Automatically switch to whatever NPC the player is attacking. No manual search needed.
2. Show drops grouped by importance, in this order:
   1. **Uniques**: items in the monster's collection log entry. Each tile shows the icon, the drop rate and the dryness line. Greyed out if not obtained.
   2. **Tertiary**: pets, jars, clues, keys, totems and other tertiary drops. Each tile shows the icon and the rate. Greyed out if it is a collection log item and not obtained.
   3. **Rare**: non-unique drops at 1/5,000 or rarer, including rare drop table and gem table rolls (for example a dragon spear). Each tile shows the icon and the rate.
   4. **High value**: see the definition below. Each tile shows the icon and the rate.
   5. **Other drops**: small icon-only squares (about 31px).
3. Every item, tile or square:
   - **Hover:** a tooltip with the full name, the rate, the dryness line and the "you own xN" line.
   - **Click:** opens the item's OSRS Wiki page in the browser.
4. **Owned count:** a green "xN" badge on owned items.
5. **Dryness line:**
   - Not owned: "% of players who'd have ≥1 by now".
   - Owned N times: "% of players who'd have ≥N by now". For example, "26% have 2+".
6. **Simple-table rule:** if a table has no uniques AND (fewer than 15 items OR every item is low value), skip the sections and show every item as the same-size tile with its rate.
7. **Collection log sync:**
   - The plugin reads the in-game collection log as the player opens its pages. One manual flip-through is needed to populate it.
   - It then increments counts from the "New item added to your collection log" chat message and from collection log page re-opens.
   - It persists the counts per account.
8. **Kill count:**
   - Bosses: use the kill count RuneLite already stores from boss KC chat messages.
   - Other monsters: the plugin counts kills while it is running and persists them per account.

## Non-goals (v1)

- Raids and chest-based loot (CoX, ToB, ToA, Barrows, Gauntlet chests, Wintertodt and similar). The player doesn't "attack" a loot source there. *(My call. Could be a v2.)*
- Manually searching for a monster that isn't being fought. *(My call.)*
- Importing history from external sites. They are not dependable enough to build on.
- Profit tracking or loot logging. The Loot Tracker plugin already does this.

## Anti-goals

- A tall, scroll-heavy panel with one row per item. That is the thing this plugin exists to avoid.
- Showing a rate that isn't the real per-kill chance, such as "RDT 1/128".

## Constraints

- RuneLite plugin in Java, structured like the official plugin-hub template so it can be submitted to the Hub later. It runs locally via Gradle.
- Network calls only to the OSRS Wiki API, with a descriptive User-Agent identifying the plugin (no personal info).
- The data source is fixed by the user's choice (approach A below): live wiki lookups with a local cache.

## Approaches

- **A. Live from the OSRS Wiki with a local cache. Chosen by the user.**
  - On first encounter, fetch the monster's drop table from the wiki and cache it on disk.
  - Later lookups are instant and work offline.
  - New content and rate changes appear without a plugin update.
  - Cost: about one second of delay on the first lookup per monster, and dependence on the wiki's data format.
- **B. Bundled snapshot.** Pre-scrape every table into a file shipped with the plugin. Instant and offline, but stale until someone re-runs the scraper and ships an update. Rejected for staleness.
- **Sync alternatives considered:**
  - Reading the collection log in-game is the only reliable source of "already owned".
  - Non-collection-log items (for example a dragon spear) can't be synced. See the open item under "Left to the builder".

## Data: verified facts about the wiki API

The wiki's Bucket API gives everything needed, as checked on 2026-11-17.

**NPC ID to monster variant:**

```
api.php?action=bucket&format=json&query=
  bucket('infobox_monster').select('page_name','page_name_sub','id').where('id','<npcId>').run()
```

- `id` is a list. For example, Abyssal demon has `#Standard` = [415, 416], `#Catacombs of Kourend` = [7241] and `#Wilderness Slayer Cave` = [11239].
- The builder must confirm that `where` matches on list membership. If it doesn't, cache a full NPC-ID → page map instead.

**Drops:**

```
bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json').where('page_name','<page>').run()
```

- Filter by `page_name_sub` for the variant. If a variant has no drop rows of its own (for example the Catacombs version), fall back to `#Standard` or the first variant that has rows. *(My call.)*
- `drop_json` fields used:
  - `Rarity`, written as "Always", "1/1,024", "4/128" or "1/19,796.13". Other strings, such as "Varies", appear too.
  - `Rolls`
  - `Quantity Low` / `Quantity High`
  - `Drop type`
  - `Dropped item`
- **Rare drop table and gem table items are already expanded with combined per-kill rates.** For example, Abyssal demon → Dragon spear is listed at 1/19,796.13. No manual RDT math is needed.
- Items can appear on several lines with different quantities (for example Coins). For display, merge them into one item. *(My call.)* Their per-kill chances add up because they come from the same roll.
- **Per-kill chance with multiple rolls:** p_kill = 1 − (1 − p)^Rolls. Zulrah uniques are 1/1,024 with 2 rolls, which gives about 1/512 per kill.

## Maths

- p = per-kill chance. n = kill count. k = number owned.
- Dryness shown is P(X ≥ max(k, 1)) for X ~ Binomial(n, p): the share of players who would have at least that many by now.
- Compute it with a numerically stable method. Use the log-space binomial for small n and the Poisson approximation when p is tiny.
- Rates are displayed as "1/X", rounded sensibly: "1/512", "1/6,554", "1/19,796".

## Classification (my calls unless stated)

- **Unique:** the item is in this monster's collection log entry.
  - Source: the game's collection log data. This is preferably read from the client's enums and structs, which list each collection log page's item IDs, or otherwise from wiki data.
  - The page is matched to the monster by name.
- **Tertiary:** the wiki row is a tertiary drop. Its row category / `Drop type` should be confirmed, falling back to known tertiary items (pets, clues, jars, keys, totems, ancient shards).
- **Rare (user's rule):** not unique or tertiary, with p_kill ≤ 1/5,000.
- **High value:**
  - A drop's **expected value per kill** is p_kill × average quantity × GE price, using RuneLite's `ItemManager` price.
  - A drop is high value if its expected value per kill is ≥ 10% of the highest expected value per kill among the non-unique drops.
  - Equivalent to: compare each drop with the most valuable non-unique drop on the same table, using expected value per kill.
- **Low value:** everything else.
- **"Every item is low value"** for the simple-table rule means no item qualifies as high value.

## Behaviour details (my calls)

- **Target selection:** the NPC the local player is interacting with, taken from `InteractingChanged` or from hitsplats the player deals.
  - The panel keeps the last target after combat ends, until a new target is attacked.
- **Kill detection** for self-counting: the NPC dies (`ActorDeath` / `NpcDespawned` after death) while the player was its last attacker.
- **Cache:**
  - Store drop tables under RuneLite's directory as JSON, refreshed after 7 days.
  - Stale data is shown immediately while a refresh runs in the background.
- **Rare non-collection-log items:** owned counts come from drops the plugin sees while running, via `NpcLootReceived`. Pre-install history isn't known, so these tiles don't grey out.
- **Item icons:** from `ItemManager.getImage`.
- **Wiki links:** `https://oldschool.runescape.wiki/w/<Item_name>`.
- **Panel width:** standard RuneLite sidebar (about 225px).
  - Unique, tertiary, rare and high-value tiles: 3 per row, about 66px wide.
  - Small squares: 6 per row.

## Left to the builder

- The exact Swing component structure and styling, matching RuneLite's dark theme (ColorScheme).
- Parsing edge-case `Rarity` strings ("Varies", "Once", "~1/x", fractions with notes). Unparseable rows are shown without a rate.
- Matching collection log pages to monsters when names differ (for example "Kalphite Queen" vs. variants, or slayer bosses).
- Whether the "Wiki API down and nothing cached" state needs more than a one-line message.
- Unit tests for: rarity parsing, roll adjustment, binomial dryness, classification thresholds and the simple-table rule.

## Mockup

`.superpowers/brainstorm/2228-1794930440/content/panel-layout-v2.html` (option B with dryness on unique tiles), plus the new Rare section between Tertiary and High value.
