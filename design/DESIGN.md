# Cultivation — Design Specification

> Agriculture Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Cultivation makes farming a practice instead of a chore: soil that lives and tires, fields worth planning, food worth varying. The name evokes patient, deliberate care — the long-term relationship between a farmer and the ground. The visual language draws from **tilled earth**, **golden wheat sheaves**, **hand tools**, and **green growth** — the working farm at harvest time, not pastoral decoration. This is the mod's one mythic register (suite `VISION.md` §2): the field and the harvest.

### Tagline

*"Worth growing."*

### Motif

The motif object is a **bound wheat sheaf with crossed farm tools** — a scythe and a hoe crossed behind the sheaf, one composed object read as a farmer's crest. It may appear in the logo, site headers, and flavor art; it never appears in another mod's assets. The 16×16 glyph reduces the motif to the sheaf alone.

### Logo Description

**Full logo (`art/logo.png`, 3172×1344):** Pixel art per the Concord stone-frame formula. A dark moss-green stone brickwork frame, its lower course breaking into tilled-earth furrows with small green sprouts. Centered, a golden wheat sheaf bound with twine, glowing warm amber, with two wooden-handled farm tools crossed behind it — the upper blade a curved scythe. Scattered grains and a leaf sprig catch the glow. Below the frame, "CULTIVATION" in blocky pixel type in the amber gradient with "MINECRAFT AGRICULTURE OVERHAUL" as the subtitle. The frame sits in a wide pixel farmstead panorama under a sunset-amber sky: striped carrot, potato, and wheat fields, composters, grazing farm animals, a farmhouse with silo, trees and mountains — background scenery only, no second motif.

**Icon (`art/icon-128.png`):** The bound wheat sheaf isolated — golden stalks, twine band, two small leaf sprigs at the base — with a warm amber glow against a dark/transparent background. Reads cleanly at 128×128.

**Glyph (`art/glyphs/sheaf-16.glyph`):** A 16×16 pixel wheat sheaf — three golden wheat heads fanned above a pale twine band, a small leaf sprig at the base, `ink` outline — for Jade/WTHIT and recipe-viewer contexts. Cultivation has no HUD slot (§2 below), so this glyph never renders as a HUD element.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary surface | Loam | `#101a0a` | Backgrounds, dark surfaces |
| Secondary surface | Moss | `#1c2e10` | Mid-tones, card backgrounds |
| Accent 1 | Wheat Amber | `#D9A441` | Glows, highlights, headings, interactive elements |
| Accent 2 | Leaf Green | `#7CB342` | Growth accents, healthy-state indicators, links |
| Bright | Harvest | `#EDC35C` | Hover states, heading gradient end, emphasis |
| Working shade | Rich Earth | `#3E2A18` | Fertile-soil texture darks |
| Working shade | Pale Loam | `#9C8A6E` | Exhausted-soil overlay, depleted states |
| Working shade | Sprout | `#A5D66A` | Particles, fresh-growth glow |

