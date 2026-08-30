# Cultivation — Agriculture Overhaul

**_Worth growing._**

![Cultivation logo](https://raw.githubusercontent.com/rfizzle/cultivation/master/art/logo.png)

**Also on [Modrinth](https://modrinth.com/mod/cultivation-agriculture-overhaul)
and [GitHub Releases](https://github.com/rfizzle/cultivation/releases).**
Visit the [website](https://cultivation.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Cultivation is an agriculture overhaul for **Minecraft 1.21.1 (Fabric)** —
soil, harvests, and the food you live on. Vanilla farming is solved minutes
into a world: a nine-block wheat square and golden carrots forever. Cultivation
makes farming a practice instead of a chore: soil that tires under harvest and
rewards rotation, fields worth planning, food worth varying — all on vanilla
crops and vanilla blocks.

**In development.** The design and full behavioral spec are committed and
features are being built against them; this page describes the first release.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- Every feature independently tunable through `config/cultivation.json` —
  hot-reload with `/cultivation reload`.
- MIT licensed.

## Features

### Living Soil

Every farmland block carries its own fertility. Harvesting drains it about
**3%** a time — half that when you rotate crops — and a fallow block recovers
fully in about **three in-game days**. Tired soil (below 25%) grows **25%
slower** and turns visibly pale and cracked; exhausted soil grows at half speed
and yields the bare minimum. Bone meal on empty farmland restores a quarter
instantly, and `/cultivation soil` reads any block exactly.

### Polyculture

A crop bordered by two different crops grows **20% faster**. Alternating rows —
wheat, carrots, potatoes — beat the monoculture slab, which is never punished,
just outgrown. A **sniffer** in the field doubles that bonus, and crops within
**8 blocks of a beehive** grow a further **10% faster** — so a farm that keeps
bees and a sniffer is a farm that compounds.

### A Varied Table

Eating the same food repeatedly restores **10% less** per repeat, down to a
**50% floor**; three different foods reset it entirely. Golden carrots stay
excellent — they just stop being the only answer. Food effects are never
touched, and the tooltip tells you plainly when a food is losing its appeal.

### Worth the Bowl

The unstackable meals finally repay the bowl: **Rabbit Stew** grants 5%
movement speed, **Beetroot Soup** 10% faster block breaking, **Mushroom Stew**
10% slower hunger drain — each for 2 minutes — and **Suspicious Stew** rolls
one of the three at double strength on top of its usual gamble. **Pumpkin
pie** and **cookies** join in smaller — each carries one of those buffs for a
single minute, sized below the crafted bowls. **Cake** is the celebration
meal: each slice grants all three buffs at once for a minute.

### Enriched Tilling & Fertilizer

Till with a **diamond** hoe and that block gains a permanent **10%** chance of
an extra drop per harvest (**netherite: 15%**). The composter produces
**Fertilizer** instead of bone meal — one dose covers a farmland block's next
**15 harvests** with a guaranteed **+1 crop** each, and re-dosing keeps the
composter loop alive for the life of the farm. Enrichment lasts until the
block reverts to dirt, and the two stack.

### The Scythe and the Rake

A new tool in iron, diamond, and netherite: one swing of the **scythe** harvests
a full **3×3** of mature crops and replants each block from its own drops.
Immature crops are skipped, Fortune applies per block, and large-scale manual
farming becomes rhythm instead of tedium.

The **iron rake** is its other half: hold the rake and put seed in your
off-hand, and one right-click sows a **3×3** of farmland at once — one seed and
one durability point per block actually planted, occupied ground skipped. So
replanting a field is one action instead of nine.

And with an **empty hand**, right-clicking a mature crop harvests and replants
it in place, through the same drain and bonuses as every other harvest — no
more break-and-replant on every block.

### Farmers Who Farm

Villager farmers live by the same soil: their harvests drain fertility, they
rotate crops when a plot tires, and they leave exhausted ground fallow until it
recovers. Village fields become rotating patchworks — and nothing about trades,
prices, or reputation changes.

## Commands

Player commands: `/cultivation soil` — fertility, band, last crop, and bonuses
of the block you're looking at; `/cultivation diet` — your current food
fatigue. Operator commands cover config reload and soil/diet testing levers.
Full reference:
[cultivation.rfizzle.com/commands.html](https://cultivation.rfizzle.com/commands.html)

## Optional integrations

Cultivation detects and integrates with these mods when present. **None are
bundled** — install whichever you already use.

- [Mod Menu](https://www.curseforge.com/minecraft/mc-mods/modmenu) — config screen entry
- [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) — settings GUI
- [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) / [WTHIT](https://www.curseforge.com/minecraft/mc-mods/wthit)
  — farmland fertility and crop growth modifiers at a glance
- [EMI](https://www.curseforge.com/minecraft/mc-mods/emi) / [REI](https://www.curseforge.com/minecraft/mc-mods/roughly-enough-items) /
  [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — scythe and rake recipes, and Fertilizer source info

**Enhanced by** its Concord siblings, never required: with
[Meridian](https://www.curseforge.com/minecraft/mc-mods/meridian-enchanting-overhaul) scythes and
hoes become first-class enchanting targets; with
[Mercantile](https://www.curseforge.com/minecraft/mc-mods/mercantile-villager-overhaul) high
reputation puts Fertilizer on a farmer's counter; with
[Prosperity](https://www.curseforge.com/minecraft/mc-mods/prosperity-loot-overhaul) far-flung
chests can hold Fertilizer caches and rare seeds; with Tribulation a hard
world makes a varied pantry real preparation; with Distillation your crops
feed the still.

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API
- Java **21+**
