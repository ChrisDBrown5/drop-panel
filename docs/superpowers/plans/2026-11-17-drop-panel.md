# Drop Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a RuneLite side-panel plugin. It shows the drop table of the NPC you're attacking, with per-kill rates, dryness percentages and owned counts, in a compact tile layout.

**Architecture:**
- **Pure-logic core**, fully unit-tested: rarity parsing, dryness maths, wiki JSON parsing, classification and tooltip text.
- **Thin RuneLite-facing layer**: target tracking, collection log sync, kill counts and the Swing panel.
- **Data source:** drop tables are fetched from the OSRS Wiki Bucket API with OkHttp, cached as JSON in the plugin directory, and refreshed after 7 days.

**Tech Stack:** Java 11 (release target), Gradle 8.10 wrapper, RuneLite client `latest.release` (compileOnly), Lombok 1.18.30, JUnit 4.12. Gson and OkHttp are injected from RuneLite.

**Spec:** `docs/superpowers/specs/2026-11-17-drop-panel-design.md`

## Global Constraints

- Follow the official `runelite/example-plugin` template structure. Rename everything:
  - package `com.droppanel`
  - group `com.droppanel`
  - project name `drop-panel`
  - `@PluginDescriptor(name = "Drop Panel", internalName = "drop-panel")`
- `build.gradle` targets Java 11 (`options.release.set(11)`). Do not add gson, okhttp or guice to `build.gradle`.
- Language and library rules:
  - No reflection.
  - Use `@Inject OkHttpClient` with `enqueue()` only. Never call the network on the client thread.
  - Use `@Inject Gson`.
  - Open URLs with `LinkBrowser`.
  - Use `net.runelite.api.gameval` constants. Don't hard-code widget IDs.
- File I/O goes only through `Filepath` from `getPluginDirectory()`.
- Logging: `log.debug` for anything per-event.
- Network: only `https://oldschool.runescape.wiki/api.php`, User-Agent `RuneLite Drop Panel plugin`. Never send account or user info.
- Thresholds from the spec:
  - Rare: `pKill <= 1/5000`.
  - High value: expected value per kill `>= 0.10 ×` the maximum expected value per kill over non-unique drops.
  - Simple table: no uniques AND (fewer than 15 drops OR no high-value drops).
  - Cache refresh: 7 days.
- Dryness: P(X ≥ max(k,1)) where X ~ Binomial(kills, pKill).
- Per-kill chance with rolls: `pKill = 1 − (1 − p)^rolls`.
- License: BSD-2-Clause (from the template).

## Review Focus

1. **NPC with no wiki drop data** (pets, quest NPCs, a brand-new boss, or the wiki down with nothing cached). The panel shows "No drop data for <name>" and doesn't throw. Pinned in Task 4 (`parseDrops_noRows_returnsEmpty`) and Task 7 (`missingTable_reportsEmpty`).
2. **Unparseable rarity strings** ("Varies", "Once", "2 × 1/128", "~1/50", "1/1,024; 1/512 on task"). These are shown without a rate and excluded from rare/high-value maths, never crashing parsing. Pinned in Task 2 (`parse_unparseable_isEmpty`) and Task 5 (`unknownRate_goesToLowAndHasNoEv`).
3. **The monster's collection log page isn't synced yet.** Nothing is greyed out, and the tooltip says "Open your collection log to sync", so the plugin never claims you lack an item you own. Pinned in Task 6 (`notSynced_tooltipSaysSync`) and Task 8 (`unsyncedPage_isNotGreyed`).
4. **Zero kills.** Dryness reads "0% have it", with no NaN and no division by zero. Pinned in Task 3 (`atLeast_zeroKills_isZero`) and Task 6 (`zeroKills_line`).
5. **Variant mismatch between NPC and drops.** For example, Zulrah's NPC variant `Zulrah#Serpentine` has drop rows under `Zulrah`. The fallback must still find the table. Pinned in Task 4 (`pickVariant_fallsBackToBasePage`).

---

## File Structure

```
build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper/*        (from template)
runelite-plugin.properties, LICENSE, .gitignore, README.md
src/main/java/com/droppanel/
  DropPanelPlugin.java        plugin entry: events, wiring, target tracking
  data/Drop.java              one merged drop (value object)
  data/DropTable.java         monster name + drops + fetchedAt
  data/Rarity.java            parse rarity strings, roll adjustment
  data/WikiQueries.java       build Bucket query strings (escaping)
  data/WikiParser.java        Bucket JSON -> variant / drops / item ids
  data/WikiClient.java        async OkHttp fetch pipeline -> DropTable
  data/DropTableCache.java    TTL cache over a CacheStore
  data/CacheStore.java        interface: read/write string by key
  data/FilepathCacheStore.java  Filepath-backed CacheStore
  stats/Dryness.java          binomial at-least-k
  stats/Format.java           "1/512", "63%", "<1%"
  layout/Section.java         enum UNIQUE, TERTIARY, RARE, HIGH_VALUE, OTHER, UNIFORM
  layout/TertiaryRules.java   tertiary detection
  layout/Classifier.java      DropTable + context -> PanelModel
  layout/PanelModel.java      ordered sections of ItemView
  layout/ItemView.java        what one tile/square shows
  owned/CollectionLogIndex.java  page name -> item ids, all CL ids, pets (from game enums)
  owned/OwnedStore.java       owned counts + synced pages, persisted via ConfigManager
  owned/CollectionLogSync.java  reads CL widgets + chat message
  kc/KillCounts.java          RuneLite boss KC + own counts
  ui/DropPanel.java           PluginPanel: header + sections
  ui/ItemTile.java            large tile (icon, rate, optional dryness, xN badge)
  ui/ItemSquare.java          small icon square
src/test/java/com/droppanel/  one *Test per logic class + DropPanelPluginTest (launcher)
src/test/resources/wiki/      saved Bucket JSON fixtures
```

