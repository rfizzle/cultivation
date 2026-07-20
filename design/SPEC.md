# Cultivation — Feature Spec

Minecraft 1.21.1 Fabric mod. Agriculture overhaul.

**Architectural philosophy:** Vanilla-soil overlay. Cultivation never registers a custom farmland or crop block, never replaces block entities, and never touches world generation or structures. Per-block soil state lives in a chunk-scoped persistent Fabric data attachment keyed by in-chunk position, so every field in the world — player-made or village-generated — stays byte-identical vanilla blocks with a data layer beside them. Behavior changes flow through a small set of choke-point seams (crop growth speed, crop drop resolution, food consumption, the farmer work task), each entered by one mixin or event handler. New registered content is items (Fertilizer, three scythes) and three status effects — nothing block-shaped. All gameplay decisions are server-side; the client receives render-only sync (soil overlays, diet tooltips).

**Asset philosophy:** Blocks and their textures stay vanilla — soil condition is communicated by client-side overlay quads over vanilla farmland, never by retexturing or swapping the block, which keeps full compatibility with Sodium, EBE, and shaders. New items and status effects get custom pixel art through Concord's glyph pipeline (concord `design/DESIGN-SYSTEM.md` §8, sources beside masters — see `design/DESIGN.md` §3 and `design/ASSETS.md`). Sounds stay vanilla throughout v1: every cue this mod needs (tilling, bone meal, composter, sweeping, crop breaks) is organic foley vanilla already nails (§9 of the design system; see Sound Design below).

---

## 1. Soil Fertility

Per-block farmland fertility that drains under harvest and recovers when rested. The core system every other soil feature keys off.

### Problem

Vanilla farmland has exactly two states: farmland or not. A nine-block wheat square produces identical output forever with zero attention, so farming collapses into a solved, AFK-able chore minutes into a world. There is no reason to rotate, rest, or care about any particular plot.

### Soil State Model

Every farmland block can carry a **SoilData** record in its chunk's soil attachment:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `fertility` | float | 100.0 | 0–100, clamped |
| `lastCrop` | block id, optional | empty | the crop most recently harvested here (rotation memory) |
| `enrichedChance` | int | 0 | §5 bonus-drop chance in percent (0, 10, or 15) |
| `fertilizerRemaining` | int | 0 | §6 harvests left on the current Fertilizer dose (0 – `fertilizerDoseHarvests`) |
| `lastRecoveryCheck` | long | — | game time of the last recovery accrual (lazy-recovery bookkeeping) |

An **absent entry means pristine defaults** (fertility 100, no memory, no bonuses) — a world where nobody farms carries zero Cultivation data. An entry is created on first write (harvest, high-tier till, fertilize) and evicted when it returns to all-default values.

Display bands, used by the command, Jade/WTHIT, and the overlay:

| Band | Fertility | Growth multiplier | Other effects |
|---|---|---|---|
| Rich | 75 – 100 | 1.0× | — |
| Fair | 25 – 74.99 | 1.0× | — |
| Tired | 0 < f < 25 | 0.75× | pale-crack overlay; villagers stop replanting (§8) |
| Exhausted | 0 | 0.5× | heavy-crack overlay; yield clamped to bare minimum; §5/§6 bonuses suppressed |

### Behavior — Drain

When a **mature crop is harvested** (its block is destroyed with drops — see Supported Crops for what counts), the farmland directly below drains:

1. Resolve the crop's block id `X` and the block's SoilData (creating it if absent).
2. Drain: `fertility -= (X == lastCrop) ? harvestDrain : harvestDrain * rotationDrainMultiplier` — defaults **3.0** same-crop, **1.5** rotated (a first-ever harvest counts as rotated). Clamp at 0.
3. Set `lastCrop = X`.

Drain is **actor-agnostic**: player breaks, scythe sweeps (§7), bare-hand right-click harvests (§7), villager harvests (§8), pistons, water, and explosions that destroy a mature crop with drops all drain identically. Breaking an **immature** crop never drains. Creative-mode breaks produce no drops and never drain.

At default values, a monoculture block sustains 33 harvests from full to exhausted; strict two-crop rotation doubles that.

### Behavior — Recovery

A block is **fallow** when it holds no crop above it. Fallow ground recovers by two equivalent paths:

- **Live path:** farmland receives vanilla random ticks; each random tick on fallow farmland adds `fallowRecoveryPerRandomTick` fertility (default **2.0**). At default `randomTickSpeed` 3, a block expects ~17.6 random ticks per in-game day ≈ **35 fertility/day** — full recovery from 0 in **just under 3 in-game days**. While rain falls on the block (raining and sky-exposed at the position above it), each random tick's gain is multiplied by `rainRecoveryMultiplier` (default **2.0**) — a rained-on fallow block recovers from 0 in about **1.5 in-game days**.
- **Lazy path:** ground that is not currently farmland receives no random ticks, so when a tracked position is next touched (re-tilled, inspected, harvested), accrued fallow time is settled first: `fertility += fallowRecoveryPerRandomTick × elapsedTicks × randomTickSpeed / 4096`, where `elapsedTicks` counts from `lastRecoveryCheck`, only over the span the position was crop-free. `lastRecoveryCheck` advances on every settle, drain, and live-path tick.

The two paths use the same base formula, so reverting farmland to dirt and re-tilling recovers **exactly as fast** as leaving farmland fallow — re-tilling is never a shortcut around resting the soil. The lazy path never applies the rain multiplier: weather history is not replayed, only live random ticks see rain.

- **Bone meal amendment:** using bone meal on a fallow farmland block (the block itself, not a crop) restores **+25** fertility, consumes the item, and plays the vanilla bone-meal particles and sound. At fertility 100 the use fails: no effect, item not consumed. Bone meal used on a crop is untouched vanilla growth behavior.

### Behavior — Effects of Fertility

- **Growth:** the crop's computed growth speed is multiplied by the band's growth multiplier (table above), combined multiplicatively with the polyculture and bee-pollination bonuses (§2).
- **Exhausted yield clamp:** when a mature crop is harvested over fertility-0 farmland, after loot resolves, the drops are reduced to the bare minimum — the crop's primary product capped at 1 total, its seed item (where distinct from the product) capped at 1 total, and the §5/§6 bonus drops suppressed entirely.

### Supported Crops

| Crop block | Mature at | Primary product | Seed item |
|---|---|---|---|
| `minecraft:wheat` | age 7 | Wheat | Wheat Seeds |
| `minecraft:carrots` | age 7 | Carrot | Carrot |
| `minecraft:potatoes` | age 7 | Potato | Potato |
| `minecraft:beetroots` | age 3 | Beetroot | Beetroot Seeds |
| `minecraft:torchflower_crop` | age 2 (becomes torchflower) | Torchflower | Torchflower Seeds |
| `minecraft:pitcher_crop` | age 4 | Pitcher Plant | Pitcher Pod |

Melon and pumpkin **stems** are affected by the growth-speed modifiers (fertility and polyculture) but never drain and never receive yield bonuses — the fruit grows beside the farmland, not on it, and is exempt. Cocoa, sugar cane, and cactus stay out of scope. Nether wart and sweet berries grow on their own ground rather than farmland and are drawn into living soil as **second-wave crops** (below).

### Second-wave crops (non-farmland soil)

Nether wart (on soul sand) and the sweet berry bush (on any `minecraft:dirt`-tag block) tire and recover their ground on the same fertility model as farmland. Soil state keys on the ground block directly below the crop; the chunk attachment is positional, so soul sand and dirt hold a `SoilData` entry exactly as farmland does — no new state shape, no migration.

