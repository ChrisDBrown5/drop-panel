# Drop Panel v2: ignore list, lock, collection log sync button, bounded cache

Date: 2026-11-17
Status: approved in conversation ("Go")
Builds on: `2026-11-17-drop-panel-design.md`

## Intent

Give the user control over what the panel follows, make collection log sync a deliberate one-click action, and guarantee the plugin does not keep hitting the OSRS Wiki.

## Goals

1. **Ignore list.**
   - Shift + right-click on an NPC adds the menu entry **"Ignore Drop Checks"**. On an already-ignored NPC it is **"Unignore Drop Checks"**.
   - Ignoring is by NPC name, case-insensitive. One entry covers every variant (e.g. "Goblin"). *(My call.)*
   - Ignored NPCs never switch the panel and never trigger a lookup.
2. **Auto-ignore.**
   - An NPC with no wiki drop data that the player targets 5 or more times is added to the ignore list automatically.
   - "Targeted" means each time the player starts interacting with a different NPC actor of that name, or re-targets it after targeting something else.
   - A console chat message says that it was auto-ignored.
3. **Manual ignore area.** At the bottom of the panel there is an "Ignored NPCs" section with:
   - a name text box and an **Add** button,
   - the list of ignored names, each with a remove (×) button.
4. **Lock.**
   - A lock toggle in the panel header pins the current monster.
   - While locked, targeting changes are ignored completely: no panel switch and no lookup.
   - Loot, kill counts and collection log updates for the locked monster still apply.
5. **Collection log sync button.**
   - A **"Sync Drop Panel"** button is added inside the collection log interface.
   - Clicking it triggers the log's own Search, which makes the game draw every obtained item. The plugin captures each one (script 4100 pre-fire: item id and quantity) and records counts for every collection log page: captured items with their quantity, everything else 0.
   - The result is stored per account and reused from then on. Pressing the button again re-syncs.
   - Opening individual pages keeps updating those pages automatically, as in v1. *(My call.)*
   - This follows the WikiSync plugin's approach (Plugin Hub approved, BSD-2), but the code is written fresh.
6. **No repeated wiki calls.**
   - Each NPC id is fetched at most once and then kept in a store of the **50 most recently used** NPC ids, persisted to disk. That includes "no data" results.
   - **No automatic refresh.** A **⟳ refresh** button in the header re-fetches the current monster on demand.
   - Re-targeting the same monster never touches the wiki or the disk.

## Non-goals

- Ignoring by NPC id or by location.
- Syncing anything other than the collection log.
- Changing how drop rates, dryness or sections are computed (v1 is unchanged).

## Constraints

- All v1 constraints and Plugin Hub rules apply.
- The menu entry is local-only (MenuAction.RUNELITE); it sends nothing to the server.
- The in-game button must not remove widgets it didn't create. Other plugins, such as WikiSync, add children to the same parent.
- Fix found in recon: the collection log structure is read from tab structs 471–475 (param 683 → page enum, page param 689 name, 690 items), not enum 2102.

## Approaches

| Area | Considered | Chosen | Why |
|---|---|---|---|
| Wiki store | Per-NPC files with a 7-day refresh (v1) vs one bounded store | **One JSON file, LRU of 50, no auto refresh, manual ⟳** | The user asked for no re-checking and about 50 targets; one small file is simple to bound. |
| Collection log sync | Page-by-page only (v1) vs a button that triggers Search | **Button triggers Search and captures script 4100; page opens still sync** | A full sync in one click, using a proven approach. |
| Ignore key | NPC id vs name | **Name** | Covers all variants, and matches what the user sees in the menu. |

## Left to the builder

- Button styling inside the log: plain text on a filled rectangle is fine.
- Exact layout of the header buttons and the ignore section, matching RuneLite's dark theme.

## Unverified until the user's in-game test

- Whether script 4100's arguments give the item id at index 1 and the quantity at index 2.
- That Search draws every obtained item.
- That struct IDs 471–475 still match the live game.
