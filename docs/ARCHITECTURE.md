# Architecture

Technical routing for the current implementation, checked against game `0.98a-RC8` and the LunaLib jar in `lib/`. Code paths below are relative to `jars/src/demandindicators/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [RELEASE.md](RELEASE.md) | Version numbers, release builder, package contents and publishing |
| [CHANGELOG.md](CHANGELOG.md) | Change-note format and upkeep for every implementation task |

## Start here

| Change / symptom | Route |
|---|---|
| No arrows at all | `ModPlugin.onGameLoad -> CargoStackAvailabilityIconProvider.register`; then [settings](#settings) |
| Wrong arrow on a stack | `getRankIconName -> getArrowIconName -> getSubmarketFor -> getCommodityStackIconName`; [indicator choice](#indicator-choice) |
| Arrow on player cargo, storage or a colony stockpile | `getSubmarketFor` (player cargo never matches a submarket) and `SubmarketPlugin.isFreeTransfer` in `getArrowIconName` |
| Marine rank or another mod's icon missing | `getHandlingPriority` (-1 off market stacks) and `getIconName -> getNextProvider`; [plugin picking](#plugin-picking) |
| Setting has no effect | `data/config/LunaSettings.csv` field id -> `LunaSettings.getBoolean("demandIndicators", <id>)` in `getCommodityStackIconName` |
| Arrow sprite missing or wrong file | `data/config/settings.json` `graphics.ui` key -> `SettingsAPI.getSpriteName("ui", <key>)`; [sprites](#sprites) |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `mod_info.json` | Mod id `demandIndicators`, `modPlugin` `demandindicators.plugins.ModPlugin`, jar `jars/demandindicators.jar`. No dependencies and not marked `utility` |
| `plugins/ModPlugin.onGameLoad` | Calls `CargoStackAvailabilityIconProvider.register()` on every load and new game |
| `GenericPluginManagerAPI` | `register()` adds one provider with `addPlugin(p, true)` when `getPluginsOfClass` finds none, so it is idempotent within a session |
| `data/config/settings.json` | Twelve `graphics.ui` sprite keys, see [sprites](#sprites) |
| `data/config/LunaSettings.csv` | A `General` header and the three settings, see [settings](#settings) |
| `data/config/LunaSettingsConfig.json` | LunaLib settings icon for `demandIndicators`: `graphics/icon/small_icon_demInd.png` |
| `data/config/version/version_files.csv` | Version Checker entry for `demandIndicators.version` |

Nothing is saved. The vanilla `GenericPluginManager` keeps transient plugins in a `transient` repository, so the provider is dropped on save and added again by the next `onGameLoad`. The mod can be removed from a save; adding `"utility": true` to `mod_info.json` would say so, but it is not set.

### Plugin picking

Evidence: `CargoStackView.renderAtCenter` (decompiled `0.98a-RC8`, `obf/campaign/ui/trade/CargoStackView.java` in the `starsector-knowledge` skill) and `GenericPluginManager.pickPlugin`.

- For every `RESOURCES` stack, every frame it renders, the game calls `pickPlugin(CommodityIconProvider.class, new CommodityIconProviderWrapper(stack))`. The plugin with the highest `getHandlingPriority` of at least 0 wins; on a tie the one registered first wins. Only the winner is asked for `getIconName` (a replacement commodity icon) and `getRankIconName` (a small sprite drawn 5 px in from the stack's top left corner).
- `getHandlingPriority` returns `GenericPluginManagerAPI.MOD_SUBSET` (300) only for stacks that get an arrow (`getArrowIconName` not null) and -1 for every other stack, so vanilla marine ranks (`PlayerFleetPersonnelTracker`, `CORE_GENERAL`) and other mods' providers own player cargo, storage and unmarked stacks.
- On a stack it claims, the arrow takes the rank icon slot, which also covers a vanilla marine rank or another mod's rank sprite there. `getIconName` passes the commodity icon on to `getNextProvider`: the provider `pickPlugin` would have chosen without this one (same rule, highest priority of at least 0, first on a tie), so another mod's replacement icon stays on the stack under the arrow.
- Crab Shack registers transient providers at `MOD_GENERAL` in its `onGameLoad`: crew while Meat Militia is active, lobsters while Lobster Uprising is active, and its nova drone commodity always. Each returns a replacement commodity icon and no rank icon, so on market stacks both mods show: its icon with the arrow on top.
- `getArrowIconName` runs twice per stack per frame (priority, then rank icon), including the settings and sprite lookups.

### Indicator choice

`getArrowIconName(stack)`, null meaning no arrow and no claim on the stack:

1. A stack being dragged (`isPickedUp`) gets none.
2. `getSubmarketFor` returns null for a stack without cargo, otherwise finds the submarket whose cargo is the stack's cargo, among the submarkets of the current interaction target's market (the market's primary entity when there is one). Player cargo and stacks outside a market dialog find none and get no arrow.
3. Only commodity stacks in a submarket whose plugin is not `isFreeTransfer()` get an arrow: storage and the colony's local resources are free transfer and get none.

`getCommodityStackIconName(stack, market)` reads `CommodityOnMarketAPI` for the stack's commodity: `excess = getExcessQuantity()`, `deficit = getDeficitQuantity()`, `econUnit = round(getCommodity().getEconUnit())`.

| Setting / state | Sprite key (`_invert` appended when Invert Indicators is on) |
|---|---|
| Show Trade Indicators off | None |
| Low Impact Mode, excess > 0 | `demandIndicators_commodityExcess` |
| Low Impact Mode, deficit > 0 | `demandIndicators_commodityDeficit` |
| Low Impact Mode, neither | None |
| excess > econUnit | `demandIndicators_commodityExcess_high` |
| 0 < excess <= econUnit | `demandIndicators_commodityExcess` |
| deficit > econUnit | `demandIndicators_commodityDeficit_high` |
| 0 < deficit <= econUnit | `demandIndicators_commodityDeficit` |
| neither, unit price > base price | `demandIndicators_commodityDeficit_low` |
| neither, unit price <= base price | `demandIndicators_commodityExcess_low` |

Excess is checked before deficit. The unit price is `market.getSupplyPrice(id, stackSize, true) / stackSize`, the price the market charges the player for the whole stack divided by the stack size, compared with `getCommodity().getBasePrice()`. The `_low` sprites are gray twins of the colored ones: a stack priced above base gets the gray arrow shaped like the orange shortage arrow, one at or below base the gray one shaped like the green excess arrow. Invert Indicators flips both sets together.

### Settings

`data/config/LunaSettings.csv` owns the ids, defaults and help text: `demandIndicators_show` (Show Trade Indicators, on), `demandIndicators_lowVis` (Low Impact Mode, off) and `demandIndicators_invert` (Invert Indicators, off). The header row's id is `IndEvo_header1`. There is no settings class and no cache: `getCommodityStackIconName` checks `ModManagerAPI.isModEnabled("lunalib")` and calls `LunaSettings.getBoolean("demandIndicators", <id>)` for all three on every call, which is every frame for each market stack. LunaLib loads its settings on first use and returns null for an unknown field id, which would throw on unboxing, so a new setting needs its CSV row in the same commit. A changed setting applies on the next frame.

LunaLib is optional (no dependency since `1.0.e`). Without it the three settings take their CSV defaults (show on, low impact off, invert off) and `LunaSettings` is never called.

### Sprites

`data/config/settings.json` maps twelve `graphics.ui` keys to files: `demandIndicators_commodityExcess`, `demandIndicators_commodityDeficit` and their `_high` and `_low` variants in `graphics/ui/`, and the same six with an `_invert` suffix in `graphics/ui/inverted/`. Normal and `_low` sprites are 20 x 20 px, `_high` sprites 20 x 28 px. `getCommodityStackIconName` resolves all six names it may need with `getSpriteName` on every call. The folders predate the mod prefix and are shared with vanilla folder names; do not move the files, the keys are what code uses.

### Build and checks

`tools/ReleaseBuild.java` (`build`, `package`) and `tools/ReleaseBuildChecks.java` compile against `lib/` and never ship in the release jar; they, `lib/` and `.github/workflows/release.yml` are owned by [RELEASE.md](RELEASE.md). Run the checks from the repository root with JDK 17:

```text
javac --release 17 -encoding UTF-8 -d out/release-tools jars/src/demandindicators/tools/ReleaseBuild.java jars/src/demandindicators/tools/ReleaseBuildChecks.java
java -cp out/release-tools demandindicators.tools.ReleaseBuildChecks
```

The IDE files (`.idea/`, `Demand Indicators.iml`, `demandindicators.iml`) are tracked. The jar artifact `demandindicators:jar` builds module `Demand Indicators` into the gitignored `jars/demandindicators.jar`.

## Data identity

| Stable ID | Display name |
|---|---|
| `demandIndicators` | Mod id, LunaLib mod id, Demand Indicators |
| `demandindicators` | Java package and jar name |
| `demandIndicators_show`, `demandIndicators_lowVis`, `demandIndicators_invert` | LunaLib field ids; players' values are stored under them |

## Source owners

Use `rg --files jars/src/demandindicators` for the complete inventory.

| File | Owner / connection |
|---|---|
| `plugins/ModPlugin.java` | Registers the provider in `onGameLoad` |
| `listener/CargoStackAvailabilityIconProvider.java` | The only runtime logic: `CommodityIconProvider` registration, priority, handing icons on to the next provider, submarket lookup, settings reads and the indicator choice |
| `tools/ReleaseBuild.java`, `tools/ReleaseBuildChecks.java` | Release builder and its checks, see [RELEASE.md](RELEASE.md) |

## Contracts

- `getRankIconName` runs inside rendering, once per market stack per frame. Keep it free of allocation heavy work and of anything that changes game state.
- `getHandlingPriority` must claim exactly the stacks `getRankIconName` draws on. Claiming more hides other providers on stacks that get nothing back; `getIconName` only passes the commodity icon on, never the rank icon.
- `getSubmarketFor` compares cargo by identity (`sub.getCargo() == stack.getCargo()`), the same way `PlayerFleetPersonnelTracker.getSubmarketFor` does.

## Dead or dormant

| Component | State |
|---|---|
| `CargoStackAvailabilityIconProvider.currSubmarket` | Never assigned; `getSubmarketFor` returns it (null) when nothing matches |
