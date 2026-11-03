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
| Wrong arrow on a stack | `getRankIconName -> getSubmarketFor -> getCommodityStackIconName`; [indicator choice](#indicator-choice) |
| Arrow on player cargo, storage or a colony stockpile | `getSubmarketFor` (player cargo never matches a submarket) and `SubmarketPlugin.isFreeTransfer` in `getRankIconName` |
| Marine rank icon missing | `getDefaultStackIconName -> PlayerFleetPersonnelTracker.getRankIconName`; [plugin picking](#plugin-picking) |
| Another mod's commodity icon or rank icon not shown | `getHandlingPriority`; [plugin picking](#plugin-picking) |
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
- `getHandlingPriority` returns `GenericPluginManagerAPI.MOD_GENERAL` (100) for every stack. Vanilla `PlayerFleetPersonnelTracker` returns `CORE_GENERAL` (0) only for marine stacks with rank data and -1 otherwise, so this provider always wins over vanilla.
- Because it always wins, it hands every stack it does not mark back to `PlayerFleetPersonnelTracker.getInstance().getRankIconName(stack)` (`getDefaultStackIconName`), which returns the marine rank sprite or null. `getIconName` returns null, so commodity icons stay vanilla.
- Another mod's `CommodityIconProvider` at 100 or lower is never asked while this one is registered first, and loses its icons. The `todo` in `getHandlingPriority` names this; Crab Shack's crew provider is the known case.

### Indicator choice

`getRankIconName(stack)`:

1. A stack being dragged (`isPickedUp`) gets the default icon.
2. `getSubmarketFor` returns null for a stack without cargo, otherwise finds the submarket whose cargo is the stack's cargo, among the submarkets of the current interaction target's market (the market's primary entity when there is one). Player cargo and stacks outside a market dialog find none and get the default icon.
3. Only commodity stacks in a submarket whose plugin is not `isFreeTransfer()` get an arrow: storage and the colony's local resources are free transfer and keep the default icon.

`getCommodityStackIconName(stack, market)` reads `CommodityOnMarketAPI` for the stack's commodity: `excess = getExcessQuantity()`, `deficit = getDeficitQuantity()`, `econUnit = round(getCommodity().getEconUnit())`.

| Setting / state | Sprite key (`_invert` appended when Invert Indicators is on) |
|---|---|
| Show Trade Indicators off | Default icon |
| Low Impact Mode, excess > 0 | `demandIndicators_commodityExcess` |
| Low Impact Mode, deficit > 0 | `demandIndicators_commodityDeficit` |
| Low Impact Mode, neither | Default icon |
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
| `listener/CargoStackAvailabilityIconProvider.java` | The only runtime logic: `CommodityIconProvider` registration, priority, submarket lookup, settings reads and the indicator choice |
| `tools/ReleaseBuild.java`, `tools/ReleaseBuildChecks.java` | Release builder and its checks, see [RELEASE.md](RELEASE.md) |

## Contracts

- `getRankIconName` runs inside rendering, once per market stack per frame. Keep it free of allocation heavy work and of anything that changes game state.
- The provider must return the vanilla marine rank for stacks it does not mark, because its priority hides `PlayerFleetPersonnelTracker` from `pickPlugin`.
- `getSubmarketFor` compares cargo by identity (`sub.getCargo() == stack.getCargo()`), the same way `PlayerFleetPersonnelTracker.getSubmarketFor` does.

## Dead or dormant

| Component | State |
|---|---|
| `CargoStackAvailabilityIconProvider.currSubmarket` | Never assigned; `getSubmarketFor` returns it (null) when nothing matches |
| `CargoStackAvailabilityIconProvider.getIconName` | Always null; commodity icons stay vanilla |
