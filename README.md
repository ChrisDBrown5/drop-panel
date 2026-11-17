# Drop Panel

A compact [RuneLite](https://runelite.net/) sidebar plugin for Old School RuneScape. Attack a monster to see its drop table, drop rates, collection progress, and drop luck without a full-width row for every item.

**Status:** in development. Drop Panel has not been submitted to or approved for the RuneLite Plugin Hub. Live collection-log syncing still needs in-game verification.

## Features

- **Follow your target.** The panel follows attackable NPCs you interact with and keeps the last monster visible when combat ends.
- **Compact drop groups.** Important items appear in three-column tiles; other drops use six-column icon grids. Small, simple tables use a single group of tiles.
- **Drop rates and luck.** View per-kill drop rates and, for uniques, the percentage of players who would have received at least your number of copies by that kill count.
- **Collection progress.** Sync collection-log counts and see obtained-count badges. Confirmed missing collection items appear greyed out.
- **Lock and refresh.** Pin the panel to a monster or manually fetch its latest wiki data.
- **Ignore controls.** Ignore NPCs through the panel or a Shift + right-click menu entry. NPCs with confirmed missing wiki data are automatically ignored after five counted targetings in a session.
- **Bounded local cache.** Keep the 50 most recently used NPC IDs, including no-data results, between sessions. Entries do not refresh automatically.
- **Wiki links.** Click an item to open its OSRS Wiki page; hover for its name, rate, luck calculation, and obtained count.

## Installation

Drop Panel is currently available as a local development plugin. The regular RuneLite client, including one opened through Jagex Launcher, cannot install this repository's JAR directly through Plugin Hub.

### Run locally on Windows

Install a JDK and Git. The project targets Java 11 bytecode; its current local build and tests have been run with Eclipse Temurin JDK 21. Gradle is included through the wrapper.

```powershell
git clone https://github.com/ChrisDBrown5/drop-panel.git
cd drop-panel
.\gradlew.bat run
```

This launches RuneLite in developer mode with Drop Panel loaded. Keep the terminal open while using the client.

For a Jagex account:

1. Open the **Jagex account/authentication** panel in the development client's sidebar.
2. Click **Add Jagex Account** and complete the sign-in flow.
3. Select your character and click **Play Now** on the login screen.

See RuneLite's [official Jagex account development instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) for details.

## Using Drop Panel

1. Enable **Drop Panel** in RuneLite's plugin list and open its sidebar tab.
2. Attack a monster. The first lookup requests its drop table from the [OSRS Wiki](https://oldschool.runescape.wiki/); subsequent lookups use the saved cache while the NPC ID remains in it.
3. Open your own collection log and click **Sync Drop Panel** to request a full collection sync. Keep the interface open until the completion message appears. Opening an individual log page also updates that page when its complete contents can be read.
4. Use the lock button to pin the current monster, or the refresh button to request its current wiki data.

Full sync checks the captured item total against the game's collection total before replacing saved counts. Interrupted or incomplete captures keep the previous counts. Syncing from a POH host's collection log is disabled.

To manage ignored monsters, hold **Shift** while right-clicking an NPC and choose **Ignore Drop Checks** or **Unignore Drop Checks**. You can also add and remove names in the panel's **Ignored NPCs** section. Name matching ignores case and covers variants with the same name.

### Drop groups

| Group | Contents |
| --- | --- |
| Uniques | Items on the monster's collection-log page, with a fallback to known collection items when its page cannot be matched. |
| Tertiary | Recognized pets, clues, jars, keys, totem pieces, and similar items. |
| Rare | Remaining drops with a known per-kill chance of 1/5,000 or rarer. |
| High value | Remaining drops whose expected GE value per kill is at least 10% of the highest value among non-unique drops. |
| Other drops | Remaining items, shown as small icons. |

### Counts and luck

- **Kill counts:** use RuneLite's saved boss count when available; otherwise use kills observed by Drop Panel. Observed kills are persisted per RuneScape profile and do not include pre-install history.
- **Collection counts:** come from synced log pages and collection-item chat/loot events. Positive item confirmations can be shown before a complete page has been synced.
- **Other obtained counts:** count NPC loot events containing each item while the plugin is enabled, rather than the total stack quantity. They are labelled **Seen since install** in tooltips.
- **Luck percentage:** uses a binomial calculation with the monster's kill count, per-kill drop chance, and obtained count. It describes the share of players expected to have at least that many copies by now. It does not change the chance of the next drop.

Obtained counts are tracked per item across sources, rather than attributed to each monster. An item obtained from several monsters can therefore make the displayed monster-specific luck estimate misleading.

## Limitations

- Live drop lookups, collection-log capture, and interface placement need in-game validation before a Plugin Hub submission.
- Raids, reward chests, and other loot sources that are not NPC drops are outside the current tracking scope.
- NPC death detection is an estimate of kills involving the local player; it cannot recover historical counts for ordinary monsters.
- Wiki rates that cannot be parsed are shown without a numeric rate. Variant selection and collection-page matching can fall back when names or data differ.
- Tertiary classification uses recognized item names and the game's pet list; it may need updates for new content.
- The binomial luck model treats kills as independent trials with a fixed chance of receiving at least one item. It does not model variable rates or multiple copies of a unique in a single kill.
- Old cached tables remain readable. Refresh a monster to populate the corrected expected quantities used for multiple-roll value calculations.
- Jagex account sign-in is handled by RuneLite's development authentication panel, independently of Drop Panel.

## Data and storage

Drop Panel requests public monster and item data from the OSRS Wiki API. Its queries use NPC IDs, wiki page names, and item names; they do not need player account details.

Drop tables and confirmed no-data results are saved in `targets.json` under RuneLite's plugin data directory. The cache is shared across characters and limited to 50 NPC IDs. Ownership and observed kill counts are stored in RuneLite configuration per RuneScape profile. The ignore list is stored in plugin configuration.

## Development

Run the tests and build the plugin:

```powershell
.\gradlew.bat build
```

Build the standalone development JAR:

```powershell
.\gradlew.bat shadowJar
```

Build outputs are written to `build/libs/`. The normal plugin JAR is `drop-panel.jar`; the standalone development JAR ends in `-all.jar`. These are development outputs, not files to copy into a standard RuneLite installation.

The automated tests cover parsing, probability calculations, grouping, persistence, sync validation, panel controls, and callback handling across shutdown. They complement the required in-game checks.

Plugin metadata is in [`runelite-plugin.properties`](runelite-plugin.properties). It uses the Plugin Hub's `standard` build mode. Publishing requires a separate submission to [runelite/plugin-hub](https://github.com/runelite/plugin-hub#submitting-a-plugin).

## Issues and contributions

Please [open an issue](https://github.com/ChrisDBrown5/drop-panel/issues) with the NPC name, expected behavior, actual behavior, and steps to reproduce a problem. Screenshots and relevant log excerpts help with interface or data issues.

## License and acknowledgements

Licensed under the [BSD 2-Clause License](LICENSE).

Drop data comes from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/). Item icons and collection-log structure are read through RuneLite and the game client. The full collection-log capture approach was informed by WikiSync; this plugin's implementation is separate.

Drop Panel is a community project and is not affiliated with or endorsed by Jagex, RuneLite, or the OSRS Wiki.