---

### Task 1: Scaffold the plugin project

**Files:**
- Create: everything from the template, renamed. Also `.gitignore` additions, `src/main/java/com/droppanel/DropPanelPlugin.java` and `src/test/java/com/droppanel/DropPanelPluginTest.java`.

**Interfaces:**
- Produces: `DropPanelPlugin extends Plugin`, annotated `@Slf4j @PluginDescriptor(name = "Drop Panel", internalName = "drop-panel", description = "Drop table, odds and dryness for what you're attacking", tags = {"drop","loot","dry","collection","wiki"})`. It has empty `startUp()` / `shutDown()`.

- [ ] **Step 1:** In the project root, run `git init`. Copy the template files (`build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, `.gitignore`, `runelite-plugin.properties`, `src/test/resources/logback-test.xml`) from `https://github.com/runelite/example-plugin`. Don't copy the `.git` folder, `ExampleConfig` or the README.
- [ ] **Step 2:** Rename the following:
  - `settings.gradle`: `rootProject.name = 'drop-panel'`.
  - `build.gradle`: `group = 'com.droppanel'` and `pluginMainClass = 'com.droppanel.DropPanelPluginTest'`.
  - `runelite-plugin.properties`: `displayName=Drop Panel`, `author=Chris`, `description=Drop table, odds and dryness for what you're attacking`, `tags=drop,loot,dry,collection,wiki`, `plugins=com.droppanel.DropPanelPlugin`.
- [ ] **Step 3:** Add a BSD-2-Clause `LICENSE` (copyright 2026, Chris). Add `.superpowers/` to `.gitignore`.
- [ ] **Step 4:** Create `DropPanelPlugin` (Interfaces above) and `DropPanelPluginTest`. The test class is the template's `main` that calls `ExternalPluginManager.loadBuiltin(DropPanelPlugin.class)`.
- [ ] **Step 5:** Run `./gradlew build`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6:** Commit, including `docs/` (the spec and this plan):
  - `git add -A`
  - `git commit -m "chore: scaffold Drop Panel plugin from RuneLite template"`

---

### Task 2: Rarity parsing and per-kill chance

**Files:** Create `data/Rarity.java`. Test `RarityTest.java`.

**Interfaces:**
- Produces:
  - `static OptionalDouble Rarity.parse(String rarity)`: the per-roll probability.
  - `static double Rarity.perKill(double perRoll, int rolls)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void parse_always() { assertEquals(1.0, Rarity.parse("Always").getAsDouble(), 0); }
@Test public void parse_fractionWithCommas() { assertEquals(1/1024.0, Rarity.parse("1/1,024").getAsDouble(), 1e-12); }
@Test public void parse_numeratorAndDecimal() {
  assertEquals(4/128.0, Rarity.parse("4/128").getAsDouble(), 1e-12);
  assertEquals(1/19796.13, Rarity.parse("1/19,796.13").getAsDouble(), 1e-12); }
@Test public void parse_unparseable_isEmpty() {
  for (String s : new String[]{"Varies", "Once", "", null, "Unknown", "1/0"}) assertFalse(s, Rarity.parse(s).isPresent()); }
@Test public void perKill_twoRolls() { assertEquals(1 - Math.pow(1023/1024.0, 2), Rarity.perKill(1/1024.0, 2), 1e-12); }
@Test public void perKill_oneRoll_isIdentity() { assertEquals(0.25, Rarity.perKill(0.25, 1), 1e-12); }
@Test public void perKill_zeroOrNegativeRolls_treatedAsOne() { assertEquals(0.25, Rarity.perKill(0.25, 0), 1e-12); }
```

- [ ] **Step 2:** Run `./gradlew test --tests com.droppanel.data.RarityTest`. Expected: FAIL (class missing).
- [ ] **Step 3:** Implement `Rarity`.
  - Accept the strings "Always", or `a/b` where `a` and `b` are numbers (strip commas, allow decimals, b > 0, result clamped to (0,1]).
  - Anything else is empty, including strings with a leading `~`. Keep the parser strict; Review Focus #2 covers this.
  - `perKill` uses `rolls < 1 ? 1 : rolls`.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: parse wiki rarity strings and per-kill chance`.

---

### Task 3: Dryness and formatting

**Files:** Create `stats/Dryness.java` and `stats/Format.java`. Tests `DrynessTest.java` and `FormatTest.java`.

**Interfaces:**
- Produces:
  - `static double Dryness.atLeast(int kills, double pKill, int k)`: P(X ≥ k), with k < 1 treated as 1.
  - `static String Format.rate(OptionalDouble pKill)`.
  - `static String Format.percent(double fraction)`.

- [ ] **Step 1: Write the failing tests**

```java
// DrynessTest
@Test public void atLeastOne_zulrah512() { assertEquals(0.63248, Dryness.atLeast(512, 1/512.0, 1), 1e-4); }
@Test public void atLeastTwo_zulrah512() { assertEquals(0.26424, Dryness.atLeast(512, 1/512.0, 2), 1e-4); }
@Test public void atLeast_zeroKills_isZero() { assertEquals(0.0, Dryness.atLeast(0, 0.5, 1), 0); }
@Test public void atLeast_certainDrop() { assertEquals(1.0, Dryness.atLeast(3, 1.0, 3), 0); assertEquals(0.0, Dryness.atLeast(2, 1.0, 3), 0); }
@Test public void atLeast_tinyP_largeN_isStable() { assertEquals(1 - Math.exp(-50000/19796.13), Dryness.atLeast(50000, 1/19796.13, 1), 1e-4); }
@Test public void atLeast_kLessThanOne_treatedAsOne() { assertEquals(Dryness.atLeast(10, .1, 1), Dryness.atLeast(10, .1, 0), 0); }
// FormatTest
@Test public void rate() {
  assertEquals("1/512", Format.rate(OptionalDouble.of(1 - Math.pow(1023/1024.0, 2))));
  assertEquals("1/6,554", Format.rate(OptionalDouble.of(1/6554.0)));
  assertEquals("1/3.5", Format.rate(OptionalDouble.of(1/3.5)));
  assertEquals("Always", Format.rate(OptionalDouble.of(1.0)));
  assertEquals("?", Format.rate(OptionalDouble.empty())); }
@Test public void percent() {
  assertEquals("63%", Format.percent(0.63248)); assertEquals("<1%", Format.percent(0.004));
  assertEquals("0%", Format.percent(0.0)); assertEquals(">99%", Format.percent(0.9996)); assertEquals("100%", Format.percent(1.0)); }
```

- [ ] **Step 2:** Run `./gradlew test --tests "com.droppanel.stats.*"`. Expected: FAIL.
- [ ] **Step 3:** Implement `Dryness.atLeast`:
  - Return 0 if kills ≤ 0 or k > kills.
  - Return 1 if pKill ≥ 1.
  - Otherwise compute 1 − Σ_{i<k} P(X=i), with the terms built iteratively:
    - `t0 = exp(kills · log1p(−p))`
    - `t(i+1) = t(i) · (kills−i)/(i+1) · p/(1−p)`
  - Clamp the result to [0,1].
  - This replaces the spec's "Poisson when p is tiny": `log1p` keeps it stable.
- [ ] **Step 4:** Implement `Format`:
  - `rate`:
    - Empty → `"?"`.
    - p ≥ 1 → `"Always"`.
    - Otherwise X = 1/p. If X < 10, use one decimal with any trailing ".0" dropped. If X ≥ 10, round to the nearest integer with `,` grouping (Locale.US).
  - `percent`:
    - 0 → `"0%"`.
    - Between 0 and 0.005 → `"<1%"`.
    - 1.0 → `"100%"`.
    - At or above 0.995 → `">99%"`.
    - Otherwise round to a whole percent.
- [ ] **Step 5:** Run the same command. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: dryness maths and rate/percent formatting`.

---

### Task 4: Wiki query building and parsing

**Files:**
- Create: `data/Drop.java`, `data/WikiQueries.java`, `data/WikiParser.java`.
- Test: `WikiQueriesTest.java`, `WikiParserTest.java`.
- Fixtures: `src/test/resources/wiki/{monster_2042.json, drops_Zulrah.json, drops_Abyssal_demon.json, drops_Hill_Giant.json, items_sample.json}`.

**Interfaces:**
- Produces:
  - `@Value class Drop { String itemName; int itemId /* -1 if unknown */; OptionalDouble pKill; double avgQuantity; }`
  - `static String WikiQueries.monsterByNpcId(int npcId)`
  - `static String WikiQueries.dropsByPage(String pageName)`
  - `static String WikiQueries.itemIds(List<String> names)` (a caller passes at most 50 names)
  - `static String WikiQueries.url(String query)`: the full `api.php?action=bucket&format=json&query=…` URL, URL-encoded.
  - `@Value class MonsterRef { String pageName; String pageNameSub; }`
  - `static Optional<MonsterRef> WikiParser.monster(String json)`
  - `static List<Drop> WikiParser.drops(String json, String pageNameSub)`. Drops are merged by item name in first-seen order, with `itemId = -1`.
  - `static Map<String,Integer> WikiParser.itemIds(String json)`. Keys are lower-cased page names.
  - `static List<Drop> WikiParser.withIds(List<Drop> drops, Map<String,Integer> ids)`.

- [ ] **Step 1: Save the fixtures.** Use `python -m json.tool` only if you want them pretty-printed; the raw form is fine. Run this from the repo root:

```bash
W="https://oldschool.runescape.wiki/api.php"; UA="RuneLite Drop Panel plugin"; D=src/test/resources/wiki; mkdir -p $D
q(){ curl -s -G -A "$UA" "$W" --data-urlencode action=bucket --data-urlencode format=json --data-urlencode "query=$1" -o "$D/$2"; }
q "bucket('infobox_monster').select('page_name','page_name_sub','id').where('id','2042').run()" monster_2042.json
for p in "Zulrah" "Abyssal demon" "Hill Giant"; do q "bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json').where('page_name','$p').limit(500).run()" "drops_${p// /_}.json"; done
q "bucket('infobox_item').select('page_name','item_id','default_version').where(bucket.Or({'page_name','Tanzanite fang'},{'page_name','Pet snakeling'},{'page_name','Coins'})).run()" items_sample.json
```

- [ ] **Step 2: Write the failing tests**

```java
// WikiQueriesTest
@Test public void escapesApostrophes() { assertTrue(WikiQueries.dropsByPage("Larran's key").contains("'Larran\\'s key'")); }
@Test public void itemIds_usesOr() { assertTrue(WikiQueries.itemIds(Arrays.asList("A","B")).contains("bucket.Or({'page_name','A'},{'page_name','B'})")); }
@Test public void monsterQuery() { assertEquals("bucket('infobox_monster').select('page_name','page_name_sub','id').where('id','2042').run()", WikiQueries.monsterByNpcId(2042)); }
// WikiParserTest (load fixtures from classpath)
@Test public void monster_zulrah() { assertEquals(new MonsterRef("Zulrah","Zulrah#Serpentine"), WikiParser.monster(fx("monster_2042.json")).get()); }
@Test public void monster_none() { assertFalse(WikiParser.monster("{\"bucket\":[]}").isPresent()); }
@Test public void pickVariant_fallsBackToBasePage() {
  List<Drop> d = WikiParser.drops(fx("drops_Zulrah.json"), "Zulrah#Serpentine");
  Drop fang = d.stream().filter(x -> x.getItemName().equals("Tanzanite fang")).findFirst().get();
  assertEquals(1 - Math.pow(1023/1024.0, 2), fang.getPKill().getAsDouble(), 1e-12); }
@Test public void variant_exactMatchOnly() { // standard vs wilderness rows are not mixed
  long swords = WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard").stream().filter(x -> x.getItemName().equals("Black shortsword")).count();
  assertEquals(1, swords); }
@Test public void rareDropTableRowsIncluded() {
  assertTrue(WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard").stream().anyMatch(x -> x.getItemName().equals("Dragon spear"))); }
@Test public void duplicatesMerged_coins() {
  List<Drop> d = WikiParser.drops(fx("drops_Abyssal_demon.json"), "Abyssal demon#Standard");
  assertEquals(1, d.stream().filter(x -> x.getItemName().equals("Coins")).count()); }
@Test public void parseDrops_noRows_returnsEmpty() { assertTrue(WikiParser.drops("{\"bucket\":[]}", "X").isEmpty()); }
@Test public void itemIds_prefersDefaultVersion() {
  Map<String,Integer> m = WikiParser.itemIds(fx("items_sample.json"));
  assertEquals(12921, (int) m.get("pet snakeling")); assertEquals(995, (int) m.get("coins")); }
```

- [ ] **Step 3:** Run `./gradlew test --tests "com.droppanel.data.Wiki*"`. Expected: FAIL.
- [ ] **Step 4: Implement `WikiQueries`.** Escape `\` as `\\` and `'` as `\'`. URL-encode with `URLEncoder.encode(q, UTF_8)`.
- [ ] **Step 5: Implement `WikiParser`.** Parse statically with `new JsonParser().parse(json)`, which works on every Gson version RuneLite ships. Don't create a `Gson` instance.
  - **Note on `drop_json`:** it is a JSON string inside the JSON, so parse it a second time.
  - **Variant pick for `drops`:** use the first rule that yields rows:
    1. Rows whose `page_name_sub` equals the requested value.
    2. Rows whose `page_name_sub` equals the part before `#`.
    3. Rows whose `page_name_sub` ends with `#Standard`.
    4. The first `page_name_sub` that appears.
  - **Each row:**
    - p = `Rarity.parse(Rarity)`.
    - pKill = `Rarity.perKill(p, Rolls)`.
    - avgQ = (`Quantity Low` + `Quantity High`) / 2, defaulting to 1 when the fields are missing.
  - **Merge rows with the same item name:**
    - pKill = min(1, sum of the known pKills). If any pKill is unknown, the merged pKill is unknown.
    - avgQ = average weighted by pKill (unweighted if unknown).
  - **`itemIds`:** for each page, keep the row whose `default_version` key is present, else the first row. Take `item_id[0]`.
- [ ] **Step 6:** Run the same command. Expected: PASS.
- [ ] **Step 7:** Commit: `feat: wiki bucket queries and drop table parsing`.

---

### Task 5: Classification into sections

**Files:**
- Create: `layout/Section.java`, `layout/TertiaryRules.java`, `layout/Classifier.java` (which holds `ClassifyContext` and `Classified` as nested or sibling classes).
- Test: `ClassifierTest.java`, `TertiaryRulesTest.java`.

**Interfaces:**
- Consumes: `Drop` (Task 4).
- Produces:
  - `enum Section { UNIQUE, TERTIARY, RARE, HIGH_VALUE, OTHER, UNIFORM }`.
  - `static boolean TertiaryRules.isTertiary(String itemName, int itemId, Set<Integer> petIds)`.
  - `@Value class ClassifyContext`:
    - `Set<Integer> monsterLogItems`: the matched collection log page's items. Empty if there is no matched page.
    - `boolean monsterPageKnown`
    - `Set<Integer> allLogItems`
    - `Set<Integer> petIds`
    - `IntUnaryOperator price`: GE price per item id, 0 if unknown.
  - `@Value class Classified { Drop drop; Section section; boolean collectionLogItem; }`
  - `static Map<Section, List<Classified>> Classifier.classify(List<Drop> drops, ClassifyContext ctx)`. The map is a `LinkedHashMap` in the order UNIQUE, TERTIARY, RARE, HIGH_VALUE, OTHER, with empty sections omitted. In simple mode it is a single UNIFORM entry. Within a section, drops keep the wiki order.

- [ ] **Step 1: Write the failing tests.** Build drops with a helper `d(name, id, p, qty)` and a context helper.

```java
@Test public void tertiary_byNameAndPet() {
  assertTrue(TertiaryRules.isTertiary("Clue scroll (elite)", 1, Set.of()));
  assertTrue(TertiaryRules.isTertiary("Jar of swamp", 1, Set.of()));
  assertTrue(TertiaryRules.isTertiary("Brimstone key", 1, Set.of()));
  assertTrue(TertiaryRules.isTertiary("Ensouled abyssal head", 1, Set.of()));
  assertTrue(TertiaryRules.isTertiary("Pet snakeling", 12921, Set.of(12921)));
  assertFalse(TertiaryRules.isTertiary("Tanzanite fang", 12922, Set.of(12921))); }
@Test public void uniques_fromMonsterLogPage() { /* fang in monsterLogItems -> UNIQUE; pet in log + pet -> TERTIARY with collectionLogItem=true */ }
@Test public void uniques_fallbackToAnyLogPage_whenPageUnknown() { /* monsterPageKnown=false, whip in allLogItems -> UNIQUE */ }
@Test public void rare_atOrBelow1in5000() { /* dragon spear p=1/5009 -> RARE; p=1/4999 -> not RARE */ }
@Test public void highValue_isTenPercentOfMaxNonUniqueEv() { /* evs 1000, 100, 99 (non-unique) -> HIGH_VALUE, HIGH_VALUE, OTHER; a unique with ev 1e9 does not raise the bar */ }
@Test public void unknownRate_goesToLowAndHasNoEv() { /* pKill empty -> OTHER; no exception */ }
@Test public void simple_fewerThan15NoUniques_isUniform() { /* 9 drops, no uniques -> single UNIFORM section with all 9 */ }
@Test public void simple_manyButAllLowValue_isUniform() { /* 30 drops, all ev 0 (price 0) -> UNIFORM */ }
@Test public void notSimple_whenAnyUnique() { /* 5 drops incl 1 unique -> sectioned */ }
```

The commented bodies are written out by the implementer. The asserted outcomes are exactly the ones in the comments.
- [ ] **Step 2:** Run `./gradlew test --tests "com.droppanel.layout.*"`. Expected: FAIL.
- [ ] **Step 3: Implement `TertiaryRules`.** An item is tertiary if `petIds` contains its ID, or its name (case-insensitive):
  - starts with `clue scroll (`, `jar of `, `ensouled `, `dark totem `, `champion scroll`
  - or equals one of `brimstone key`, `larran's key`, `mossy key`, `giant key`, `ancient shard`, `scroll box (` (prefix), `clue bottle`
- [ ] **Step 4: Implement `Classifier`:**
  - `log = monsterPageKnown ? monsterLogItems.contains(id) : allLogItems.contains(id)`.
  - Assign sections in this order:
    1. `tertiary` → TERTIARY.
    2. Else if `log` → UNIQUE.
    3. Else if pKill is known and ≤ 1/5000 → RARE.
    4. Else, using `ev = pKill·avgQ·price(id)` (0 if unknown) and `maxEv` over every non-UNIQUE drop: `ev > 0 && ev ≥ 0.10·maxEv` → HIGH_VALUE.
    5. Otherwise → OTHER.
  - `collectionLogItem` = `allLogItems.contains(id)`.
  - **Simple mode:** no UNIQUE AND (`drops.size() < 15` OR no HIGH_VALUE) → everything goes in UNIFORM.
- [ ] **Step 5:** Run the same command. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: classify drops into panel sections`.

---

### Task 6: What each item shows (tile text and tooltip)

**Files:** Create `layout/Owned.java`, `layout/ItemView.java` and `layout/PanelModel.java`. Test `ItemViewTest.java`.

**Interfaces:**
- Consumes: `Classified`, `Dryness`, `Format`.
- Produces:
  - `@Value class Owned { boolean known; int count; }`
    - `known` is false when the item is a collection log item whose page hasn't been synced.
    - Non-collection-log items are always known, and their count comes from the plugin's seen drops.
  - `@Value class ItemView { int itemId; String name; Section section; String rateLine; String drynessLine /* null unless UNIQUE */; int ownedBadge /* 0 = none */; boolean greyed; String tooltipHtml; String wikiUrl; }`
  - `static ItemView ItemView.of(Classified c, Owned owned, int kills)`.
  - `@Value class PanelModel { String monsterName; int kills; Map<Section, List<ItemView>> sections; }`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void unique_notOwned() { // Serpentine visage, pKill 1/512, 512 kills, owned known 0
  ItemView v = view(UNIQUE, 1/512.0, new Owned(true, 0), 512);
  assertEquals("1/512", v.getRateLine()); assertEquals("63% have it", v.getDrynessLine()); assertTrue(v.isGreyed()); assertEquals(0, v.getOwnedBadge()); }
@Test public void unique_ownedTwice() { ItemView v = view(UNIQUE, 1/512.0, new Owned(true, 2), 512);
  assertEquals("26% have 2+", v.getDrynessLine()); assertFalse(v.isGreyed()); assertEquals(2, v.getOwnedBadge()); }
@Test public void tertiary_hasNoDrynessLineButTooltipHasIt() { ItemView v = view(TERTIARY, 1/4000.0, new Owned(true, 0), 512);
  assertNull(v.getDrynessLine()); assertTrue(v.getTooltipHtml().contains("12% of players have it by 512 kills")); assertTrue(v.getTooltipHtml().contains("You: not obtained")); }
@Test public void notSynced_tooltipSaysSync() { ItemView v = view(UNIQUE, 1/512.0, new Owned(false, 0), 512);
  assertFalse(v.isGreyed()); assertTrue(v.getTooltipHtml().contains("Open your collection log to sync")); }
@Test public void zeroKills_line() { assertEquals("0% have it", view(UNIQUE, 1/512.0, new Owned(true, 0), 0).getDrynessLine()); }
@Test public void wikiUrl() { assertEquals("https://oldschool.runescape.wiki/w/Larran%27s_key", ItemView.wikiUrl("Larran's key")); }
@Test public void greyOnlyForCollectionLogItems() { /* RARE dragon spear (not a CL item), owned 0 -> not greyed */ }
```

- [ ] **Step 2:** Run `./gradlew test --tests com.droppanel.layout.ItemViewTest`. Expected: FAIL.
- [ ] **Step 3: Implement `ItemView.of`:**
  - `k = max(owned.count, 1)`; `pct = Format.percent(Dryness.atLeast(kills, pKill, k))`.
  - **Dryness line:** UNIQUE only, and only when pKill is known: `count ≤ 1 → pct + " have it"`, else `pct + " have " + count + "+"`.
  - **`greyed`:** `collectionLogItem && owned.known && owned.count == 0`.
  - **`ownedBadge`:** `owned.count`.
  - **Tooltip:** HTML with these lines:
    1. The name.
    2. `rate + " · " + pct + " of players have it by " + kills + " kills"`, using `"have N+"` wording when count ≥ 2.
    3. `"You: own x" + count`, or `"You: not obtained"`, or `"Open your collection log to sync"` when not known.
    4. `"Click to open wiki page"`.
  - **`wikiUrl(name)`:** `https://oldschool.runescape.wiki/w/` + the name with spaces replaced by `_`, URL-encoded.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: tile and tooltip text for drop items`.

---

### Task 7: Wiki client and drop table cache

**Files:**
- Create: `data/DropTable.java`, `data/CacheStore.java`, `data/FilepathCacheStore.java`, `data/DropTableCache.java`, `data/WikiClient.java`.
- Test: `DropTableCacheTest.java`.

**Interfaces:**
- Consumes: `WikiQueries`, `WikiParser`, `Drop`.
- Produces:
  - `@Value class DropTable { String monsterName; List<Drop> drops; long fetchedAtMillis; }`
  - `interface CacheStore { Optional<String> read(String key); void write(String key, String value); }`
  - `class FilepathCacheStore implements CacheStore`: constructed with `Filepath dir` (`plugin.getPluginDirectory().join("tables")`). Each key is stored as `<key>.json`.
  - `class DropTableCache`, constructed with `(CacheStore store, Gson gson, LongSupplier clock)`:
    - `Optional<DropTable> get(int npcId)`
    - `boolean isStale(DropTable t)`: true when older than 7 days.
    - `void put(int npcId, DropTable t)`
  - `class WikiClient` (`@Inject OkHttpClient, Gson`):
    - `void fetch(int npcId, Consumer<Optional<DropTable>> done)`
    - Three chained `enqueue` calls: monster, then drops, then item IDs in chunks of 50.
    - Calls `done` on the OkHttp thread. `Optional.empty()` means no data or an error.

- [ ] **Step 1: Write the failing tests.** They use an in-memory `CacheStore` (a `HashMap`) and a mutable clock.

```java
@Test public void roundTrip() { cache.put(2042, table); assertEquals(table, cache.get(2042).get()); }
@Test public void missingTable_reportsEmpty() { assertFalse(cache.get(1).isPresent()); }
@Test public void staleAfterSevenDays() { cache.put(1, tableAt(0)); clock = 7L*24*3600*1000 + 1; assertTrue(cache.isStale(cache.get(1).get())); }
@Test public void freshWithinSevenDays() { cache.put(1, tableAt(0)); clock = 7L*24*3600*1000 - 1; assertFalse(cache.isStale(cache.get(1).get())); }
@Test public void corruptJson_treatedAsMissing() { store.write("2042", "{not json"); assertFalse(cache.get(2042).isPresent()); }
```

- [ ] **Step 2:** Run `./gradlew test --tests com.droppanel.data.DropTableCacheTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `DropTable`, `CacheStore`, `DropTableCache` and `FilepathCacheStore`.
  - `DropTableCache` catches `JsonParseException` and returns empty.
  - `FilepathCacheStore` swallows `IOException` with `log.debug`.
  - `OptionalDouble` doesn't round-trip through Gson. Serialize `pKill` as a nullable `Double` in a private DTO inside `DropTableCache`.
- [ ] **Step 4:** Implement `WikiClient`:
  - Requests use the `User-Agent: RuneLite Drop Panel plugin` header.
  - A non-2xx response, an empty monster result or empty drops ends with `done.accept(Optional.empty())`.
  - The resulting `DropTable.monsterName` is `MonsterRef.pageName`.
- [ ] **Step 5:** Run `./gradlew test`. Expected: all tests PASS.
- [ ] **Step 6:** Commit: `feat: wiki client and 7-day drop table cache`.

---

### Task 8: Owned counts and collection log sync

**Files:**
- Create: `owned/OwnedStore.java`, `owned/CollectionLogIndex.java`, `owned/CollectionLogSync.java`.
- Test: `OwnedStoreTest.java`.

**Interfaces:**
- Consumes: `Owned` (Task 6).
- Produces:
  - `class OwnedStore`, constructed with `(Function<String,String> load, BiConsumer<String,String> save, Gson gson)`.
    - In the plugin these are bound to `configManager.getRSProfileConfiguration("drop-panel", key)` / `setRSProfileConfiguration`.
    - Keys: `"clCounts"` (itemId → count), `"syncedPages"` (lower-cased page name → item ids), `"seen"` (itemId → count).
    - Methods:
      - `void recordLogPage(String pageName, Map<Integer,Integer> itemCounts)`: count 0 means not obtained.
      - `void recordNewLogItem(int itemId)`: sets count to max(count,1).
      - `void recordLoot(int itemId, int times)`: adds to `seen`, and to `clCounts` when the item's page is synced.
      - `Owned owned(int itemId, boolean collectionLogItem)`
      - `Optional<Set<Integer>> syncedPageItems(String pageName)`
  - `class CollectionLogIndex`. `void build(Client client)` runs on the client thread after login. Accessors:
    - `Optional<Set<Integer>> pageItems(String pageName)` (case-insensitive)
    - `Set<Integer> allItems()`
    - `Set<Integer> petIds()`: the page named "All Pets".
  - `class CollectionLogSync`, with `@Subscribe` handlers called from the plugin:
    - `onScriptPostFired(ScriptPostFired e)` for `ScriptID.COLLECTION_DRAW_LIST`.
    - `onChatMessage(ChatMessage e)`, matching `^New item added to your collection log: (.+)$` with colour tags stripped via `Text.removeTags`.

- [ ] **Step 1: Write the failing tests.** They use a `HashMap` for load/save.

```java
@Test public void unsyncedPage_isNotGreyed() { assertFalse(store.owned(12922, true).isKnown()); }
@Test public void syncedPage_reportsCounts() { store.recordLogPage("Zulrah", Map.of(12922, 0, 12932, 2));
  assertEquals(new Owned(true, 0), store.owned(12922, true)); assertEquals(new Owned(true, 2), store.owned(12932, true)); }
@Test public void newLogItem_setsAtLeastOne() { store.recordLogPage("Zulrah", Map.of(12922, 0)); store.recordNewLogItem(12922); store.recordNewLogItem(12922);
  assertEquals(1, store.owned(12922, true).getCount()); }
@Test public void loot_incrementsSeenAndSyncedLog() { store.recordLogPage("Zulrah", Map.of(12922, 1)); store.recordLoot(12922, 1); store.recordLoot(11286, 1);
  assertEquals(2, store.owned(12922, true).getCount()); assertEquals(new Owned(true, 1), store.owned(11286, false)); }
@Test public void persistsAcrossInstances() { store.recordLogPage("Zulrah", Map.of(12922, 3)); assertEquals(3, new OwnedStore(load, save, gson).owned(12922, true).getCount()); }
@Test public void pageItemsRemembered() { store.recordLogPage("Zulrah", Map.of(12922, 0)); assertEquals(Set.of(12922), store.syncedPageItems("zulrah").get()); }
```

- [ ] **Step 2:** Run `./gradlew test --tests com.droppanel.owned.OwnedStoreTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `OwnedStore` so the tests pass.
- [ ] **Step 4: Implement `CollectionLogIndex.build`** from the game's enums and structs:
  - Tabs: `client.getEnum(2102)`, whose values are tab struct IDs.
  - A tab struct's param `683` is an enum of page struct IDs.
  - A page struct's param `689` is the page name (string), and its param `690` is an enum of item IDs.
  - These IDs come from community collection log plugins and **must be verified in Step 6**. If they turn out wrong, fall back to `OwnedStore.syncedPageItems`/`recordLogPage` data alone. Keep `CollectionLogIndex` behind the same accessors so callers don't change.
- [ ] **Step 5: Implement `CollectionLogSync.onScriptPostFired`:**
  - Read the page title from the dynamic children of `InterfaceID.Collection.HEADER_TEXT`, taking the first child with non-empty text.
  - Read the items from the dynamic children of `InterfaceID.Collection.ITEMS_CONTENTS`: `getItemId()` and `getItemQuantity()`, where `getOpacity() == 0` means obtained and anything else means count 0.
  - Call `ownedStore.recordLogPage`, then tell the plugin to refresh.
  - Chat handler: resolve the item name to an ID through the current `DropTable` (case-insensitive). If the item isn't in the current table, ignore it; the next page open will catch it.
- [ ] **Step 6: Verify in game (user).**
  - Add a temporary `log.debug` in `CollectionLogIndex.build` for the page count and the item count of "Zulrah". Also add one in `onScriptPostFired` for the parsed title and item counts.
  - The user runs `./gradlew run`, logs in following https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts, and opens the Zulrah collection log page.
  - Expected:
    - The index reports more than 100 pages.
    - "zulrah" has about 10 items.
    - The sync log shows title "Zulrah" and the correct obtained counts.
  - If the title widget is wrong, find the right one with the Widget Inspector (developer mode) and update it.
  - Remove the temporary logs.
- [ ] **Step 7:** Commit: `feat: collection log index, sync and owned counts`.

---

### Task 9: Kill counts

**Files:** Create `kc/KillCounts.java`. Test `KillCountsTest.java`.

**Interfaces:**
- Produces: `class KillCounts`, constructed with `(BiFunction<String,String,String> getProfile, BiConsumer<String,String> setProfileOwn)`. In the plugin these are bound to `configManager.getRSProfileConfiguration(group, key)` and `setRSProfileConfiguration("drop-panel", "ownkc." + name, value)`.
  - `int kills(String monsterName)`: RuneLite's `killcount` group, key `monsterName.toLowerCase()`, if it is greater than 0. Otherwise the plugin's own count.
  - `void recordKill(String monsterName)`: increments the own count.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void prefersRuneLiteBossKc() { profile.put("killcount.zulrah", "512"); assertEquals(512, kc.kills("Zulrah")); }
@Test public void fallsBackToOwnCount() { kc.recordKill("Hill Giant"); kc.recordKill("Hill Giant"); assertEquals(2, kc.kills("Hill Giant")); }
@Test public void unknownMonster_isZero() { assertEquals(0, kc.kills("Goblin")); }
@Test public void ownCountPersists() { kc.recordKill("Goblin"); assertEquals(1, new KillCounts(get, set).kills("Goblin")); }
```

- [ ] **Step 2:** Run `./gradlew test --tests com.droppanel.kc.KillCountsTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `KillCounts`. Own counts are keyed `ownkc.<lower-cased name>` in group `drop-panel`. Unparseable values count as 0.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: kill counts from RuneLite KC with own-count fallback`.

---

### Task 10: Panel UI and plugin wiring

**Files:**
- Create: `ui/DropPanel.java`, `ui/ItemTile.java`, `ui/ItemSquare.java`.
- Modify: `DropPanelPlugin.java`.
- No unit tests. Swing and game events are verified in game.

**Interfaces:**
- Consumes: every earlier task's interfaces.
- Produces: `DropPanel extends PluginPanel`.
  - `void show(PanelModel m)`, `void showMessage(String text)` and `void showLoading(String monsterName)`. All are called on the EDT via `SwingUtilities.invokeLater`.

- [ ] **Step 1: Build `ItemTile`.** It is 66px wide.
  - Contents: the icon via `itemManager.getImage(id).addTo(label)`, `rateLine` (white), and `drynessLine` (orange `#ff981f`, if not null).
  - A green `xN` badge at the top-right when `ownedBadge > 0`.
  - When `greyed`: a desaturated, 35%-alpha icon (`ImageUtil.grayscaleImage` + `ImageUtil.alphaOffset`) and grey rate text.
  - `setToolTipText(tooltipHtml)`.
  - Left click → `LinkBrowser.browse(wikiUrl)`.
- [ ] **Step 2: Build `ItemSquare`.** 31×31, icon only, with the same tooltip, click and greying behaviour.
- [ ] **Step 3: Build `DropPanel`.**
  - Header: the monster name in bold white, and `"<kills> kills"` in orange.
  - Sections in model order, each with a small grey upper-case label: "Uniques", "Tertiary", "Rare", "High value", "Other drops", and "Drops" for UNIFORM.
  - UNIQUE, TERTIARY, RARE, HIGH_VALUE and UNIFORM render as `ItemTile` rows of 3. OTHER renders as `ItemSquare` rows of 6. Use a wrapping grid (`GridLayout(0,3,4,4)` / `GridLayout(0,6,4,4)`).
  - Colours from `ColorScheme.DARK_GRAY_COLOR` / `DARKER_GRAY_COLOR`.
  - Empty state: "Attack a monster to see its drops".
- [ ] **Step 4: Wire `DropPanelPlugin`.**
  - **`startUp`:**
    - Build the panel and add a `NavigationButton` with a 16×16 icon resource (`src/main/resources/com/droppanel/icon.png`, an optimised PNG).
    - Create `FilepathCacheStore(getPluginDirectory().join("tables"))`, `DropTableCache`, `OwnedStore`, `KillCounts`, `CollectionLogIndex`, and `CollectionLogSync` (register it with the `EventBus`).
  - **`shutDown`:** remove the nav button, unregister the sync and clear state.
  - **`onGameStateChanged(LOGGED_IN)`:** `clientThread.invokeLater(() -> index.build(client))`.
  - **`onInteractingChanged`:**
    - Only when the source is `client.getLocalPlayer()` and the target is an `NPC` whose `getComposition()` has an "Attack" action. Use the NPC ID from `npc.getId()`.
    - If it differs from the current one, call `load(npcId, npc.getName())`.
    - Keep the last target when the interaction ends.
  - **`load`:**
    - Show the cached table at once if present. If it is missing or stale, call `showLoading` (only when missing) and `wikiClient.fetch`.
    - On result: `cache.put`, then rebuild on the client thread. Item prices must be read via `itemManager.getItemPrice` on the client thread.
    - If the result is empty and nothing is cached: `showMessage("No drop data for " + name)`.
  - **`rebuild()`:**
    - Build the `ClassifyContext` from the index. `monsterPageKnown` is true when `index.pageItems(table.monsterName)` or `ownedStore.syncedPageItems(...)` is present.
    - Call `Classifier.classify`.
    - Map each item through `ItemView.of` with `ownedStore.owned(id, c.isCollectionLogItem())` and `killCounts.kills(table.monsterName)`.
    - Call `panel.show`.
  - **`onNpcLootReceived`:**
    - `killCounts.recordKill(npc.getName())`.
    - For each item, call `ownedStore.recordLoot(id, 1)` once per distinct item ID. Counting drops, not quantity, is my call.
    - If the NPC is the current target, call `rebuild()`.
  - **`onChatMessage`:** for the RuneLite KC message, call `rebuild()` on the next tick. RuneLite stores the count itself; we only re-read it.
- [ ] **Step 5:** Run `./gradlew build`. Expected: `BUILD SUCCESSFUL`, all tests pass.
- [ ] **Step 6: Verify in game (user).** Run `./gradlew run` and log in per the Jagex-accounts guide. Check each of these:
  1. **Attacking a Hill Giant:** a single "Drops" section with same-size tiles.
  2. **Attacking Zulrah (or any boss you have KC on):**
     - Sections appear as Uniques, Tertiary, Rare, High value and Other drops.
     - The unique tiles show the rate and "NN% have it".
  3. **Before syncing:** nothing is greyed and the tooltip says to sync. Open that boss's collection log page, and unobtained uniques grey out, with xN badges on owned items.
  4. **Hovering** any item shows the full name, rate, dryness and ownership.
  5. **Clicking** any item opens its wiki page.
  6. **Talking to a non-combat NPC** doesn't change the panel.
  7. **Attacking an NPC the wiki has no drops for** shows "No drop data for …".
  8. **Restarting the client** makes the panel load instantly for a monster seen before, served from the cache.
- [ ] **Step 7:** Commit: `feat: drop panel UI and plugin wiring`.
