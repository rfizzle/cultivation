# Cultivation — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source column
> flags a pixel asset with no `.glyph` source yet — a candidate for the glyph
> pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths are under
> `src/main/resources/` unless noted. Rendered previews under `art/glyphs/` are
> gitignored review artifacts, not entries.

## Branding masters

| Asset | Source | Final / derived copies |
|---|---|---|
| Full logo | Gemini (prompt in `DESIGN.md` §4, mirrored in `art/exploration/logo-prompt.md`) | `art/logo.png` (master, 3172×1344) → `site/assets/logo.png` (1600w web copy), README embed |
| OG image | derived from `art/logo.png` (1200×630, centered on Ink) | `site/assets/og-image.png` |
| Mod icon | `art/glyphs/icon-128.glyph` (32px native, ×4 ladder) | `art/icon-128.png` (128 master; `fabric.mod.json` icon when the jar exists) |
| Mod icon 512 | derived from `art/glyphs/icon-128.glyph` (×16 ladder) | `art/icon-512.png` → `site/assets/icon.png` (512), `site/assets/apple-touch-icon.png` (180) |

## In-game pixel art

`.glyph` sources are authored and committed; the final assets ship with the
implementation (no `src/main/resources/` tree exists yet).

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Sheaf brand glyph 16×16 (Jade/recipe viewers, suite footer) | `art/glyphs/sheaf-16.glyph` | `assets/cultivation/textures/gui/sheaf.png` — (not yet shipped) |
| Fertilizer item 16×16 | `art/glyphs/fertilizer.glyph` | `assets/cultivation/textures/item/fertilizer.png` |
| Iron scythe item 16×16 | `art/glyphs/iron_scythe.glyph` | `assets/cultivation/textures/item/iron_scythe.png` |
| Diamond scythe item 16×16 | `art/glyphs/diamond_scythe.glyph` | `assets/cultivation/textures/item/diamond_scythe.png` |
| Netherite scythe item 16×16 | `art/glyphs/netherite_scythe.glyph` | `assets/cultivation/textures/item/netherite_scythe.png` |
| Iron rake item 16×16 | `art/glyphs/iron_rake.glyph` | `assets/cultivation/textures/item/iron_rake.png` |
| Nimble effect icon 18×18 | `art/glyphs/effect_nimble.glyph` | `assets/cultivation/textures/mob_effect/nimble.png` |
| Diligent effect icon 18×18 | `art/glyphs/effect_diligent.glyph` | `assets/cultivation/textures/mob_effect/diligent.png` |
| Sated effect icon 18×18 | `art/glyphs/effect_sated.glyph` | `assets/cultivation/textures/mob_effect/sated.png` |
| Tired-soil overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_tired.glyph` | `assets/cultivation/textures/overlay/soil_tired.png` |
| Exhausted-soil overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_exhausted.glyph` | `assets/cultivation/textures/overlay/soil_exhausted.png` |
| Fertilized-soil fleck overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_fertilized.glyph` | `assets/cultivation/textures/overlay/soil_fertilized.png` |
| Enriched-soil fleck overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_enriched.glyph` | `assets/cultivation/textures/overlay/soil_enriched.png` |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Sheaf glyph web copy | rendered from `art/glyphs/sheaf-16.glyph` | `site/assets/glyph-16.png` — (planned, with site assets) |