- **Drain.** Nether wart drains on the break (its only harvest). The sweet berry bush drains on each **pick** — the age ≥ 2 right-click that pops berries and resets the bush to age 1 — and on a break that yields berries. Every one routes through the single harvest choke point, so drain, the exhausted yield clamp, and `CultivationHarvestCallback` apply exactly as for a farmland crop, using the same `harvestDrain`/`rotationDrainMultiplier` numbers. The exhausted clamp keeps at most one berry / one wart.
- **Growth.** Tired and Exhausted ground slows these crops by the same fertility band multiplier (`tiredGrowthMultiplier`/`exhaustedGrowthMultiplier`), applied by widening their own random-tick growth roll. Polyculture and bee pollination stay a farmland-row mechanic and do **not** apply — these crops receive the fertility band alone.
- **Recovery.** Their ground is never farmland, so it recovers on the lazy fallow-accrual path (`fallowRecoveryPerRandomTick`), settled on the next harvest read — no live per-random-tick hook and no rain multiplier (moot in the Nether; a deliberate simplification for berry dirt). A bush's soil recovers continuously between picks rather than only when cleared, which keeps a persistent bush from ratcheting straight to exhaustion.
- **Investment.** Enriched tilling and Fertilizer dosing do **not** extend to these crops — soul sand and dirt cannot be tilled, so `enrichedChance`/`fertilizerRemaining` stay 0 and their harvest bonuses are inert.
- **Reap-and-replant tools.** The scythe (§7) and bare-hand right-click harvest (§7) act only on farmland replant crops and pass a bush or wart through untouched — a bush is picked, never destroyed-and-replanted.
- **Surfaces.** `/cultivation soil`, `/cultivation field`, Jade/WTHIT, and the `com.rfizzle.cultivation.api` fertility reads resolve the ground under a looked-at wart or bush. The in-world crack overlay renders on the ground block's top face where visible beneath the crop; a wart position (harvested by the break, so the crop is already gone at the drain write) refreshes its overlay on the next chunk-load pull rather than as a live delta.
- **Toggle.** `enableNonFarmlandSoil` (default true) gates all of the above; off, both crops behave as vanilla and farmland is unaffected.

### Visual Feedback

- **Tired** blocks render a faint pale-crack overlay on the farmland top face; **Exhausted** blocks render a heavier version. Rich and Fair farmland renders no overlay — healthy hydrated farmland is already visibly dark in vanilla.
- **Investment overlays:** a block with an active Fertilizer dose (`fertilizerRemaining` > 0) renders a sparse dark compost-fleck overlay; an enriched block (`enrichedChance` > 0) renders a sparser warm loam fleck (no visual distinction between 10% and 15% — Jade and the command carry the number). Investment overlays compose with the crack overlays — a Tired, fertilized block shows both.
- Overlays are flat textured quads 1 pixel above the top face, rendered client-side via `WorldRenderEvents.LAST` within `soilOverlayRenderDistance` (default **24** blocks), depth-tested (no through-wall rendering).
- **Sync:** the client requests soil overlay data per chunk on chunk load; the server responds with only the visually deviating positions (packed in-chunk pos + 4 flag bits: 2-bit band, 1-bit dose-active, 1-bit enriched). The server pushes a delta when a tracked block's flags change — band boundary crossed, dose started or ran out, enrichment set or cleared, farmland removed. Non-deviating blocks are never synced. The client re-pulls every loaded chunk, nearest first, whenever a rule behind the overlays moves mid-session — `showSoilOverlays`, `enableSoilFertility`, `tiredThreshold`, or `enableNonFarmlandSoil` — so a toggle or a `/cultivation reload` never leaves loaded chunks showing the previous rules.

### Edge Cases

- **Farmland reversion** (trampling, shoveling, breaking, block replaced): `enrichedChance` and `fertilizerRemaining` reset to defaults immediately; `fertility`, `lastCrop`, and recovery bookkeeping persist at the position, so soil memory survives the block and applies to whatever farmland is tilled there later. Enriched farmland resists a player's trample and so never reverts that way (§5); a resisted trample changes nothing at the position.
- **Pistons:** soil state is positional and never moves with a pushed block. The state at a vacated position remains and governs future farmland there. Accepted: the soil is the ground, not the block.
- **Multiplayer:** soil is shared world state, server-authoritative — every player sees and works the same fertility, exactly like vanilla hydration. No per-player soil.
- **Chunk lifecycle:** the attachment persists with the chunk. A corrupted or unreadable soil entry is dropped and logged; the block silently returns to pristine defaults.
- **`/fill`, world editing:** blocks placed or removed by commands follow the same rules as any block change (reversion clears bonuses); no special handling.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableSoilFertility` | bool | true | — |
| `harvestDrain` | float | 3.0 | 0–100 |
| `rotationDrainMultiplier` | float | 0.5 | 0–1 |
| `fallowRecoveryPerRandomTick` | float | 2.0 | 0–100 |
| `rainRecoveryMultiplier` | float | 2.0 | 1–10 |
| `boneMealFertilityRestore` | float | 25.0 | 0–100 |
| `tiredThreshold` | float | 25.0 | 0–100 |
| `tiredGrowthMultiplier` | float | 0.75 | 0–1 |
| `exhaustedGrowthMultiplier` | float | 0.5 | 0–1 |
| `enableNonFarmlandSoil` | bool | true | — |

`enableSoilFertility=false` freezes the system: no drain, no recovery, no overlays, all growth multipliers 1.0, exhausted clamp off. Stored SoilData is retained untouched (re-enabling resumes where the world left off; the fallow clock does not accrue while disabled). `enableNonFarmlandSoil=false` reverts only the second-wave crops (nether wart, sweet berries) to vanilla and leaves farmland soil untouched.

### Implementation Notes

- Chunk attachment `SoilStore`: a persistent Fabric data attachment on `LevelChunk` holding `Map<Integer, SoilData>` keyed by packed in-chunk position (`x | z << 4 | y << 8` section-relative packing at the implementer's discretion), with a codec for chunk NBT persistence. One helper mediates every read/write and handles entry creation, default eviction, lazy-recovery settling, and chunk dirtying.
- Drain + yield choke point: a mixin on the crop-drop resolution path (`Block#dropResources` filtered to supported mature crops, or an equivalent single seam) so players, villagers, scythes, explosions, and pistons all flow through one handler — drain, exhausted clamp, §5/§6 bonuses, and the `CultivationHarvestCallback` (see Public API) fire here, in that order.
- Growth modifier: a mixin on `CropBlock`/`StemBlock`/`PitcherCropBlock` random-tick growth that multiplies the effective growth speed by the combined fertility × polyculture modifier.
- Second-wave crops: `NetherWartBlock`/`SweetBerryBushBlock` random-tick mixins widen the vanilla growth roll (`RandomSource#nextInt`) by the fertility band alone; the sweet-berry pick wraps `useWithoutItem`'s `popResource` to route the popped berries through the same drop choke point. A single crop-keyed predicate (`SupportedCrops.isTrackedSoilGround`, carrying the `enableNonFarmlandSoil` toggle) replaces the farmland-only gate across the drain seam, overlay sync, command, probe, and API; `SupportedCrops.soilProfile` is the drain registry (farmland crops + wart + berries) while `matureProfile` stays the replant registry (farmland crops only).
- Recovery: `FarmlandBlock#randomTick` mixin for the live path; the lazy path runs inside the `SoilStore` helper on first touch and is the only recovery the second-wave grounds receive.
- Bone meal amendment: a `UseItemCallback`/`UseBlockCallback` handler on bone meal targeting fallow farmland, running before vanilla item behavior.
- Overlay renderer: client class rendering band quads from a per-chunk cache; packets `SoilBandsS2C` (chunk response) and `SoilBandDeltaS2C` (change push).

---

## 2. Polyculture Bonus

Mixed fields grow faster than monoculture.

### Problem

Vanilla growth mechanics are indifferent to field layout, so the optimal farm is a maximal monoculture slab. There is no mechanical reason for the varied, striped fields real farming — and interesting building — produces.

### Behavior

When a supported crop (or stem) rolls a growth tick, count its four cardinal neighbors at the same Y that are supported crops or stems of a **different block id**. If that count is **≥ `polycultureMinDifferentNeighbors`** (default **2**), the block's growth speed is multiplied by `polycultureGrowthMultiplier` (default **1.2×**).

