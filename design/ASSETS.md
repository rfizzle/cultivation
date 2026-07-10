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
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` (master, 3172×1344) → `site/assets/logo.png` (1600w web copy), README embed |
| OG image | derived from `art/logo.png` (1200×630, centered on Ink) | `site/assets/og-image.png` |

## In-game pixel art

`.glyph` sources are authored and committed; the final assets ship with the
implementation (no `src/main/resources/` tree exists yet).

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Sheaf brand glyph 16×16 (Jade/recipe viewers, suite footer) | `art/glyphs/sheaf-16.glyph` | `assets/cultivation/textures/gui/sheaf.png` — (not yet shipped) |
| Fertilizer item 16×16 | `art/glyphs/fertilizer.glyph` | `assets/cultivation/textures/item/fertilizer.png` — (not yet shipped) |
| Iron scythe item 16×16 | `art/glyphs/iron_scythe.glyph` | `assets/cultivation/textures/item/iron_scythe.png` — (not yet shipped) |
| Diamond scythe item 16×16 | `art/glyphs/diamond_scythe.glyph` | `assets/cultivation/textures/item/diamond_scythe.png` — (not yet shipped) |
| Netherite scythe item 16×16 | `art/glyphs/netherite_scythe.glyph` | `assets/cultivation/textures/item/netherite_scythe.png` — (not yet shipped) |
| Nimble effect icon 18×18 | `art/glyphs/effect_nimble.glyph` | `assets/cultivation/textures/mob_effect/nimble.png` — (not yet shipped) |
| Diligent effect icon 18×18 | `art/glyphs/effect_diligent.glyph` | `assets/cultivation/textures/mob_effect/diligent.png` — (not yet shipped) |
| Sated effect icon 18×18 | `art/glyphs/effect_sated.glyph` | `assets/cultivation/textures/mob_effect/sated.png` — (not yet shipped) |
| Tired-soil overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_tired.glyph` | `assets/cultivation/textures/overlay/soil_tired.png` — (not yet shipped) |
| Exhausted-soil overlay 16×16 (code-bound, tiling) | `art/glyphs/soil_exhausted.glyph` | `assets/cultivation/textures/overlay/soil_exhausted.png` — (not yet shipped) |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Mod icon 128×128 | Gemini (prompt in `DESIGN.md` §4) or `/glyph` size ladder | `art/icon-128.png` → `site/assets/icon.png`, `fabric.mod.json` icon — (planned, branding) |
| Favicon set | derived from icon-128 | `site/assets/favicon.ico`, `favicon-32.png`, `apple-touch-icon.png` — (planned, branding) |
| Sheaf glyph web copy | rendered from `art/glyphs/sheaf-16.glyph` | `site/assets/glyph-16.png` — (planned, with site assets) |