Shared neutrals (text and surfaces) follow the standard tokens as-is — `--color-bone`, `--color-ash`, `--color-smoke`, `--color-ink`, `--color-card`, `--color-elevated` — see concord [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §1.

**Pairing-rule check (DESIGN-SYSTEM §2, all rows including reserved):** amber-with-leaf shares at most one accent with any member — Wheat Amber sits in the gold family beside Meridian's and Prosperity's gold, but gold-with-violet reads Meridian, gold-with-cyan reads Prosperity, and amber-with-leaf reads Cultivation. Leaf Green (yellow-green) is distinct from Mercantile's Emerald (blue-green), and the pair shares nothing with the reserved Apothecary (magenta/copper), Tempest (blue/white), or Stratum (grey/orange) rows. Surfaces are a dark tint of the mod's own green-amber hue, per §7 admission.

### Typography

- **Headings:** pixel/blocky display treatment in the accent gradient `#D9A441` → `#EDC35C`.
- Everything else is the standard (DESIGN-SYSTEM §3); in-game is the vanilla font, always.

---

## 2. HUD Decision

**No slot, by design.** The standard's test (concord [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md)) grants a slot only for persistent ambient state the player needs while walking around. Cultivation's state is either **block-local** (soil fertility — read from the ground itself via the tired/exhausted overlays, Jade/WTHIT, and `/cultivation soil`) or already surfaced by **vanilla UI** (dietary fatigue on food tooltips, meal buffs as vanilla status-effect icons). Nothing needs a permanent screen element. The 16×16 sheaf glyph exists for Jade/recipe-viewer contexts only, and the HUD accessors (`isHudVisible()` / `getHudHeight()`) are intentionally absent from the API — siblings' stacking math treats Cultivation as never occupying a slot.

---

## 3. Assets

The full asset manifest — every `.glyph` source under `art/`, the final resource/site path it ships as, and what is still `MISSING` a source — lives in [`ASSETS.md`](ASSETS.md).

Asset-family judgments (the suite stance: custom where it earns its place, vanilla where vanilla is right):

- **New items get custom pixel art** — Fertilizer and the three scythes are new items with no vanilla analogue; each is a 16×16 glyph-pipeline sprite in the palette above (scythe blades read as their tool tier: iron grey, diamond cyan-white, netherite dark).
- **Status effects get custom icons** — Nimble, Diligent, and Sated need 18×18 effect icons in the vanilla HUD; small, legible, single-object designs (boot, pick-edge, bowl).
- **Soil overlays are custom** — two 16×16 top-face overlay textures (tired: faint pale cracks; exhausted: heavy pale cracks on Pale Loam) rendered over vanilla farmland. The farmland block itself is never retextured or replaced.
- **Everything else stays vanilla** — crops, farmland, composter, bone meal, and all sounds (see SPEC — Sound Design). Vanilla is genuinely right for every block this mod touches.

---

## 4. Generation Prompts

**Full logo (Gemini):**

> Pixel art logo for a Minecraft mod named "CULTIVATION". A dark stone brickwork frame in near-black mossy greens (#101a0a, #1c2e10), the bottom course of bricks breaking into tilled soil furrows (#3E2A18) with small green sprouts (#7CB342, #A5D66A). Centered inside the frame: a bound sheaf of golden wheat (#D9A441) tied with twine, glowing warm amber (#EDC35C), with a scythe and a hoe crossed diagonally behind it on wooden handles, the scythe's curved blade at the upper right. A few floating grains and a leaf sprig catch the glow. Below the frame, "CULTIVATION" in a chunky blocky pixel font with a gold-to-amber gradient (#D9A441 → #EDC35C), and beneath it "MINECRAFT AGRICULTURE OVERHAUL" in small pixel type. Behind and around the frame, a wide pixel farmstead panorama under a sunset-amber sky: striped carrot, potato, and wheat fields, composters, a few grazing farm animals, a farmhouse with a silo, trees and distant mountains. Crisp pixel-art style, limited palette, no anti-aliasing.

**Icon 128×128 (Gemini):**

> Pixel art icon, 128×128, for a Minecraft mod: a bound sheaf of golden wheat (#D9A441) tied with a twine band, two small green leaf sprigs (#7CB342) at its base, glowing warm amber (#EDC35C) against a dark background (#0a0a0a). Centered, chunky pixels, limited palette, no anti-aliasing, no text.

Pixel-art sources (glyph, item sprites, effect icons, soil overlays) are `.glyph` files under `art/` — authored through the glyph pipeline, referenced from `ASSETS.md`, never duplicated here.

---

## 5. Image References

Exploration renders, rejected variants, and reference shots live in `art/exploration/`. None committed yet; the first logo-generation round seeds it.

---

## 6. Website & Listing Brand Notes

Content lives elsewhere — page copy in `site/` (rendered by the shared Concord template), store copy in `site/listing-*.md`; this section carries only brand direction.

- **Accent usage:** Wheat Amber (→ Harvest for hover/emphasis) carries headings, hero glow, and interactive elements; Leaf Green carries links, healthy-soil/growth accents, and secondary highlights. Surfaces and body text stay on the shared neutrals over the Loam/Moss tints. Accents are declared once in `site.json`'s theme block.
- **Hero art direction:** full logo over the dark stone-and-furrow field.
- **Gallery shots (1920×1080, vanilla or light shader):** a striped polyculture field mid-growth; a pale cracked exhausted plot beside dark fertile rows; a scythe swing clearing a 3×3; a composter with Fertilizer and a fertilized plot; a village farm grown into a rotating patchwork; a food tooltip showing dietary fatigue.
- **OG image:** full logo on Ink at 1200×630, per DESIGN-SYSTEM §6.

---

## 7. Concord Context

Cultivation owns the **agriculture silo**: crops, soil, food values, and cooking payoffs — beside Meridian (enchanting, violet/gold, compass rose), Mercantile (villagers & trade, emerald/emerald, market stall), Tribulation (difficulty, crimson/ember, hourglass), and Prosperity (loot, gold/cyan, treasure chest). Its amber-with-leaf signature is the pair concord's DESIGN-SYSTEM §2 reserves for this domain, and reads distinct from every sibling under the pairing rule: the only shared hue (gold-family amber) is disambiguated by its green partner. Suite standards this document defers to: concord [`VISION.md`](../../concord/VISION.md), [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md), [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md), [`API-STANDARD.md`](../../concord/API-STANDARD.md).

## Open Decisions

- Concord's DESIGN-SYSTEM §2 reserved row and `VISION.md` §9 profile carry this domain under the working name *Husbandry*; on admission to `members.json`, the reserved row renames to Cultivation (a concord-side edit, made deliberately with admission).