- Evaluated live at each growth roll — four block reads, no caching, no stored state.
- Stacks multiplicatively with fertility: an exhausted polyculture block grows at 0.5 × 1.2 = 0.6×.
- Layout math: in alternating single rows (wheat / carrot / potato), every interior block has exactly 2 different-crop neighbors — the whole row qualifies. A two-crop checkerboard qualifies everywhere. A monoculture field has 0 different neighbors and simply grows at the vanilla rate — never penalized.

**Sniffer premium.** A qualifying block whose different-crop neighbors include a **sniffer crop** — torchflower (either growth stage) or pitcher plant — earns the premium partner bonus while `enableSnifferPolyculture` is true: the polyculture *bonus fraction* above 1.0 is scaled by `snifferPolycultureBonusMultiplier` (default **2×**), so the standard 1.2× becomes 1.4×. It is the fraction that doubles, not the raw multiplier — the +20% becomes +40%, never +140%. The premium only applies to a block that already qualifies (`different ≥ polycultureMinDifferentNeighbors`); a lone sniffer neighbor never conjures a bonus. Positive-only: the factor is floored at 1.0 by its clamp, so a sniffer border can only speed a row up. This gives the sniffer's rare finds a job — the crown jewels of a striped field — without touching the animal side (sniffer breeding, egg-finding, and behavior stay vanilla).

### Edge Cases

- **Field edges:** the end-cap blocks of the two outermost rows have only 1 different-crop neighbor and miss the bonus. Accepted — interior dominates, and the rule stays four block reads.
- **Mixed maturity:** neighbor maturity is irrelevant; only block id is compared. A just-planted neighbor counts.
- **Stems:** an attached stem keeps its base stem's id for comparison; melon and pumpkin stems count as two distinct crops.
- **Sniffer maturity:** torchflower and pitcher are the two crops whose maturity changes their block id — the mature torchflower flower keeps `torchflower_crop` and the two-tall pitcher plant keeps `pitcher_crop`. Neither drops out of a field's neighbor counts by finishing, so both growth stages of each count as a sniffer neighbor for the premium (and as ordinary polyculture neighbors).
- **Sniffer self:** a sniffer crop earns the premium only from a *different* sniffer or crop bordering it — a torchflower row beside an identical torchflower row is monoculture and qualifies for nothing. The premium rewards the row the sniffer borders.
- **Multiplayer:** none — layout is world geometry, identical for everyone.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enablePolyculture` | bool | true | — |
| `polycultureGrowthMultiplier` | float | 1.2 | 1–5 |
| `polycultureMinDifferentNeighbors` | int | 2 | 1–4 |
| `enableSnifferPolyculture` | bool | true | — |
| `snifferPolycultureBonusMultiplier` | float | 2.0 | 1–5 |

### Implementation Notes

- Lives entirely inside §1's growth-modifier mixin: a static helper computes the neighbor count and returns the multiplier. No attachment, no sync, no persistence.
- The sniffer premium rides the same four-block neighbor scan — the same states are already read, so it adds no world reads. The scan yields both the different-crop count and whether any different neighbor is a sniffer crop; the pure premium function folds the two configs in.

### Bee Pollination

Crops near a populated beehive grow faster — vanilla already sends bees to the field, so Cultivation reads that signal instead of ignoring it.

#### Behavior

When a supported crop (or stem) rolls a growth tick, a **populated** beehive or bee nest within `beePollinationRange` (default **8** blocks) multiplies its growth speed by `beePollinationGrowthMultiplier` (default **1.1×**). The factor stacks multiplicatively with the fertility band (§1) and polyculture — a healthy alternating-row crop beside a hive grows at 1.2 × 1.1 = 1.32×.

- **"Populated"** means the hive's block entity currently houses at least one bee (`BeehiveBlockEntity` occupant count > 0). An empty hive — decorative, or one whose bees have all left — grants nothing.
- **Hive lookup** rides vanilla's own `bee_home` POI index (`PoiManager.getInRange`, the same query vanilla bee AI uses), so there is no block scan, no stored state, and no sync. Occupancy is confirmed only on the POI records the index returns, not on every block in range.
- **Read-only on the bee.** The effect lands entirely on the crop; the bee's behavior, breeding, and health are never touched — the animal side of the silo stays Instinct's. Vanilla's own pollination visuals are the only feedback.

#### Edge Cases

- **Foraging flicker:** occupant count dips while bees are out foraging by day and rises when they return. Because growth is evaluated per random tick (already probabilistic), a hive that flickers empty for part of a day averages out imperceptibly — the same noise polyculture already tolerates.
- **Range measurement:** the POI query is spherical (Euclidean), measured from the crop, so a hive above or below counts if it is within radius.
- **Multiplayer:** none — hive presence and occupancy are world state, identical for everyone.

#### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableBeePollination` | bool | true | — |
| `beePollinationGrowthMultiplier` | float | 1.1 | 1–5 |
| `beePollinationRange` | int | 8 | 1–16 |

#### Implementation Notes

- Lives in the same growth-modifier seam as §1–§2: a static helper (`BeePollination`) gated by config runs the POI query and returns the multiplier. No attachment, no sync, no persistence — the POI index it reads is vanilla's own.
- The POI query's cost scales quadratically in `beePollinationRange`: it spans a `floor(range / 16) + 1` chunk radius, so range 8 sweeps a 3×3 chunk column and range 16 a 5×5. The query short-circuits on the first populated hive and runs only when the feature is enabled and a crop actually rolls a growth tick; admins raising the range on large servers should weigh that scaling.

---

## 3. Dietary Fatigue

Eating the same food repeatedly restores less; a varied diet resets it.

### Problem

Vanilla food is solved the moment one stackable, efficient food is secured — golden carrots or steak forever. Every other crop and dish is dead content because there is no mechanical reason to vary what you eat.

### Behavior

Each player carries a persistent **DietData** attachment: a fatigue-stack map `Map<Item, int>` and a history of the last 3 foods eaten (by item id, in order, repeats included).

On every food consumption, server-side, in order:

1. **Effectiveness:** `eff = max(fatigueFloor, 1.0 − fatiguePerRepeat × stacks[item])` — defaults floor **0.5**, per-repeat **0.10**. A never-fatigued food eats at 100%; the 5th repeat and beyond eat at 50%.
2. **Apply:** nutrition restored becomes `max(1, round(nutrition × eff))` (a food that restores ≥ 1 hunger never drops to 0); saturation restored becomes `saturation × eff`. **Food effects are never touched** — golden apple absorption, suspicious stew rolls, and §4 meal buffs apply at full strength regardless of fatigue.
3. **Record:** `stacks[item] += 1` (capped at the stack count that reaches the floor); append the item to the history.
4. **Reset check:** if the last `fatigueResetDistinctFoods` (default **3**) eats are 3 **distinct** items, clear the entire stack map (including the entry just incremented) and the history.

Consequences of the model: a strict single food decays 100 → 90 → 80 → 70 → 60 → 50%. A two-food alternation never triggers the reset and both foods grind down to the floor together. A three-food rotation resets on every bite and always eats at 100%.

### Scope

Every item with a food component, including honey bottles and all golden-apple variants (each a distinct item). Eating a slice of cake counts as eating the `minecraft:cake` item. Suspicious stews are all one item regardless of rolled effect. Potions and milk are not food and are ignored.

### Feedback

- Food item tooltips (client, when `showFatigueTooltips` is on) show a line once the item has any stacks: *"Losing its appeal (−20%)"*, and at the floor: *"Thoroughly tired of this (−50%)"* — keys under `tooltip.cultivation.fatigue.*`.
- Food item tooltips (client, when `showNutritionTooltips` is on) show a nutrition line above the fatigue line: hunger and saturation from the item's `FOOD` component. When the food currently carries fatigue, the line leads with the fatigue-adjusted values for this bite and keeps the base in parentheses — *"Hunger 6 (8), Saturation 9.6 (12.8)"* — using the same `scaledNutrition` / `saturation × effectiveness` math the server applies; otherwise the base values stand alone — *"Hunger 8, Saturation 12.8"*. Keys under `tooltip.cultivation.nutrition.*`. Suppressed when AppleSkin is installed, which draws its own hunger/saturation shanks (the fatigue line, having no AppleSkin counterpart, still shows).
- `/cultivation diet` prints the player's current fatigue entries and last three foods.

