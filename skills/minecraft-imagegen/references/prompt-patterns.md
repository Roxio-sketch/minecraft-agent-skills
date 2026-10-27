# Minecraft Image Prompt Patterns

Load this file when the request needs a stronger prompt shape than the base
`SKILL.md` workflow provides. Keep prompts specific, but do not over-specify
every surface if the user asked for a fast concept pass.

## Base Spec

Use this template when translating a loose request into a structured prompt:

```text
Use case: <pack-icon | texture-concept | promo-art | server-banner | ui-mockup>
Asset target: <where the image will ship>
Minecraft context: <vanilla-faithful | modded industrial | fantasy server | etc.>
Primary subject: <main thing to show>
Important details: <must-show motifs, mobs, blocks, items, biome cues>
Composition: <square icon | wide banner | flat texture sheet | UI panel background>
Palette: <color direction>
Style: <pixel-art-inspired | painterly splash art | clean mockup>
Keep: <details that must survive revisions>
Avoid: <logos, watermarks, clutter, extra text, off-theme props>
```

## Fresh Generation Pattern

Use when there is no source image to preserve.

```text
Create a new <asset target> for a Minecraft project.
Show <primary subject>.
Style: <style>.
Composition: <composition>.
Palette: <palette>.
Constraints: <hard constraints>.
Avoid: <negative constraints>.
```

## Existing Image Edit Pattern

Use when there is a local image, concept board, or prior generated asset.

```text
Edit the provided image.
Change only: <background / palette / specific objects / framing>.
Keep unchanged: <logo, silhouette, text layout, main character, icon shape>.
Target use: <asset target>.
Avoid: <negative constraints>.
```

Focus edit prompts on deltas. Do not restate the entire scene unless the prior
result drifted significantly.

## Pack Icon Pattern

Use when the final asset must still read at `64x64`.

```text
Create a square Minecraft pack icon with a bold central silhouette.
Primary subject: <item, mob, biome emblem, or landmark>.
Keep the composition simple and readable at tiny size.
Use strong contrast and minimal small detail.
No text, no watermark, no decorative border unless requested.
```

## Texture Concept Pattern

Use when the image is concept art that will later be simplified manually into a
final tile texture.

```text
Create a flat front-on texture concept for <block or item material>.
No perspective, no scene background, neutral lighting.
Make it easy to simplify into a small Minecraft texture later.
Style direction: <vanilla-faithful | noisy industrial | magical crystal | etc.>.
```

## Release Banner Pattern

Use when the output needs copy-safe negative space for titles or release text.

```text
Create a wide Minecraft release banner for <project>.
Show <hero scene>.
Leave clean negative space on the <left or right> for title text.
Favor a strong focal point and mobile-readable composition.
No embedded logo or text unless exact copy was provided.
```

## UI Mockup Pattern

Use when exploring plugin, mod, or server UX without implementing code yet.

```text
Create a Minecraft UI mockup background for <screen or panel>.
Reserve clear space for <buttons, slots, status text, tabs>.
Keep it readable, high-contrast, and aligned to a <vanilla | fantasy | tech> tone.
No tiny unreadable labels unless exact text was requested.
```

## Revision Loop

For follow-up turns, change one major variable at a time:

- composition
- palette
- subject emphasis
- lighting
- background complexity

Example:

```text
Keep the same icon silhouette and palette.
Reduce background detail and make the pickaxe head glow brighter.
```
