<p align="center">
  <img src="art/logo.png" alt="Cultivation" width="800">
</p>

<p align="center"><strong>Worth growing.</strong></p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue"></a>
  <a href="https://github.com/rfizzle/cultivation/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/rfizzle/cultivation/actions/workflows/ci.yml/badge.svg"></a>
</p>

Cultivation makes farming a practice instead of a chore. Farmland carries its own
fertility that drains as you harvest and recovers while it rests; rotation and
polyculture pay for planning a field instead of tiling one crop; a varied diet is worth
keeping; the unstackable meals finally repay the bowl; and a scythe turns manual
harvesting into rhythm.

Everything runs on vanilla crops and vanilla blocks — no new crops, ores, mobs, or
dimensions, and no vanilla recipe is taken away.

## Download

| [Modrinth](https://modrinth.com/mod/cultivation-agriculture-overhaul) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cultivation-agriculture-overhaul) | [GitHub Releases](https://github.com/rfizzle/cultivation/releases) | [Website](https://cultivation.rfizzle.com) | [Report an issue](https://github.com/rfizzle/cultivation/issues) |
| --- | --- | --- | --- | --- |

---

## Features

### Living soil

Every farmland block carries its own fertility. Harvesting drains it about **3%** a
time — half that when you rotate crops — and a fallow block recovers fully in about
**three in-game days**, twice as fast in the rain. Tired soil (below **25%**) grows
**25% slower** and turns visibly pale and cracked; exhausted soil grows at half speed
and yields the bare minimum. Bone meal on empty farmland restores a quarter instantly,
and `/cultivation soil` reads any block exactly.

Soil condition renders as a client-side overlay over vanilla farmland rather than as new
blocks, so it composes with Sodium, EBE, and shaders.

### Polyculture

A crop bordered by two different crops grows **20% faster**. Alternating rows — wheat,
carrots, potatoes — beat the monoculture slab, which is never punished, just outgrown. A
**sniffer** in the field doubles that bonus, and crops within **8 blocks of a beehive**
grow a further **10% faster**.

### A varied table

Eating the same food repeatedly restores **10% less** per repeat, down to a **50%
floor**; three different foods reset it entirely. Golden carrots stay excellent — they
just stop being the only answer. Food effects are never touched, and the tooltip says
plainly when a food is losing its appeal.

### Worth the bowl

**Rabbit Stew** grants 5% movement speed, **Beetroot Soup** 10% faster block breaking,
and **Mushroom Stew** 10% slower hunger drain — each for two minutes. **Suspicious
Stew** rolls one of the three at double strength on top of its usual gamble. **Pumpkin
pie** and **cookies** carry one of those buffs for a single minute, and **cake** grants
all three at once per slice.

### Enriched tilling and Fertilizer

Till with a **diamond** hoe and that block gains a permanent **10%** chance of an extra
drop per harvest (**netherite: 15%**). The composter produces **Fertilizer** instead of
bone meal — one dose covers a block's next **15 harvests** with a guaranteed **+1 crop**
each. Enrichment lasts until the block reverts to dirt, and the two stack.

### The scythe

A tool in iron, diamond, and netherite: one swing harvests a full **3×3** of mature
crops and replants each block from its own drops. Immature crops are skipped and Fortune
applies per block. The **iron rake** is its planting mirror: hold the rake with seed in
your off-hand and one right-click sows a 3×3 of farmland. And with an **empty hand**,
right-clicking a mature crop harvests and replants it in place.

### Farmers who farm

Villager farmers live by the same soil: their harvests drain fertility, they rotate
crops when a plot tires, and they leave exhausted ground fallow until it recovers.
Nothing about trades, prices, or reputation changes.

## Installation

**Requirements:** Minecraft 1.21.1, Fabric Loader 0.16.10+, Fabric API, Java 21

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
3. Drop `cultivation-<version>.jar` into `mods/` as well.

Cultivation goes on **both** the server and every client — soil, harvests, and diet are
server-side, while the soil overlays and the fatigue/nutrition tooltips are not.
Optionally add [Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game settings screen,
and [Jade](https://modrinth.com/mod/jade) or [WTHIT](https://modrinth.com/mod/wthit) to
read fertility from the crosshair.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/cultivation soil` | 0 | Fertility, band, last crop and bonuses of the block you are looking at |
| `/cultivation diet` | 0 | Your current food fatigue |
| `/cultivation field` | 0 | A summary of the surrounding field |
| `/cultivation soil set …` | 2 | Sets soil state, for testing |
| `/cultivation diet reset` | 2 | Clears diet fatigue |
| `/cultivation reload` | 2 | Reloads the config and re-syncs every client |

## Configuration

Config generates at `config/cultivation.json` on first launch, and every feature is
independently toggleable — from Mod Menu / Cloth Config, or by hand. `/cultivation
reload` applies changes without a restart and re-broadcasts them to connected clients.

Server-authoritative keys cover each system (`enableSoilFertility`, `enablePolyculture`,
`enableDietaryFatigue`, `enableMealBuffs`, `enableEnrichedTilling`, `enableFertilizer`,
`enableScytheHarvest`, `enableBroadcastSowing`, `enableVillagerStewardship`, …) alongside the tuning numbers
(`harvestDrain`, `rotationDrainMultiplier`, `fallowRecoveryPerRandomTick`,
`polycultureGrowthMultiplier`, `fatiguePerRepeat`, `fatigueFloor`,
`fertilizerDoseHarvests`, …). The presentation keys are client-only and never synced:
`showSoilOverlays`, `showFatigueTooltips`, `showNutritionTooltips`, and
`soilOverlayRenderDistance`. An out-of-range value is clamped with a warning in the log
rather than rejected.

---

## For Mod Developers

Cultivation publishes a stable, read-only integration surface under
`com.rfizzle.cultivation.api`, conforming to the
[Concord API Standard](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Use it as a soft dependency: compile against the mod with `modCompileOnly` and
guard every call with `FabricLoader.isModLoaded("cultivation")`. Everything
outside the `api` package is internal and may change in any release. Reads are
server-authoritative; Cultivation has no HUD slot by design, so the surface
ships no HUD accessors, and it exposes no soil mutators — outside mods observe
fertility and react, they don't write it.

### Gradle setup

```gradle
repositories {
    // Sibling jars resolve from GitHub Releases through an artifact-only `rfizzle:` ivy
    // repo while the Modrinth projects are not publicly resolvable. See API-STANDARD §4.
    ivy {
        name = 'GitHubReleases'
        url = 'https://github.com'
        patternLayout {
            artifact '/[organisation]/[module]/releases/download/v[revision]/[module]-[revision].jar'
        }
        metadataSources { artifact() }
        content { includeGroup 'rfizzle' }
    }
}

dependencies {
    modCompileOnly "rfizzle:cultivation:<version>"
}
```

Add `"cultivation": "*"` under `suggests` in your `fabric.mod.json` — never `depends`,
and never a version floor.

### The stable surface

`CultivationAPI` accessors:

- `getFertility(ServerLevel, BlockPos)` — fertility 0–100; 100 for untracked farmland; −1 if the block is not farmland.
- `getSoilInfo(ServerLevel, BlockPos)` — optional record of fertility, enriched chance, remaining Fertilizer dose, and last crop.
- `getFoodEffectiveness(ServerPlayer, ItemStack)` — the multiplier the player's next eat of this item would receive, from the configured `fatigueFloor` up to 1.0.

Array-backed Fabric events (server-side):

- `CultivationHarvestCallback` — fires from the single harvest choke point after Cultivation's own drain and bonuses. Its drops list is mutable: the sanctioned point for other mods to inject bonus produce.
- `CultivationFoodCallback` — fires after a food is consumed and fatigue applied, with the effectiveness that was used. Observation only.

### Usage example

```java
if (FabricLoader.getInstance().isModLoaded("cultivation")) {
    float fertility = com.rfizzle.cultivation.api.CultivationAPI.getFertility(serverLevel, pos);
}
```

Stable item IDs for datapack integrations: `cultivation:fertilizer`,
`cultivation:iron_scythe`, `cultivation:diamond_scythe`,
`cultivation:netherite_scythe`, `cultivation:iron_rake`. The scythes carry a
`#cultivation:scythes` tag alongside `#minecraft:enchantable/durability` and
`#minecraft:enchantable/mining`; the rake carries `#cultivation:rakes` and
`#minecraft:enchantable/durability`.

Full reference: [site/pages/developers.json](site/pages/developers.json).

---

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a modular collection of system
overhauls. Install any, combine all.

- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.
- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Respite](https://respite.rfizzle.com) — Make the night count.
- [Distillation](https://distillation.rfizzle.com) — Every drop counts.

With none of them installed, nothing here is missing.

---

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Cultivation is not
affiliated with Mojang Studios or Microsoft.