### Edge Cases

- **Death** clears DietData entirely — a fresh start.
- **Persistence:** DietData survives relog and world restart (persistent player attachment).
- **Multiplayer:** strictly per-player; one player's diet never affects another.
- **Client sync:** the server pushes the owner's DietData on join and on change (`DietSyncS2C`), read only for tooltips; all restoration math is server-side.
- **Disabled mid-world:** `enableDietaryFatigue=false` suspends application and accumulation; stored stacks are retained but inert.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableDietaryFatigue` | bool | true | — |
| `fatiguePerRepeat` | float | 0.10 | 0–1 |
| `fatigueFloor` | float | 0.5 | 0–1 |
| `fatigueResetDistinctFoods` | int | 3 | 2–5 |

Client: `showFatigueTooltips` (bool, default true), `showNutritionTooltips` (bool, default true).

### Implementation Notes

- Persistent player attachment `DietData` with codec; cleared via `ServerPlayerEvents.AFTER_RESPAWN` (death only, not dimension change).
- Application seam: a mixin at the point the consumed `FoodProperties` reach `FoodData` (e.g. `FoodData#eat`), scaling nutrition/saturation before they land; the cake-block path routes through the same helper with `Items.CAKE`.
- Fires `CultivationFoodCallback` (Public API) after application.

---

## 4. Meal Buffs

Crafted bowl foods grant a short passive buff.

### Problem

Vanilla's crafted meals (stews and soups) cost crafting effort but restore no more than cheap stackables, and each one hogs a full inventory slot — so nobody crafts them. The recipes exist; the reason doesn't.

### Behavior

Three new status effects, all beneficial, with custom 18×18 icons (see `design/ASSETS.md`):

| Effect | Id | Per amplifier level | Implementation surface |
|---|---|---|---|
| **Nimble** | `cultivation:nimble` | +5% movement speed | `generic.movement_speed` attribute modifier, multiply-total |
| **Diligent** | `cultivation:diligent` | +10% block breaking speed | `player.block_break_speed` attribute modifier, multiply-total |
| **Sated** | `cultivation:sated` | −10% hunger drain | exhaustion accrual scaled by `1 − 0.10 × (amplifier + 1)` |

Granted on consumption, duration `mealBuffDurationTicks` (default **2400** = 2 minutes) for the stews, `snackBuffDurationTicks` (default **1200** = 1 minute) for pumpkin pie and cookies, and `cakeBuffDurationTicks` (default **1200** = 1 minute) for cake:

| Food | Buff |
|---|---|
| Rabbit Stew | Nimble I (+5% speed) |
| Beetroot Soup | Diligent I (+10% break speed) |
| Mushroom Stew | Sated I (−10% hunger drain) |
| Cookie | Nimble I (+5% speed) — snack duration; a lighter, briefer Rabbit Stew |
| Pumpkin Pie | Sated I (−10% hunger drain) — snack duration; a lighter, briefer Mushroom Stew |
| Suspicious Stew | one of the three, uniformly random, at level II (double strength) — in addition to its vanilla rolled effect, which is untouched |
| Cake (each slice) | all three — Nimble I + Diligent I + Sated I — the celebration meal; placed, sliced, shared |

Pumpkin pie and cookies sit below the stews: they reuse the stews' effects at level I but on the shorter snack register, so a stew stays the premium source of its buff. Cake keeps the crown as the only meal that hands over the whole trio.

**One meal at a time:** consuming any of these buffed foods first removes all three Cultivation effects, then applies the new grant (the stews and snacks a single buff, cake its trio together). Buffs replace; they never stack or extend.

**Stacking:** the four crafted bowl foods — rabbit stew, beetroot soup, mushroom stew, suspicious stew — stack to **16**, so a shelf of meals fits a hotbar. Eating one returns its empty bowl exactly as vanilla; bowls already stack. Suspicious stew stacks only across identical rolled effects, per component equality — distinct rolls never merge. Stacking is part of the meal-buff feature and rides the `enableMealBuffs` toggle; because stack size is baked into the item at startup, the toggle is read once at init and takes effect on restart. Stack size is an item-prototype property both physical sides read locally, so client and server should run matching `enableMealBuffs` — a mismatch is cosmetic (the server stays authoritative and vanilla already tolerates over-max stacks), not a save or crash concern.

### Edge Cases

- **Returned bowl on a full inventory:** eating a stew from a stack of two or more with no inventory room drops the empty bowl at the player's feet rather than discarding it — a targeted wrap on `Player#eat`'s inventory-add, mirroring vanilla `HoneyBottleItem` (vanilla silently loses the bowl in this newly reachable stack-`>1` path).
- **Milk** clears the effects (vanilla behavior, accepted).
- **Cake** is eaten by the slice from the placed block; each slice grants the full trio to whoever ate it. Candle-cake variants count as cake. The slice-eat path and §3's cake-fatigue handling share one seam.
- **Dietary fatigue** (§3) reduces the meal's hunger/saturation but never the buff.
- **Multiplayer:** per-player vanilla status effects; nothing shared.
- **Totem/respawn:** effects are lost on death like any vanilla effect.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableMealBuffs` | bool | true | — |
| `mealBuffDurationTicks` | int | 2400 | 200–72000 |
| `cakeBuffDurationTicks` | int | 1200 | 200–72000 |
| `snackBuffDurationTicks` | int | 1200 | 200–72000 |

### Implementation Notes

- Three `MobEffect` registrations (attribute-backed for Nimble/Diligent; Sated hooks `FoodData#addExhaustion` via a small mixin checking the effect). Pumpkin pie and cookies add no effects — they reuse the three, distinguished only by the shorter snack duration register.
- Stack size is raised by rebuilding the four stews' default component maps (`MAX_STACK_SIZE = 16`, every other component preserved) once at init through an `Item.components` accessor — no per-stack data, no datapack interaction. The full-inventory bowl drop is a targeted wrap on `Player#eat`'s inventory-add.
- Grant hook rides §3's consumption seam (same mixin, after fatigue application), keyed by item id — no food-component data manipulation, so datapack changes to the foods' nutrition don't interact. Pumpkin pie and cookies are ordinary food-component items, so they ride the generic `Player#eat` seam with the stews (no block path). The cake-block slice path routes through the same helper (it already must, for §3), so cake grants ride the identical seam. The item id also selects the duration register (meal / snack / cake).

---

## 5. Enriched Tilling

Diamond and netherite hoes till better farmland.

### Problem

Hoes are the only tool family with no reason to progress past stone — a diamond or netherite hoe is a joke item. Late-game agriculture deserves late-game tools.

### Behavior

When a hoe use **creates farmland** (on grass block, dirt, or dirt path), the new farmland's SoilData records an enriched chance by hoe tier:

| Hoe | `enrichedChance` |
|---|---|
| Wood / stone / iron / gold | 0% |
| Diamond | **10%** |
| Netherite | **15%** |

On every mature harvest of that block (§1 choke point), roll `enrichedChance`: on success, append **+1 of the crop's primary product** to the drops. The bonus:

- is permanent for the life of the farmland block — it survives any number of harvests;
- is cleared when the block reverts to dirt (§1 edge cases); re-tilling rolls a fresh value from whatever hoe is used;
- is suppressed at fertility 0 (§1 exhausted clamp);
- stacks with §6 Fertilizer (independent additions).

**Trample resistance.** Enriched farmland (`enrichedChance > 0`) is not reverted to dirt by a **player's** trampling — a jump that would revert plain farmland leaves an invested block standing, so a plot worth a hundred hours survives its own gardener's misstep. Plain farmland stays exactly as fragile as vanilla. The protection covers only a player's own feet: a mob still reverts enriched farmland under `mobGriefing`, keeping world danger out of scope. Governed by `enrichedSoilResistsTrampling` (default on), independent of `enableEnrichedTilling` — a block already enriched keeps resisting even if tilling is later disabled.

