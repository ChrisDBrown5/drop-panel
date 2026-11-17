# Drop Panel v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the ignore list (shift-menu, auto-ignore, manual), the lock, the collection log sync button, and a bounded 50-entry wiki store with no automatic refresh.

**Architecture:** Two new pure, unit-tested classes, `TargetStore` and `IgnoreList`. A pure `FullSync` turns captured items into per-page counts. The rest is RuneLite wiring: the menu entry, the in-log button, the panel header and the ignore section.

**Tech Stack:** As v1: Java 11 target, RuneLite client 1.13.x, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-17-drop-panel-v2-design.md`, which extends `docs/superpowers/specs/2026-11-17-drop-panel-design.md`.

## Global Constraints

- All v1 Global Constraints still hold:
  - no client-thread IO
  - OkHttp `enqueue` only
  - injected Gson
  - Filepath for files
  - LinkBrowser for links
  - gameval constants
  - Java 11
- Store capacity: **50** NPC ids, least recently used evicted. No automatic refresh.
- Auto-ignore threshold: **5** targetings with no data.
- Menu text: exactly `Ignore Drop Checks` / `Unignore Drop Checks`, only while Shift is held.
- Button text in the log: `Sync Drop Panel`.

## Review Focus

1. **Ignoring the currently shown monster.** The panel keeps showing it, and it stops reacting to that name afterwards. No crash. Pinned in Task 2 (`ignore_isCaseInsensitive`).
2. **Store full and a 51st monster arrives.** The least recently used entry is evicted, and the shown monster is never evicted mid-view, because a read refreshes recency. Pinned in Task 1 (`evictsLeastRecentlyUsed`, `getRefreshesRecency`).
3. **Store file corrupt or from v1.** Start empty and don't throw. Pinned in Task 1 (`corruptFile_startsEmpty`).
4. **Sync pressed with the index empty** (struct IDs wrong). Report "could not read collection log structure" and change nothing. Pinned in Task 3 (`emptyIndex_recordsNothing`).
5. **Locked panel with loot from another monster.** Kill counts still record, and the panel doesn't switch. This is wiring, so it's covered by the in-game test list.

---

### Task 1: TargetStore, a bounded wiki store

**Files:** Create `data/TargetStore.java` and test `TargetStoreTest.java`. Delete `data/DropTableCache.java` and its test, which `TargetStore` replaces.

**Interfaces:**
- Produces: `class TargetStore`, constructed with `(CacheStore store, Gson gson, int capacity)`.
  - `Optional<Entry> get(int npcId)`: refreshes recency.
  - `void putTable(int npcId, DropTable t)`
  - `void putNoData(int npcId, String npcName)`
  - `@Value static class Entry { DropTable table /* null when noData */; String npcName; boolean noData; }`
  - Persistence: a single key `"targets"` in the `CacheStore`. The whole map (in LRU order) is written on every put.
  - Reads happen once, in the constructor.

- [ ] **Step 1:** Write failing tests:
  - `roundTripTable`
  - `noDataEntry`
  - `evictsLeastRecentlyUsed` (capacity 3: put 1, 2, 3, get 1, put 4, so 2 is gone and 1, 3, 4 remain)
  - `getRefreshesRecency`
  - `persistsAcrossInstances` (the order survives too)
  - `corruptFile_startsEmpty`
- [ ] **Step 2:** Run them and watch them fail (compile).
- [ ] **Step 3:** Implement with an access-ordered `LinkedHashMap` and `removeEldestEntry` above capacity. Serialize as a JSON list of DTOs, oldest first.
- [ ] **Step 4:** Run them and watch them pass. Commit: `feat: bounded 50-entry wiki store with no-data entries`.

### Task 2: IgnoreList

**Files:** Create `ignore/IgnoreList.java` and test `IgnoreListTest.java`.

**Interfaces:**
- Produces: `class IgnoreList`, constructed with `(Supplier<String> load, Consumer<String> save, int autoThreshold)`.
  - `boolean isIgnored(String name)`
  - `void add(String name)`
  - `void remove(String name)`
  - `List<String> names()`: display names, sorted case-insensitively.
  - `boolean recordNoDataTarget(String name)`: returns true when this call auto-ignores the name.
  - Names are normalized for matching with `trim()` + lower case. The first-added spelling is shown.
  - Persistence: newline-separated names. No-data counters stay in memory only.

- [ ] **Step 1:** Write failing tests:
  - `ignore_isCaseInsensitive`
  - `addRemove`
  - `blankNamesRejected`
  - `autoIgnoreOnFifth` (calls 1–4 return false; call 5 returns true and the name is ignored)
  - `persists`
  - `namesSorted`
- [ ] **Step 2:** Run them and watch them fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run them and watch them pass. Commit: `feat: NPC ignore list with auto-ignore`.

### Task 3: Full collection log sync (pure part) and the index fix

**Files:** Create `owned/FullSync.java` and test `FullSyncTest.java`. Modify `owned/CollectionLogIndex.java`.

**Interfaces:**
- Produces:
  - `static Map<String, Map<Integer,Integer>> FullSync.pageCounts(Map<String, Set<Integer>> pages, Map<Integer,Integer> captured)`: every page maps each of its items to the captured quantity, or 0.
  - `CollectionLogIndex.pages()`: returns `Map<String, Set<Integer>>`.
  - `CollectionLogIndex` reads tab structs `{471,472,473,474,475}` → param 683 → page enum → param 689 / 690.

- [ ] **Step 1:** Write failing tests:
  - `capturedItemsGetQuantity_othersZero`
  - `itemOnTwoPages_countedOnBoth`
  - `emptyIndex_recordsNothing`
- [ ] **Step 2:** Run them and watch them fail.
- [ ] **Step 3:** Implement `FullSync`. Switch `CollectionLogIndex.build` to the tab struct list and expose `pages()`.
- [ ] **Step 4:** Run them and watch them pass. Commit: `feat: full collection log page counts; read log structure from tab structs`.

### Task 4: In-game sync button and capture

**Files:** Create `owned/SyncButton.java`. Modify `owned/CollectionLogSync.java` and `DropPanelPlugin.java`.

**Interfaces:**
- Produces:
  - `SyncButton` (client, clientThread, `Runnable onClick`) with `add()`, `remove()`, and `onScriptPostFired(ScriptPostFired)`. It re-adds the button when script **7797** (log setup) fires.
  - `CollectionLogSync.startFullSync()`, `onScriptPreFired(ScriptPreFired)` and `onGameTick()`.

- [ ] **Step 1: The button.**
  - It is a filled RECTANGLE plus a TEXT child, both created under `InterfaceID.Collection.UNIVERSE`.
  - Position: `ABSOLUTE_RIGHT`, x = 109, width 90, with the same y and height as `SEARCH_TOGGLE`. That sits left of WikiSync's spot, x 33–104.
  - Text: "Sync Drop Panel".
  - `setAction(0, "Sync Drop Panel")` and an op listener that calls `onClick`.
  - `remove()` hides only the widgets it created.
- [ ] **Step 2: Start the sync.** `startFullSync()`:
  - sets armed = true and clears the capture map,
  - calls `client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null)`, then `client.runScript(2240)`.
- [ ] **Step 3: Capture.** `onScriptPreFired`, when armed and the script ID is 4100:
  - `args[1]` → item id; `args[2]` → quantity if it is an `Integer`, else 1.
  - Record the tick of the last capture.
- [ ] **Step 4: Finish.** `onGameTick`, when armed and 2 ticks have passed since the last capture:
  - Disarm.
  - If the index is empty: post the console message `Drop Panel: could not read collection log structure` and change nothing.
  - Otherwise: `FullSync.pageCounts`, then `store.recordLogPage` for each page, then `changed.run()`, then post the console message `Drop Panel: collection log synced (N items obtained)`.
- [ ] **Step 5:** `./gradlew build` passes. Commit: `feat: Sync Drop Panel button in the collection log`.

### Task 5: Plugin wiring for ignore, auto-ignore, lock, store and refresh

**Files:** Modify `DropPanelPlugin.java`.

- [ ] **Step 1: Construction.**
  - Replace `DropTableCache` with `TargetStore(capacity 50)`. It is constructed on the executor at start-up.
  - `IgnoreList` uses the global config key `drop-panel.ignored`.
- [ ] **Step 2: `onInteractingChanged`.** Return early when:
  - the panel is locked, or
  - `ignoreList.isIgnored(npc.getName())`.
- [ ] **Step 3: `load`.**
  - On a store hit:
    - a table → show it;
    - noData → show "No drop data for X", and if `ignoreList.recordNoDataTarget(name)` returns true, post a console message and refresh the panel.
  - On a miss: fetch. On success call `putTable`. On no data call `putNoData`, then count the targeting as above.
  - Same-target suppression: skip when the same NPC *actor* is re-targeted. A new actor, or a return after another target, counts as a new targeting.
- [ ] **Step 4: Refresh.** Re-fetch the current NPC id, bypassing the store, and replace the entry.
- [ ] **Step 5: Shift menu.**
  - `onMenuEntryAdded`, when Shift is held and the entry type is `EXAMINE_NPC` with a non-null NPC: add `Ignore Drop Checks` or `Unignore Drop Checks`.
  - Type `MenuAction.RUNELITE`; the target copies the entry's target.
  - `onClick` toggles the ignore and refreshes the ignore section.
- [ ] **Step 6:** `./gradlew build` passes. Commit: `feat: ignore list, auto-ignore, lock and refresh wiring`.

### Task 6: Panel header buttons and the ignore section

**Files:** Modify `ui/DropPanel.java`.

**Interfaces:**
- Produces:
  - `DropPanel(ItemManager, Callbacks)`, where `interface Callbacks { void lockChanged(boolean locked); void refresh(); void addIgnore(String name); void removeIgnore(String name); }`.
  - `void setIgnored(List<String> names)`
  - `boolean isLocked()`

- [ ] **Step 1: Header.** The monster name and kills, plus small **🔒/🔓** toggle and **⟳** buttons, implemented as text buttons with tooltips "Lock to this monster" / "Refresh drop data from the wiki".
- [ ] **Step 2: Ignore section.**
  - A bottom "Ignored NPCs" section with a `JTextField` and an Add button. Enter also adds.
  - Each name is a row with an × button.
  - The section survives every `show` / `showMessage` / `showLoading` call: the content area is rebuilt, the footer is not.
- [ ] **Step 3:** `./gradlew build` passes. Commit: `feat: panel lock/refresh buttons and ignored NPCs section`.

### Final: in-game checklist for the user

1. Shift + right-click a goblin shows "Ignore Drop Checks". After choosing it, attacking goblins doesn't change the panel. It appears in Ignored NPCs, and × removes it.
2. Type a name into Ignored NPCs and press Add; that monster is ignored.
3. Attack an NPC with no wiki data 5 times: a console message appears and it's auto-ignored.
4. Lock on a Hill Giant, then attack something else: the panel stays and no loading is shown. Unlock, and it follows again.
5. Open the collection log: the "Sync Drop Panel" button appears. Click it and the chat says "collection log synced (N items obtained)". Uniques grey out and badges are correct, including quantities on stackable items such as Zulrah's mutagens or pets.
6. Attack the same monster repeatedly or switch between two monsters: no "loading" after the first time. After restarting the client, still no loading. ⟳ re-fetches.
