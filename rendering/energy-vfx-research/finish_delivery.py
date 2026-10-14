from pathlib import Path
import json,zipfile,hashlib,shutil
R=Path(__file__).parent
data=json.loads((R/'catalog.json').read_text('utf8'))
initial=R/'source-snapshot.json'
backup=R/'evidence'/'api-first-attempt.json'
if initial.exists() and not backup.exists():shutil.copy2(initial,backup)
initial.write_text(json.dumps([{'id':s['id'],'repo':s['repo'],'branch':s['branch'],'commit':s['commit'],'checked_date':s['checked_date'],'license':s['license'],'license_url':s.get('license_url'),'verified_entries':len(s['code'])} for s in data['sources']],ensure_ascii=False,indent=2),encoding='utf8')
SK=R.parent/'skill'/'minecraft-energy-vfx-research'
out=R.parent/'deliverables'/'minecraft-energy-vfx-research-skill.zip'
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as z:
    for p in SK.rglob('*'):
        if p.is_file():z.write(p,Path(SK.name)/p.relative_to(SK))
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    assert all(not n.lower().endswith(('.mp4','.jpg','.png','.java','.glsl','.shader')) for n in z.namelist())
manifest=json.loads((R/'evidence'/'source-manifest.json').read_text('utf8'))
for row in manifest:
    p=Path(row['path']);assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256']
assert len(data['sources'])==17 and len(manifest)==46
print('Verified source hashes:',len(manifest))
print('Skill archive:',str(out),'bytes:',out.stat().st_size)