### Edge Cases

- **Existing farmland** cannot be re-tilled in vanilla; the only way to change a block's tier is reversion + re-till.
- **Enchantments on the hoe** are irrelevant to the roll; Fortune on the *harvesting* tool applies to the base loot as vanilla, before the flat bonus is appended.
- **Creative tilling** counts normally.
- **Multiplayer:** whoever tills sets the bonus; the block then serves everyone equally (shared world state).
- **Mob trampling** still reverts enriched farmland (under `mobGriefing`) — the trample resistance covers only a player's own feet, never hostile trampling.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableEnrichedTilling` | bool | true | — |
| `diamondHoeEnrichChance` | int | 10 | 0–100 |
| `netheriteHoeEnrichChance` | int | 15 | 0–100 |
| `enrichedSoilResistsTrampling` | bool | true | — |

### Implementation Notes

- Hook the till action (`UseBlockCallback` before vanilla, or a `HoeItem#useOn` mixin) — detect the dirt→farmland conversion, write `enrichedChance` by tier into `SoilStore`.
- The harvest-side roll lives in §1's drop choke point.
- Trample resistance wraps the single `turnToDirt` call in `FarmBlock#fallOn` (a `@WrapWithCondition`), skipping the revert for a player over an enriched block while leaving the fall-damage `super.fallOn` intact; the mob path is untouched.

---

## 6. Compost Fertilizer

The composter produces Fertilizer; Fertilizer permanently improves a plot.

### Problem

The composter converts surplus crops into bone meal — a growth *accelerant*, which loops straight back into producing more surplus. Vanilla offers no way to convert farming effort into permanent improvement of the farm itself.

### Behavior

**New item: `cultivation:fertilizer`** (stack size 64, no crafting recipe — the composter is its source).

- **Composter output:** when a composter at level 8 is emptied — by player use or by hopper extraction — it yields **1 Fertilizer** instead of 1 bone meal (while `composterProducesFertilizer` is true). Bone meal remains available from skeletons, fishing, and bone blocks; nothing else about the composter changes.
- **Application:** using Fertilizer on a farmland block (or on a crop — it applies to the farmland beneath) sets `fertilizerRemaining = fertilizerDoseHarvests` (default **15**), consumes the item, and plays vanilla bone-meal particles + use sound. A partial dose can be topped up at any time — the counter resets to full and the item is consumed. At an already-full dose the use fails silently: no effect, item not consumed. Fertilizer is not usable on any other block and is **not** a growth accelerant — it never advances crop age.
- **Effect:** every mature harvest of a block with `fertilizerRemaining > 0` appends **+1 of the crop's primary product** to the drops, guaranteed, and decrements the counter by 1; at 0 the bonus stops until the next dose. At fertility 0 the bonus is suppressed and the counter is **not** decremented — exhausted ground never spends a dose it doesn't pay out. Independent of (stacks with) §5. An active dose renders the compost-fleck overlay (§1 Visual Feedback).

A block with netherite tilling and an active dose on healthy soil yields `base + 1 (+1 at 15%)` per harvest — the investment ceiling, sustained by re-dosing every 15 harvests. At default drain a monoculture block consumes about two doses per full-to-exhausted cycle, so a working farm keeps its composter busy forever.

### Edge Cases

- **Dispensers** have no behavior for Fertilizer in v1 (the item is ejected as an item).
- **`composterProducesFertilizer=false`** restores vanilla bone-meal output; already-applied doses keep working (governed by `enableFertilizer`).
- **`enableFertilizer=false`** disables application and the +1 bonus; stored dose counters are retained untouched but inert, the item stays registered (inventories never break), and the composter reverts to bone meal regardless of the other flag.
- **Multiplayer:** shared block state; one dose serves all players.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableFertilizer` | bool | true | — |
| `composterProducesFertilizer` | bool | true | — |
| `fertilizerDoseHarvests` | int | 15 | 1–1000 |

### Implementation Notes

- Composter seam: `ComposterBlock` produces its output in two places — the player-extraction path and the `WorldlyContainer` output slot hoppers pull from; one mixin per path swaps the `ItemStack` (both check the config live).
- Application: item class with `useOn` writing to `SoilStore`; harvest-side bonus and dose decrement in §1's choke point.

---

## 7. The Scythe

A harvesting tool that reaps and replants a 3×3.

### Problem

Manual harvesting of a large field is click-per-block tedium followed by seed-per-block replanting — the single biggest push toward AFK farm automation. Satisfying manual farming needs a tool built for it.

### Items & Recipes

Three items: `cultivation:iron_scythe`, `cultivation:diamond_scythe`, `cultivation:netherite_scythe`.

| Scythe | Durability | Attack damage (total) | Attack speed | Enchantability |
|---|---|---|---|---|
| Iron | 250 | 4.0 | 1.6 | 14 |
| Diamond | 1561 | 5.0 | 1.6 | 10 |
| Netherite | 2031 | 6.0 | 1.6 | 15 |

Shaped recipe (iron/diamond), 3 material + 2 sticks — a curved blade over an angled handle, distinct from the hoe's pattern:

```
 II
IS
 S
```

(row 1: ` II`, row 2: `IS `, row 3: ` S ` — I = iron ingot / diamond, S = stick.) Netherite via the vanilla smithing-table upgrade (netherite upgrade template + diamond scythe + netherite ingot), preserving enchantments and damage.

### Behavior

When a player breaks a **mature supported crop** (§1 table) with a scythe in main hand:

1. The vanilla single-block break is replaced by a **3×3 sweep** at the same Y, centered on the target.
2. For each mature supported crop in the area (center included): resolve its drops through the normal loot path — Fortune on the scythe applies per block, §1 drain and §5/§6 bonuses apply per block — then **withdraw 1 of the crop's seed item** from those drops and reset the crop block to age 0. If the drops contain no seed (possible on an unlucky wheat roll), the block is left empty, farmland intact.
3. Remaining drops spawn at each block's position. Immature crops, stems, and non-crop plants in the area are untouched.
4. Durability: **1 per crop harvested** (Unbreaking applies normally). The vanilla sweep-attack sound plays at the center.

Breaking an immature crop with a scythe is a plain vanilla break. The scythe has no use-key behavior and no sweep *damage* — the 3×3 is harvest, not combat; as a weapon it hits one target like any tool.

### Edge Cases

- **Mixed fields:** the sweep handles each block independently — a wheat/carrot alternating field harvests and replants both crops correctly, and rotation drain (§1) is evaluated per block.
- **Pitcher crops** (2 blocks tall): count as their base block; harvest breaks both halves, replants a pitcher pod at age 0.
- **Creative:** the sweep runs identically (harvest + replant + drops), with no durability loss. It is deliberate tool use, not mining.
- **Protection/claim mods:** each block in the sweep is a normal block-break at that position, so per-block protection checks in break events fire per block; a denied block is skipped.
- **Enchantments:** Fortune and Unbreaking as above; Mending accepted; Efficiency and Silk Touch are permitted but functionally inert on instant-break crops.
- **Multiplayer:** server-side; the actor is the breaking player for all statistics, advancement triggers, and API callbacks.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableScytheHarvest` | bool | true | — |

`false` reduces scythes to ordinary single-block tools (items stay registered and craftable).

### Implementation Notes

- Intercept at `PlayerBlockBreakEvents.BEFORE` on the center block: cancel vanilla, run the unified sweep through §1's harvest helper per block (one code path for drops/drain/bonuses/callback), replant, apply durability.
- Item tags: all three scythes join `#minecraft:enchantable/durability` and `#minecraft:enchantable/mining` (Fortune/Unbreaking/Mending availability, and the surface sibling enchantment mods key off), plus a `#cultivation:scythes` tag.

### Right-Click Harvest

The single-block sibling of the sweep. A **bare-hand right-click** on a mature supported crop harvests that one block and replants it — the same reap-and-replant the scythe performs, without the tool.

#### Behavior

When a player right-clicks a **mature supported crop** (§1 table) with an **empty main hand**:

