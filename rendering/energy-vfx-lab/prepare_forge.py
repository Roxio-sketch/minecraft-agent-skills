from pathlib import Path
import shutil
ROOT=Path(__file__).parent
FORGE=ROOT/'forge'
base=ROOT.parent/'lab'
for f in ['gradlew','gradlew.bat','settings.gradle','gradle/wrapper/gradle-wrapper.properties','gradle/wrapper/gradle-wrapper.jar']:
 target=FORGE/f;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(base/f,target)
(FORGE/'settings.gradle').write_text((FORGE/'settings.gradle').read_text().replace('cosmic-rendering-lab','energy-vfx'),encoding='utf-8')
(FORGE/'build.gradle').write_text('''plugins { id 'java'; id 'net.minecraftforge.gradle' version '6.0.54' }
group = 'dev.energyvfx'
version = '0.2.5'
base { archivesName = 'energyvfx' }
java.toolchain.languageVersion = JavaLanguageVersion.of(17)
minecraft {
 mappings channel: 'official', version: '1.20.1'
 runs { client { workingDirectory project.file('run'); property 'forge.logging.console.level', 'info'; property 'energyvfx.qa', project.findProperty('qa') ?: 'false'; property 'energyvfx.ghostQa', project.findProperty('ghostQa') ?: 'false'; property 'energyvfx.perfQa', project.findProperty('perfQa') ?: 'false'; property 'energyvfx.motionNearCamera', project.findProperty('motionNearCamera') ?: 'false'; property 'energyvfx.motionQa', project.findProperty('motionQa') ?: 'false'; property 'energyvfx.buildupQa', project.findProperty('buildupQa') ?: 'false'; property 'energyvfx.buildupRecipe', project.findProperty('buildupRecipe') ?: ''; property 'energyvfx.buildupLabel', project.findProperty('buildupLabel') ?: 'baseline'; property 'energyvfx.zoomQa', project.findProperty('zoomQa') ?: 'false'; property 'energyvfx.zoomRecipe', project.findProperty('zoomRecipe') ?: project.findProperty('buildupRecipe') ?: ''; property 'energyvfx.disableDirectParcels', project.findProperty('disableDirectParcels') ?: 'false'; property 'energyvfx.disableParcelNearEye', project.findProperty('disableParcelNearEye') ?: 'false'; property 'energyvfx.disableZoomParcelCull', project.findProperty('disableZoomParcelCull') ?: 'false'; property 'energyvfx.flowQa', project.findProperty('flowQa') ?: 'false'; property 'energyvfx.firstPersonQa', project.findProperty('firstPersonQa') ?: 'false'; property 'energyvfx.qa.width', project.findProperty('qaWidth') ?: '960'; property 'energyvfx.qa.height', project.findProperty('qaHeight') ?: '540'; property 'energyvfx.perfCompare', project.findProperty('perfCompare') ?: 'false'; property 'energyvfx.qualityQa', project.findProperty('qualityQa') ?: 'false'; property 'energyvfx.qa.output', project.file('../evidence/game').absolutePath; args '--width', (project.findProperty('qaWidth') ?: '960'), '--height', (project.findProperty('qaHeight') ?: '540'), '--username', 'EnergyVfxQA'; if(project.hasProperty('nativeDir')) { property 'org.lwjgl.librarypath', project.property('nativeDir') }; mods { energyvfx { source sourceSets.main } } } }
}
dependencies { minecraft 'net.minecraftforge:forge:1.20.1-47.4.10' }
tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }
tasks.named('jar', Jar).configure { from('LICENSE') { into 'META-INF' }; finalizedBy 'reobfJar' }
''',encoding='utf-8')
(FORGE/'gradle.properties').write_text('org.gradle.jvmargs=-Xmx3G\norg.gradle.daemon=false\n',encoding='utf-8')
resource=FORGE/'src/main/resources'
(resource/'META-INF').mkdir(parents=True,exist_ok=True)
(resource/'META-INF/mods.toml').write_text('''modLoader="javafml"
loaderVersion="[47,)"
license="MIT"
[[mods]]
modId="energyvfx"
version="0.2.5"
displayName="Energy VFX Workbench Runtime"
description="Client volumetric energy and body/equipment aura renderer consuming energy-vfx/2 recipes."
[[dependencies.energyvfx]]
modId="forge"
mandatory=true
versionRange="[47.4.10,)"
ordering="NONE"
side="CLIENT"
[[dependencies.energyvfx]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.21)"
ordering="NONE"
side="CLIENT"
''',encoding='utf-8')
(resource/'pack.mcmeta').write_text('{"pack":{"pack_format":15,"description":"Energy VFX runtime"}}',encoding='utf-8')
assets=resource/'assets/energyvfx';(assets/'shaders').mkdir(parents=True,exist_ok=True)
for name in ['noise','field','scene','volume','blur','composite','flow-cache']:shutil.copy2(ROOT/(name+'.glsl'),assets/'shaders'/(name+'.glsl'))
shutil.copy2(ROOT/'recipe-schema.json',assets/'recipe-schema.json')
shutil.copy2(ROOT/'presets/1.json',assets/'recipe.json')
shutil.copy2(ROOT/'presets/9.json',assets/'ghost-blue.json')
shutil.copytree(ROOT/'presets',assets/'presets',dirs_exist_ok=True)
print('Forge assets synchronized with browser kernels.')
