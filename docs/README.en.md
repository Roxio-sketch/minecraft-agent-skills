# Minecraft Agent Skills & Rendering Workbenches

A maintainer-led open-source collection of Codex skills, editable shader/VFX workbenches, and Minecraft Forge 1.20.1 integration examples. It connects effect design in natural language to recipe editing, implementation, and validation with explicit evidence boundaries.

This release publishes **23 rendering skills, 32 workbench/research/preview entry points, 18 development source directories, and 51 groups of project-specific VFX records**. Existing AXO launcher and Photon automation skills remain available. [Complete inventory](rendering-catalog.md).

The scope includes procedural skies and atmosphere, volumetric clouds and fog, black/white holes, lightning, cosmic item materials, colored glint, refraction, rim lighting, energy shields, trails and beams, particles, scene windows, local subscenes, selective bloom, and GPU performance.

## 2026-10-19: portable Xiantu VFX library

The new [minecraft-xiantu-vfx-library](../skills/minecraft-xiantu-vfx-library/SKILL.md) packages nine self-contained previews: sword formations, gold/white sword qi, five-element qi sea, edge-breathing cataclysm rift, energy, lightning, shields, trails, and procedural space rift. Ten entries (gallery plus effects) bring the shared launcher to 43. Personal player skins, commercial video references and Minecraft textures are excluded; original notices remain. The browser prototypes do not certify game integration.

Run `python skills/minecraft-xiantu-vfx-library/scripts/serve.py` for this gallery only.

See the [Chinese Skills usage guide](skills-usage.md) for installation, explicit `$skill-name` calls, gallery startup, recipe export, and game-integration boundaries. [Current preview captures](../README.md#仙途特效库截图) are labeled as previews, not Minecraft runtime evidence.

## Run

Python 3.10+ and a modern WebGL2 browser:

```text
python rendering/启动全部工作台.py
```

Choose an entry number; the launcher serves on loopback with an available port. Preserve the sibling `skills/` and `rendering/` directories. Install selected skill folders into your Codex skills directory. Regular previews do not require an API key. Forge examples generally target Minecraft 1.20.1 / Java 17; check each project for precise loader versions and dependencies.

## Evidence and maturity

Research catalogs, browser design previews, standalone Forge tests, and user-instance checks are distinguished. Historical reports are published with dates and limitations; the release checks do not imply all historical game tests were rerun. The tornado workbench is a browser implementation without completed Forge integration; Halo provides asset generation and integration guidance rather than a universal installable runtime.

A documented cauldron-fire optimization uses lower-resolution RGBA16F volume integration and scene-depth-aware premultiplied reconstruction. On an RTX 4060 Ti at 1280×900, a specific near-view fire pass decreased from approximately 83–86 ms to 11–11.5 ms. This is pass-level GPU timing, not total game FPS. Reconstruction softens detail; later visual feedback identified dark-fire opacity and ground-visibility issues. [Evidence and limitations](validation-and-evidence.md).

## Open-source maintenance

Roxio-sketch maintains the skills, source, documentation, releases, and integration records. The collection is early-stage and does not claim broad adoption. Proposed Codex/API maintenance work includes issue triage, PR assistance, shader/recipe consistency checks, regression planning, and documentation/release automation. [Maintainer case](oss-maintainer-case.md).

## Licensing

Original content is MIT unless a more specific notice applies. Third-party noise, Three.js, Lucide, Gradle wrappers, LGPL RenderBlender textures, and curated research sources retain their respective notices. Minecraft armor textures, commercial reference videos, downloaded full shader packs, build caches, and host QA JARs are excluded. [Provenance](licensing-and-provenance.md).