1. The crop's drops resolve through the one harvest choke point (§1: drain → exhausted clamp → §5/§6 bonuses → `CultivationHarvestCallback`) — there is no tool in hand, so **no Fortune** applies.
2. One of the crop's seed items is withdrawn from those drops to replant the block at age 0 (the shared replant seam the scythe uses). If the drops contain no seed, the block is left empty, farmland intact.
3. The remaining drops spawn at the block, and the crop's own break sound plays.

Immature crops, stems, and non-crop blocks are left as vanilla right-click behavior (which, on a crop, is nothing). A non-empty main hand takes its own use — only a bare main hand harvests; the off-hand never triggers it. Creative harvests identically (there is no tool to spare durability on). It is server-authoritative: the actor is the interacting player for statistics and API callbacks.

#### Differentiation from the Scythe

The scythe stays the tool for scale: one swing reaps and replants the full 3×3, carries Fortune and other enchantments, and costs durability per crop. The bare-hand gesture is one block, no Fortune, no cost — the quality-of-life pick for a stray crop, not a field.

#### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableRightClickHarvest` | bool | true | — |

`false` returns a bare-hand right-click on a crop to vanilla (no effect).

#### Implementation Notes

- A `UseBlockCallback` listener, server-side only, gated on an empty main hand and a mature supported crop; it reaps through §1's harvest helper (the one code path for drops/drain/bonuses/callback) and the shared replant seam, then returns `SUCCESS`.

### Broadcast Sowing

The planting counterpart to the sweep, gated behind the **iron rake**. Right-clicking farmland with the rake in the main hand and a crop seed in the off-hand sows the surrounding 3×3 in one pass, so laying out a fresh field is no longer click-per-block. First sowing is the gap the scythe leaves — it replants what it reaps, but never seeds bare ground; the rake is the tool that does.

#### Item & Recipe

One item: `cultivation:iron_rake`. It draws the iron tier's stats and carries no attack modifiers — a tool, not a weapon.

| Rake | Durability | Enchantability |
|---|---|---|
| Iron | 250 | 14 |

Shaped recipe, 3 iron ingots + 2 sticks — a toothed head over a straight handle, distinct from the hoe and the scythe:

```
III
 S
 S
```

(row 1: `III`, row 2: ` S `, row 3: ` S ` — I = iron ingot, S = stick.) The rake accepts Unbreaking and Mending (`#minecraft:enchantable/durability`) and repairs with iron.

#### Behavior

When a player **right-clicks a farmland block** with an **iron rake in the main hand** and an **in-scope crop seed in the off-hand**:

1. The 3×3 of farmland centered on the targeted block is sown, the center included. Each free block is planted with the off-hand seed's crop at age 0.
2. A block is planted only where a single seed could be: the position is empty (replaceable) and the crop can survive there (farmland below, sufficient light). Occupied or unsuitable blocks are skipped.
3. **One off-hand seed and one rake durability are spent per block actually planted**, in survival; the off-hand stack and the rake's remaining durability each cap how many blocks are sown, and a rake that breaks mid-pass stops the sow. In creative the full 3×3 is sown and no seed or durability is spent. The crop's own place sound plays once at the center.

Without a rake, planting stays vanilla single-block. In-scope seeds are the six **farmland replant crops** (§1 table — wheat, carrots, potatoes, beetroots, torchflower, pitcher); nether wart and sweet berries are out of scope and fall through to vanilla. One seed type per pass — alternating polyculture rows (§2) stay a deliberate layout act. Sowing is not a harvest: a fresh plant touches no soil state, so it never drains fertility or records rotation memory.

#### Differentiation from the Scythe

The scythe reaps and replants a standing 3×3; the rake seeds a bare 3×3. Together they are the field's tool pair — the rake for ground still empty, the scythe for the crop already in it — covering the whole rhythm from first sowing through harvest-and-replant.

#### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableBroadcastSowing` | bool | true | — |

`false` reduces the rake to an inert tool; off-hand seeds plant one block the vanilla way (the item stays registered and craftable).

#### Implementation Notes

