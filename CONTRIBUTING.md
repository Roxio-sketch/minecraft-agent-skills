# Contributing

Repository scope: publish only Minecraft-specific skills and their supporting resources. Keep general video editing, cover composition, voice, and unrelated personal skills outside this repository.

Issues and usage reports are welcome. Include Minecraft/Forge/Java versions, GPU/driver, browser, shader pack and version, exact workbench/Skill version, recipe, reproduction steps, and the scope of the failure. Remove tokens and private game/account data before posting.

For rendering changes, keep recipe schemas, workbench GLSL, Forge resources, and documentation consistent. State what was checked: static validation, browser GPU, standalone Forge, or an actual user instance. Retain source licenses. Do not claim a browser preview proves game compatibility or a pass timer proves total FPS.

Before submitting, run `python scripts/check_rendering_release.py`. For affected effects, use the existing checks and record visual/runtime validation where appropriate. Avoid committing build/run caches or extracted Minecraft textures.
