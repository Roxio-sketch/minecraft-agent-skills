from pathlib import Path
import shutil
root=Path(__file__).parent
java=root/'forge/src/main/java/dev/energyvfx'
backup=root/'evidence/performance/source-before'
backup.mkdir(parents=True,exist_ok=True)
for name in ['Gpu.java','EnergyVfx.java','AuraEmitter.java']:
    if not (backup/name).exists(): shutil.copy2(java/name,backup/name)
for name in ['noise','field','scene','volume','blur','composite']:
    if not (backup/(name+'.glsl')).exists():shutil.copy2(root/(name+'.glsl'),backup/(name+'.glsl'))
    target=root/'forge/src/main/resources/assets/energyvfx/benchmark/shaders'/(name+'.glsl')
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(backup/(name+'.glsl'),target)
gpu=(backup/'Gpu.java').read_text(encoding='utf-8')
gpu=gpu.replace('Recipe.resource("shaders/"+name+".glsl")','Recipe.resource("benchmark/shaders/"+name+".glsl")')
gpu=gpu.replace('static void render(RenderLevelStageEvent event)throws Exception{','static void render(RenderLevelStageEvent event)throws Exception{\n  long cpuStarted=System.nanoTime();PerfTrace.mark(1);')
gpu=gpu.replace('event.getPartialTick()','PerformanceQa.partial(event.getPartialTick())')
gpu=gpu.replace('GL11.GL_NEAREST);\n   RenderSystem','GL11.GL_NEAREST);PerfTrace.mark(2);\n   RenderSystem')
gpu=gpu.replace('scene.getDepthTextureId(),1);draw();','scene.getDepthTextureId(),1);draw();PerfTrace.mark(3);')
gpu=gpu.replace('u[22]/width,0);draw();','u[22]/width,0);draw();PerfTrace.mark(4);')
gpu=gpu.replace('0,u[22]/height);draw();','0,u[22]/height);draw();PerfTrace.mark(5);')
gpu=gpu.replace('u[21]);draw();EnergyVfx.Client.draws++','u[21]);draw();PerfTrace.mark(6);EnergyVfx.Client.draws++')
gpu=gpu.replace('\n static void freeTargets()', '\n static void freeTargets()')
gpu=gpu.replace('  }\n }\n static void freeTargets()', '  }finally{PerfTrace.cpu("vfxSubmitCpuMs",System.nanoTime()-cpuStarted);}\n }\n static void freeTargets()')
(java/'Gpu.java').write_text(gpu,encoding='utf-8')
client=(java/'EnergyVfx.java').read_text(encoding='utf-8')
client=client.replace('if(active&&!m.isPaused()){ticks++;if(recipe.values[39]>.5)AuraEmitter.tick();}', 'if(active&&!m.isPaused()&&!PerformanceQa.freeze){ticks++;if(recipe.values[39]>.5){long cpuStart=System.nanoTime();AuraEmitter.tick();PerfTrace.cpu("emitterTickCpuMs",System.nanoTime()-cpuStart);}}')
client=client.replace('Gpu.render(e);','if(PerformanceQa.ENABLED&&!PerformanceQa.optimized)Gpu.render(e);else OptimizedGpu.render(e);')
client=client.replace('Gpu.release();','Gpu.release();OptimizedGpu.release();')
(java/'EnergyVfx.java').write_text(client,encoding='utf-8')
prepare=root/'prepare_forge.py'
text=prepare.read_text(encoding='utf-8')
text=text.replace("property 'energyvfx.qa.output'", "property 'energyvfx.perfQa', project.findProperty('perfQa') ?: 'false'; property 'energyvfx.perfCompare', project.findProperty('perfCompare') ?: 'false'; property 'energyvfx.qa.output'")
text=text.replace("args '--width', '960', '--height', '540'", "args '--width', (project.findProperty('qaWidth') ?: '960'), '--height', (project.findProperty('qaHeight') ?: '540')")
prepare.write_text(text,encoding='utf-8')
print('Original implementation archived; nonblocking performance QA enabled.')
