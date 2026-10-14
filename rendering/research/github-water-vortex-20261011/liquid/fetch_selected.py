"""Fetch a fixed, licensed subset for read-only liquid source study; never runs it."""
from pathlib import Path
import concurrent.futures, hashlib, json, subprocess, urllib.request

ROOT = Path(__file__).resolve().parent
PROJECTS = [
    dict(id='dli-fluid', repo='dli/fluid', commit='ac3ee551ee33caaf4c0aa38da21e2be5562fd5ab', commit_date='2016-06-29T23:06:44+01:00', paths=[
        'LICENSE','README.md','simulator.js','renderer.js',
        'shaders/common.frag','shaders/addforce.frag','shaders/advect.frag',
        'shaders/transfertogrid.frag','shaders/transfertogrid.vert','shaders/normalizegrid.frag',
        'shaders/divergence.frag','shaders/jacobi.frag','shaders/subtract.frag',
        'shaders/transfertoparticles.frag','shaders/extendvelocity.frag','shaders/enforceboundaries.frag',
        'shaders/sphere.vert','shaders/sphere.frag','shaders/spheredepth.frag','shaders/sphereao.frag','shaders/composite.frag']),
    dict(id='particles4all', repo='matsuoka-601/Particles4All', commit='58d6fa6d2c50e3f58da5c7a6f9b885ce26c485f0', commit_date='2026-08-24T23:00:35+09:00', paths=[
        'LICENSE','README.md','src/sim.js','src/wgsl.js','src/aniso_wgsl.js',
        'src/ssfr.js','src/ssfr_wgsl.js','src/ssfr_composite_wgsl.js','src/render.js']),
    dict(id='splashsurf', repo='InteractiveComputerGraphics/splashsurf', commit='56d3e6ac444cf8540c238a4391483955a9484210', commit_date='2026-06-02T21:02:40+02:00', paths=[
        'LICENSE','README.md','splashsurf_lib/README.md','splashsurf_lib/src/reconstruction.rs',
        'splashsurf_lib/src/density_map.rs','splashsurf_lib/src/marching_cubes.rs',
        'splashsurf_lib/src/marching_cubes/triangulation.rs','splashsurf_lib/src/postprocessing.rs',
        'splashsurf_lib/src/kernel.rs','splashsurf_lib/examples/minimal_sph_levelset.rs']),
]

def fetch(task):
    project, path = task
    url = f"https://raw.githubusercontent.com/{project['repo']}/{project['commit']}/{path}"
    data = urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Codex-local-source-research'}),timeout=40).read()
    if len(data)>800_000: raise ValueError(f'Unexpected large source: {path}')
    out = ROOT/project['id']/path
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_bytes(data)
    return dict(path=path,local_path=out.relative_to(ROOT).as_posix(),bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),
                github_url=f"https://github.com/{project['repo']}/blob/{project['commit']}/{path}",raw_url=url,license='MIT')

if __name__=='__main__':
    jobs=[(p,x) for p in PROJECTS for x in p['paths']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: results=list(pool.map(fetch,jobs))
    assert sum(x['bytes'] for x in results)<3_000_000
    projects=[]
    for p in PROJECTS:
        files=[x for x in results if x['local_path'].startswith(p['id']+'/')]
        projects.append({k:v for k,v in p.items() if k!='paths'}|dict(license='MIT',license_scope='Selected upstream source and documentation only; external dependencies and referenced media not acquired.',files=files))
    manifest=dict(schema='github-liquid-source-study/1',acquired_date='2026-10-11',projects=projects,
                  total_files=len(results),total_bytes=sum(x['bytes'] for x in results),
                  acquisition='GitHub repository pages; git ls-remote HEAD and filtered no-checkout metadata; immutable raw commit URLs (API rate limit 403).',
                  execution='No downloaded source was executed. No install, build, npm, cargo or shader execution.',
                  exclusions=['.git directories','binary assets','HDR panoramas','videos','particle simulation data','third-party dependencies'])
    (ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'files':manifest['total_files'],'bytes':manifest['total_bytes'],'projects':len(projects)}))