- A `UseBlockCallback` listener gated on an `iron_rake` in the main hand, an in-scope crop seed in the off-hand (`SupportedCrops.plantableCropForSeed`), and a farmland anchor. It checks each of the 3×3 positions for whether a seed could survive there — `canBeReplaced` for emptiness and `canSurvive` for farmland-below and light, a survivability test rather than full placement-permission parity (the off-center blocks are set directly, not replayed through a placement event) — then places the crop at age 0 (the pitcher's single-block pod handled like the shared replant seam), spends one off-hand seed and one rake durability per planted block, and plays the crop's place sound once at the center. The sow runs server-side; on the client a valid gesture returns `SUCCESS` on the main-hand pass so Fabric cancels the off-hand seed's predicted single-block placement and forwards the interaction, so the 3×3 and its sound are authored once by the server. It returns `SUCCESS` when the gesture applies (else `PASS`, so a lone off-hand seed can still land by vanilla when the whole 3×3 is occupied).

---

## 8. Villager Field Stewardship

Farmer villagers live by the soil rules.

### Problem

The vanilla farmer work task harvests and replants the same blocks forever. Against §1 that behavior would blindly strip every village field to exhaustion — villagers need just enough sense to farm sustainably, without becoming a different mob.

### Behavior

Four changes to the farmer's existing farmland work task — nothing else about villagers is touched:

1. **Drain applies automatically.** Villager harvests destroy mature crops with drops, so they flow through §1's actor-agnostic choke point. No villager-specific drain code.
2. **Rotation preference.** When replanting a block, the farmer prefers the first seed in its inventory whose crop differs from the block's `lastCrop`; if it has none, it plants what it has. (A farmer holding only wheat seeds farms wheat — accepted.)
3. **Fallow discipline.** Farmland below `villagerFallowThreshold` fertility (default **25** — the Tired band) is excluded from the farmer's *replant* targets; it becomes eligible again at `villagerReplantThreshold` (default **50**). The gap is hysteresis so farmers don't churn at the boundary. Harvesting an existing mature crop is always allowed regardless of fertility.
4. **Fertilizer upkeep.** Fertilizer joins the farmer's wanted-item list, so farmers pick it up like seeds — and the vanilla composting habit already hands it to them, since village composters emit Fertilizer while `composterProducesFertilizer` is true. During fieldwork, a farmer holding Fertilizer applies one dose to a workable farmland block whose `fertilizerRemaining` is 0 — never topping up a partial dose — following §6's application rules (particles, sound, counter to full). Villages consequently run the whole loop unaided: grow, compost, dose, repeat. The trade is honest: farmers no longer bone-meal crops (their composters no longer produce bone meal); they sustain their fields instead. Gated by `enableVillagerFertilizing`, and inert unless `enableFertilizer` is also true.

Village fields consequently settle into rotating patchworks with resting strips — visibly tended ground — and their long-run output obeys the same rules a player's fields do.

### Edge Cases

- **No trades, prices, gossip, reputation, schedules, professions, or names are touched** — the villager-identity silo is Mercantile's (concord `VISION.md` §8/§9). This feature edits exactly one work task's target selection and seed choice.
- **Disabled** (`enableVillagerStewardship=false`): the farmer task is fully vanilla. Soil drain still applies to their harvests while §1 is enabled — villagers then farm unsustainably, which is the config owner's choice.
- **Multiplayer:** server-side AI; no player state involved.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableVillagerStewardship` | bool | true | — |
| `enableVillagerFertilizing` | bool | true | — |
| `villagerFallowThreshold` | float | 25.0 | 0–100 |
| `villagerReplantThreshold` | float | 50.0 | 0–100 (≥ fallow threshold, clamped up) |

### Implementation Notes

- Mixin into the farmer work behavior (`HarvestFarmland`): filter candidate positions for the replant case against `SoilStore` fertility; reorder the inventory seed scan for rotation preference; add a dose-application step when the farmer holds Fertilizer and the target block's dose is spent. Three injection points, no behavior-tree restructuring.
- Fertilizer joins the villager wanted-items set (Fabric API where it suffices, else a small mixin) so farmers pick it up.

---

## 9. Commands

### `/cultivation` Command Tree

| Command | Permission | Behavior |
|---|---|---|
| `/cultivation soil` | 0 | Reports the farmland block the player is looking at (≤ 10 blocks): fertility % and band, last crop, enriched %, remaining Fertilizer dose — e.g. `Fertility 62% (Fair) — last crop: Wheat. Enriched +15%, fertilizer 7/15.` Error if not looking at farmland. |
| `/cultivation soil set <0..100>` | 2 | Sets the targeted farmland's fertility. |
| `/cultivation field` | 0 | Surveys the 9×9 plot around the looked-at farmland block (≤ 10 blocks): average fertility % and band, counts of exhausted, enriched, and fertilized blocks, and the distinct crops in rotation — e.g. `Field 9×9: 47 farmland, avg fertility 58% (Fair)` / `12 exhausted, 8 enriched, 5 fertilized` / `Crops: Wheat, Carrots, Potatoes`. Untracked columns count as full fertility. Error if not looking at farmland. |
| `/cultivation diet` | 0 | Lists the caller's fatigue entries (`Bread −30%`) and last three foods. |
| `/cultivation diet reset [player]` | 2 | Clears DietData for the target (default: caller). |
| `/cultivation reload` | 2 | Reloads the JSON config from disk. Reports failure when the file could not be read at all (unparseable, not a JSON object, or over the size limit) — the server falls back to defaults and leaves the file untouched, and the operator is told their edits did not take. A file that loads but has individual values clamped into range reports success. |

All feedback is localized (`command.cultivation.*`). Diagnostic density is favored over prose in op-only output.

---

## 10. Advancements

Four entries, parented into the **vanilla Husbandry tab** (vanilla-deferential: this mod extends that tab's story rather than opening its own).

| Id | Title | Trigger |
|---|---|---|
| `balanced_table` | A Balanced Table | trigger a §3 fatigue reset by eating 3 distinct foods |
| `long_term_investment` | Long-Term Investment | apply Fertilizer to farmland |
| `reap_what_you_sow` | Reap What You Sow | harvest 9 mature crops in a single scythe sweep |
| `old_growth` | Old Growth | harvest a crop from a block that is both enriched and carrying an active Fertilizer dose |

Custom criterion triggers fired from the §1 choke point, §3 reset check, §6 application, and §7 sweep. Icons reuse the mod's item sprites.

---

## Configuration

All features are independently toggleable via a ModMenu / Cloth Config screen and a JSON config file (`config/cultivation.json`), created with defaults on first launch. `configVersion` is **1**. Unknown/missing fields are filled with defaults and clamped to valid ranges after load; a corrupted file falls back to defaults and is left untouched.

### Server Config

| Key | Type | Default | Description |
|---|---|---|---|
| `enableSoilFertility` | bool | true | Master toggle for soil fertility (§1) |
| `harvestDrain` | float | 3.0 | Fertility lost per same-crop harvest |
| `rotationDrainMultiplier` | float | 0.5 | Drain multiplier when the crop differs from the last harvest |
| `fallowRecoveryPerRandomTick` | float | 2.0 | Fertility regained per random tick while fallow |
| `rainRecoveryMultiplier` | float | 2.0 | Live-path recovery multiplier while rain falls on the block |
| `boneMealFertilityRestore` | float | 25.0 | Fertility restored by bone meal on fallow farmland |
| `tiredThreshold` | float | 25.0 | Fertility below which soil is Tired |
| `tiredGrowthMultiplier` | float | 0.75 | Growth speed multiplier while Tired |
| `exhaustedGrowthMultiplier` | float | 0.5 | Growth speed multiplier at fertility 0 |
| `enablePolyculture` | bool | true | Toggle the polyculture growth bonus (§2) |
| `polycultureGrowthMultiplier` | float | 1.2 | Growth multiplier for qualifying crops |
| `polycultureMinDifferentNeighbors` | int | 2 | Different-crop cardinal neighbors required |
| `enableSnifferPolyculture` | bool | true | Toggle the sniffer-crop premium polyculture bonus (§2) |
| `snifferPolycultureBonusMultiplier` | float | 2.0 | Scale on the polyculture bonus when a sniffer crop borders the row |
| `enableBeePollination` | bool | true | Toggle the bee-pollination growth bonus (§2) |
| `beePollinationGrowthMultiplier` | float | 1.1 | Growth multiplier for a crop near a populated hive |
| `beePollinationRange` | int | 8 | Block radius within which a populated hive boosts growth |
| `enableDietaryFatigue` | bool | true | Toggle dietary fatigue (§3) |
| `fatiguePerRepeat` | float | 0.10 | Effectiveness lost per consecutive repeat |
| `fatigueFloor` | float | 0.5 | Minimum effectiveness |
| `fatigueResetDistinctFoods` | int | 3 | Distinct recent foods that clear all fatigue |
| `enableMealBuffs` | bool | true | Toggle meal buffs (§4) |
| `mealBuffDurationTicks` | int | 2400 | Stew buff duration in ticks |
| `cakeBuffDurationTicks` | int | 1200 | Cake trio-buff duration in ticks |
| `enableEnrichedTilling` | bool | true | Toggle high-tier hoe tilling (§5) |
| `diamondHoeEnrichChance` | int | 10 | Bonus-drop chance (%) for diamond-tilled farmland |
| `netheriteHoeEnrichChance` | int | 15 | Bonus-drop chance (%) for netherite-tilled farmland |
| `enrichedSoilResistsTrampling` | bool | true | Enriched farmland is not trampled to dirt by players (§5) |
| `enableFertilizer` | bool | true | Toggle Fertilizer application and bonus (§6) |
| `composterProducesFertilizer` | bool | true | Composter yields Fertilizer instead of bone meal |
| `fertilizerDoseHarvests` | int | 15 | Harvests granted +1 product per Fertilizer dose |
| `enableScytheHarvest` | bool | true | Toggle the scythe 3×3 sweep (§7) |
| `enableRightClickHarvest` | bool | true | Toggle the bare-hand single-block right-click harvest (§7) |
| `enableVillagerStewardship` | bool | true | Toggle farmer rotation/fallow behavior (§8) |
| `enableVillagerFertilizing` | bool | true | Farmers apply Fertilizer to spent field blocks (§8) |
| `villagerFallowThreshold` | float | 25.0 | Fertility below which farmers stop replanting |
| `villagerReplantThreshold` | float | 50.0 | Fertility at which farmers resume replanting |

### Client Config

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `showSoilOverlays` | bool | true | — | Render Tired/Exhausted farmland overlays |
| `soilOverlayRenderDistance` | int | 24 | 4–64 | Max overlay render distance (blocks) |
| `showFatigueTooltips` | bool | true | — | Show fatigue lines on food tooltips |
| `showNutritionTooltips` | bool | true | — | Show hunger/saturation on food tooltips (deferred to AppleSkin when present) |

---

## Public API

Per concord [`API-STANDARD.md`](../../concord/API-STANDARD.md): the only stable package is **`com.rfizzle.cultivation.api`** (local `@Stable` marker — no shared jar); everything outside it is internal. Read-only by default, server-authoritative, provider errors isolated by the host.

### Surface

- `CultivationAPI.getFertility(ServerLevel, BlockPos): float` — 0–100; `100` for untracked soil; `-1` if the block is not soil. Soil is farmland, plus a second-wave crop's ground (soul sand under nether wart, dirt under a sweet berry bush) while `enableNonFarmlandSoil` is on.
- `CultivationAPI.getSoilInfo(ServerLevel, BlockPos): Optional<SoilInfo>` — `SoilInfo(float fertility, int enrichedChance, int fertilizerRemaining, Optional<ResourceLocation> lastCrop)`; empty if the block is not soil (same definition as `getFertility`).
- `CultivationAPI.getFoodEffectiveness(ServerPlayer, ItemStack): float` — the multiplier the player's next eat of this item would receive, in `[fatigueFloor, 1.0]` (the floor is the configurable `fatigueFloor`, default `0.5`); `1.0` when dietary fatigue is disabled.
- **`CultivationHarvestCallback`** — Fabric event fired server-side from the harvest choke point after Cultivation's own drain/bonuses: `(ServerLevel, BlockPos, BlockState crop, List<ItemStack> drops, @Nullable Entity harvester)`. The drops list is mutable — the sanctioned mutation point for siblings/third parties (e.g. quality-produce injection). A listener that throws is caught, logged, and skipped.
- **`CultivationFoodCallback`** — Fabric event fired server-side after a food is consumed and fatigue applied: `(ServerPlayer, Item food, float effectivenessApplied)`. Observation only.

### Deliberate absences

- **No HUD accessors** — Cultivation holds no HUD slot (see `design/DESIGN.md` §2); siblings' stacking sums treat it as absent.
- **No soil mutators** — fertility is gameplay state; outside mods observe it and react, they don't write it.

---

## Compatibility

### Required

- Fabric Loader ≥ 0.16.10, Fabric API (data attachments carry all state), Minecraft 1.21.1

### Optional Integrations

- **ModMenu + Cloth Config** — config screen.
- **Jade / WTHIT** — farmland tooltip: fertility % + band, enriched %, Fertilizer dose remaining, last crop; crop tooltip: polyculture bonus active, sniffer premium active, bees nearby, and the combined growth modifier.
- **EMI / REI / JEI** — scythe recipes (shaped + smithing) and a Fertilizer info entry naming the composter as its source.

### Sibling & Mod Compatibility

- **Cultivation is provider-side in the suite:** siblings consume its stable item ids (`cultivation:fertilizer`, the scythes), tags (`#cultivation:scythes`, the vanilla `#minecraft:enchantable/*` entries the scythes join), and the API above. Cultivation ships no `isModLoaded` consumption of any sibling in v1 — Mercantile's trade packs, Prosperity's loot injections, and Meridian's enchantment targeting all live in the consumer's repo per the suite pattern.
- **Mercantile** — orthogonal villager seams: Mercantile owns identity/trades/reputation; Cultivation touches only the farmland work task's target and seed selection. Both loaded, both apply, no coordination needed.
- **Sodium / EBE / Iris** — full compatibility: soil overlays are a `WorldRenderEvents.LAST` post-pass; no block rendering, chunk meshing, or block-entity rendering is touched.
- **Right-click-harvest mods** — mods that replant by destroying-with-drops flow through the choke point and drain normally; mods that silently swap block states without drops bypass drain (accepted, documented).

---

## Sound Design

All cues are **vanilla** in v1 — every sound this mod needs is organic foley vanilla already nails, exactly the case DESIGN-SYSTEM §9 reserves for vanilla events. A custom synthesized cue is added only if a future feature earns its own identity.

| Feature | Event | Vanilla Sound |
|---|---|---|
| Enriched tilling | farmland created | `minecraft:item.hoe.till` |
| Bone meal amendment | fertility restored | `minecraft:item.bone_meal.use` |
| Fertilizer | applied to farmland | `minecraft:item.bone_meal.use` |
| Composter | Fertilizer collected | `minecraft:block.composter.empty` |
| Scythe | 3×3 sweep | `minecraft:entity.player.attack.sweep` + per-crop `block.crop.break` |

---

## Localization

All user-facing text uses translation keys in `assets/cultivation/lang/en_us.json`, namespaced by surface per concord DESIGN-SYSTEM §10. Band and effect names route through `translationKey()` helpers — code never formats an enum for the player.

| Pattern | Example | Used for |
|---|---|---|
| `config.cultivation.*` (+ `.tooltip`) | `config.cultivation.harvest_drain` | Cloth Config labels and descriptions |
| `command.cultivation.*` | `command.cultivation.soil.report` | Command feedback |
| `tooltip.cultivation.*` | `tooltip.cultivation.fatigue.losing` | Food fatigue lines; Jade/WTHIT soil and crop lines |
| `info.cultivation.*` | `info.cultivation.fertilizer` | Item purpose lines (Fertilizer, scythes) |
| `item.cultivation.<id>` | `item.cultivation.iron_scythe` | Item names (vanilla-mandated) |
| `effect.cultivation.<id>` | `effect.cultivation.nimble` | Status effect names (vanilla-mandated) |
| `advancements.cultivation.*` | `advancements.cultivation.old_growth.title` | Advancement titles/descriptions |
| `stat.cultivation.*` | `stat.cultivation.crops_scythed` | Custom statistics, if added |

Parameterized messages use `%s`/`%d` style. No chat toasts ship in v1, so the ✦ notification surface is unused.

---

## HUD

Cultivation ships **no HUD element**. The slot decision and reasoning live in `design/DESIGN.md` §2; the API's deliberate omission of HUD accessors is recorded under Public API above. Soil state is world-readable (overlays, Jade/WTHIT, `/cultivation soil`); diet state lives on tooltips; buffs use vanilla effect icons.

---

## Testing Strategy

### Unit Tests (JUnit + `fabric-loader-junit`)

- Fertility math: drain with/without rotation, clamping, band boundaries (25.0 is Fair; 0 exactly is Exhausted), lazy-recovery formula against the live-path expectation, rain multiplier on the live path only
- Fertilizer dose bookkeeping: decrement rides the bonus, no decrement at fertility 0, top-up resets to full, application at a full dose fails
- Dietary fatigue algorithm: single-food decay to floor, two-food alternation grinding both to floor, three-food rotation resetting every eat, history bookkeeping, death clear
- Polyculture neighbor counting: alternating rows, checkerboard, monoculture, field edges/corners, stem ids
- Exhausted yield clamp and bonus-suppression ordering; primary product/seed mapping for all six crops
- Config round-trip, clamping, corrupted-file fallback; `villagerReplantThreshold` clamped ≥ fallow threshold
- Codec round-trips: `SoilStore` (empty, dense, eviction of default entries), `DietData`

### Gametests (Fabric Gametest API)

- Harvest drains 3.0 same-crop / 1.5 rotated; immature break drains nothing; creative break drains nothing
- Fallow farmland recovers via random ticks, twice as fast under rain; reverted-to-dirt position settles lazy recovery on re-till (rain-blind); bone meal restores +25 and fails at 100
- Tired/exhausted growth multipliers applied (assert on computed growth speed, not statistics)
- Polyculture multiplier applied for a qualifying layout, absent for monoculture
- Enriched tilling: diamond/netherite set 10/15; forced 100% chance yields +1 product; reversion clears
- Fertilizer: composter level 8 yields Fertilizer (player and hopper paths); application sets a full 15-harvest dose and consumes; application at a full dose fails without consuming; topping up a partial dose resets it to full; +1 product per harvest with the counter decrementing to 0, then no bonus; exhausted soil suppresses the bonus without decrementing
- Scythe: 3×3 harvest + replant at age 0, seed withdrawn per block, immature skipped, durability −1 per crop, no-seed roll leaves block empty, mixed-crop field handled per block
- Villager: farmer skips replanting below 25, resumes at 50, prefers a rotated seed when inventory allows; farmer picks up Fertilizer and doses a spent block, never a partial one
- Fatigue: consecutive eats restore stepped-down hunger; three distinct foods reset; effects (golden apple) unaffected
- Meal buffs: each stew grants its effect for 2400 ticks; a second stew replaces; suspicious stew grants a level-II roll plus its vanilla effect; a cake slice grants the trio for 1200 ticks and replaces a stew buff
- Commands: `soil`, `soil set`, `field`, `diet`, `diet reset` behave and permission-gate as specced; the field survey aggregates fertility, band, and coverage counts over the plot

### Manual Testing

- Soil overlay rendering: band textures, fleck overlays (fertilized/enriched) composing with cracks, render distance, depth testing, Sodium/EBE/Iris
- Food tooltips (nutrition and fatigue lines, with and without AppleSkin) and Jade/WTHIT soil/crop lines
- Effect icons in the vanilla HUD; config screen labels/tooltips
- Village farms over long observation: rotation patchwork and fallow strips emerging
