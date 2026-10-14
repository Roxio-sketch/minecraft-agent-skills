"""Record actual read scopes and verify acquired bytes against Git object IDs."""
from pathlib import Path
import hashlib, json, subprocess

ROOT=Path(__file__).resolve().parent
SCOPES={
 'dli-fluid':[
  ('LICENSE',[[1,21]],'MIT copyright and grant'),('README.md',[[1,8]],'Upstream purpose and method'),
  ('simulator.js',[[30,69],[274,291],[294,368],[369,461],[485,587]],'GPU types; PIC/FLIP pipeline; pressure; particle advection'),
  ('shaders/addforce.frag',[[1,42]],'Gravity and mouse force; potential custom vortex injection point'),
  ('shaders/advect.frag',[[1,59]],'Staggered velocity sampling; RK2; random displacement; commented CFL clamp'),
  ('shaders/transfertoparticles.frag',[[1,50]],'PIC versus FLIP velocity update'),
  ('shaders/jacobi.frag',[[1,34]],'Pressure iterative stencil and air-cell handling'),
  ('renderer.js',[[274,317]],'Sphere instance rendering; not a common fluid mesh'),
  ('shaders/composite.frag',[[1,77]],'Normal/speed/AO/shadow composite; not refractive continuous water'),
 ],
 'particles4all':[
  ('LICENSE',[[1,21]],'MIT copyright and grant'),('README.md',[[1,51]],'PBF/WebGPU; anisotropic surface; narrow-range filtering'),
  ('src/sim.js',[[470,603]],'Grid sorting, iterative constraints, finish pass'),
  ('src/wgsl.js',[[61,84],[250,337],[477,610]],'Predicted position, velocity update, surface tension, lambda/delta'),
  ('src/aniso_wgsl.js',[[1,159]],'Weighted covariance; eigenbasis; support/extent clamps; neighbor fallback'),
  ('src/ssfr.js',[[1,83],[174,301]],'Depth/thickness/filter pipelines, radius setup, render bindings'),
  ('src/ssfr_wgsl.js',[[1,183],[207,338]],'Ellipsoid depth, sphere thickness, depth filter and empty pixel return'),
  ('src/ssfr_composite_wgsl.js',[[1,45],[70,260],[265,330]],'Nearest-depth normal reconstruction, Fresnel, refraction and thickness absorption'),
 ],
 'splashsurf':[
  ('LICENSE',[[1,21]],'MIT copyright and grant'),('README.md',[[10,111]],'Particle-to-mesh purpose, parameters, subdomain approach and nondeterminism note'),
  ('splashsurf_lib/src/reconstruction.rs',[[114,192]],'Neighbor search, density map and triangulation pipeline'),
  ('splashsurf_lib/src/density_map.rs',[[150,188],[520,631],[635,730]],'Density sum; support bounds; domain margins; per-particle scalar contributions'),
  ('splashsurf_lib/src/marching_cubes.rs',[[90,126]],'Iso-surface triangulation entry'),
  ('splashsurf_lib/src/marching_cubes/triangulation.rs',[[21,53]],'Triangle connectivity loop'),
  ('splashsurf_lib/src/postprocessing.rs',[[1,90],[105,163]],'Weighted Laplacian/normal smoothing; limited cleanup inspection'),
 ],
}
ANCHORS={
 'dli-fluid':[
  ('simulate','simulator.js',274,587),('force injection point','shaders/addforce.frag',26,41),
  ('PIC/FLIP mixing','shaders/transfertoparticles.frag',35,49),('RK2 advection','shaders/advect.frag',39,58),
  ('pressure Jacobi passes','simulator.js',485,533),('particle sphere renderer','renderer.js',274,317)],
 'particles4all':[
  ('constraint dispatch loop','src/sim.js',473,595),('lambdaWGSL (compression-only condition)','src/wgsl.js',477,542),
  ('deltaWGSL','src/wgsl.js',545,610),('tensionWGSL','src/wgsl.js',290,334),
  ('anisoWGSL','src/aniso_wgsl.js',66,157),('hitEllipsoid','src/ssfr_wgsl.js',92,112),
  ('sphere thickness pass','src/ssfr_wgsl.js',150,160),('narrowRange','src/ssfr_wgsl.js',219,233),
  ('empty pixel remains empty','src/ssfr_wgsl.js',242,246),('water optical composite','src/ssfr_composite_wgsl.js',281,330)],
 'splashsurf':[
  ('reconstruct_single_surface_append','splashsurf_lib/src/reconstruction.rs',149,191),
  ('particle_support_loop','splashsurf_lib/src/density_map.rs',677,728),
  ('support margin against boundary holes','splashsurf_lib/src/density_map.rs',600,609),
  ('triangulate_density_map_append','splashsurf_lib/src/marching_cubes.rs',113,125),
  ('par_laplacian_smoothing_inplace','splashsurf_lib/src/postprocessing.rs',17,52)],
}

manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf8'))
verified=[]
for p in manifest['projects']:
 index={'dli-fluid':0,'splashsurf':1,'particles4all':2}[p['id']]
 repo_dir=Path('C:/Users/MAINTAINER/AppData/Local/Temp')/f'codex-liquid-source-{index}-20261011'
 actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo_dir,text=True).strip()
 assert actual==p['commit'],(actual,p['commit'])
 tree=subprocess.check_output(['git','ls-tree','-r','HEAD'],cwd=repo_dir,text=True)
 objects={line.split('\t',1)[1]:line.split()[2] for line in tree.splitlines()}
 for f in p['files']:
  data=(ROOT/f['local_path']).read_bytes()
  blob=hashlib.sha1(f'blob {len(data)}\0'.encode()+data).hexdigest()
  assert blob==objects[f['path']],f['local_path']
  assert len(data)==f['bytes'] and hashlib.sha256(data).hexdigest()==f['sha256'],f['local_path']
  f['upstream_git_blob_sha1']=blob
  f['verified_against_git_tree']=True
  f['read_scope']='Acquired source; not closely read in this study'
  scopes=[(ranges,why) for path,ranges,why in SCOPES[p['id']] if path==f['path']]
  if scopes:
   ranges,why=scopes[0]
   line_count=len(data.decode('utf8').splitlines())
   ranges=[[a,min(b,line_count)] for a,b in ranges]
   f['read_scope']={'line_ranges':ranges,'purpose':why}
  verified.append(f['local_path'])
 p['license_url']=f"https://github.com/{p['repo']}/blob/{p['commit']}/LICENSE"
 p['key_anchors']=[{'symbol':name,'path':path,'start_line':a,'end_line':b,
   'url':f"https://github.com/{p['repo']}/blob/{p['commit']}/{path}#L{a}-L{b}"}
  for name,path,a,b in ANCHORS[p['id']]]
 p['not_run']=True
manifest['verification']={'all_selected_bytes_match_immutable_git_tree':True,'verified_files':len(verified),
 'method':'SHA256 and byte count plus Git blob SHA1 against git ls-tree for the fixed commit; no source execution',
 'notes':'Uninspected files are marked. Selected subsets omit dependencies and are not runnable packages.'}
(ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'verified_files':len(verified),'source_bytes':manifest['total_bytes'],'not_run':True}))
