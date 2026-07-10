# Cultivation — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source column
> flags a pixel asset with no `.glyph` source yet — a candidate for the glyph
> pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths are under
> `src/main/resources/` unless noted.

No assets are committed yet — every entry below is planned. An entry graduates
into a Branding masters / In-game pixel art table when the asset lands.

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` → `site/assets/logo.png` — (planned, branding) |
| Mod icon 128×128 | Gemini (prompt in `DESIGN.md` §4) or `/glyph` size ladder | `art/icon-128.png` → `site/assets/icon.png`, `fabric.mod.json` icon — (planned, branding) |
| Sheaf glyph 16×16 | `/glyph` (`art/glyph-16.glyph`) | `art/glyph-16.png` → Jade/WTHIT + recipe-viewer icon, cross-mod site footer — (planned, branding) |
| OG image 1200×630 | composed from full logo on Ink | `site/assets/og-image.png` — (planned, branding) |
| Favicon set | derived from icon-128 | `site/assets/favicon.ico`, `favicon-32.png`, `apple-touch-icon.png` — (planned, branding) |
| Fertilizer item 16×16 | `/glyph` | `assets/cultivation/textures/item/fertilizer.png` — (planned, item) |
| Iron scythe item 16×16 | `/glyph` | `assets/cultivation/textures/item/iron_scythe.png` — (planned, item) |
| Diamond scythe item 16×16 | `/glyph` | `assets/cultivation/textures/item/diamond_scythe.png` — (planned, item) |
| Netherite scythe item 16×16 | `/glyph` | `assets/cultivation/textures/item/netherite_scythe.png` — (planned, item) |
| Nimble effect icon 18×18 | `/glyph` | `assets/cultivation/textures/mob_effect/nimble.png` — (planned, effect) |
| Diligent effect icon 18×18 | `/glyph` | `assets/cultivation/textures/mob_effect/diligent.png` — (planned, effect) |
| Sated effect icon 18×18 | `/glyph` | `assets/cultivation/textures/mob_effect/sated.png` — (planned, effect) |
| Tired-soil overlay 16×16 | `/glyph` | `assets/cultivation/textures/overlay/soil_tired.png` — (planned, overlay) |
| Exhausted-soil overlay 16×16 | `/glyph` | `assets/cultivation/textures/overlay/soil_exhausted.png` — (planned, overlay) |
