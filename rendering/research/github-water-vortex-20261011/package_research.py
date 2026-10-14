"""Validate the static source dossier and package it; do not execute upstream code."""
from pathlib import Path
import hashlib
import json
import re
import zipfile
import argparse

ROOT = Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--out',type=Path,required=True)
args=parser.parse_args()
catalog=json.loads((ROOT/'catalog.json').read_text(encoding='utf-8'))
checks=[]
def check(name,ok,detail=None):
    entry={'name':name,'ok':bool(ok)}
    if detail is not None:entry['detail']=detail
    checks.append(entry)

seen=set()
for project in catalog['projects']:
    check('fixed commit: '+project['repo'],bool(re.fullmatch('[0-9a-f]{40}',project['commit'])))
    for entry in project['files']:
        rel=entry['localPath']; path=(ROOT/rel).resolve()
        check('path inside dossier: '+rel,path.is_relative_to(ROOT.resolve()))
        data=path.read_bytes(); lines=data.decode('utf-8-sig').splitlines()
        check('upstream bytes/hash: '+rel,len(data)==entry['bytes'] and hashlib.sha256(data).hexdigest()==entry['sha256'])
        check('pinned source URL: '+rel,
              entry['sourceUrl']==f"https://github.com/{project['repo']}/blob/{project['commit']}/{entry['path']}")
        check('read line ranges: '+rel,all(1<=a<=b<=len(lines) for a,b in entry['readRanges']))
        check('unique source path: '+rel,rel not in seen);seen.add(rel)
    license_path=ROOT/project['licenseLocalPath']
    license_text=license_path.read_text(encoding='utf-8-sig')
    check('original license: '+project['repo'],'MIT' in license_text and 'Copyright' in license_text)
    if project['repo']=='Scrawk/Tiled-Directional-Flow':
        shader=(ROOT/'flow/Tiled-Directional-Flow/Assets/TiledDirectionalFlow/Shaders/TiledDirectinalFlow.shader').read_text(encoding='utf-8-sig')
        check('Creditware notices retained','Creditware' in license_text and 'Creditware' in shader and 'Frans van Hoesel' in shader)
    for a in project['anchors']:
        lines=(ROOT/a['localPath']).read_text(encoding='utf-8-sig').splitlines()
        line=a['line'];ok=1<=line<=a.get('endLine',line)<=len(lines)
        if a.get('needle'):ok=ok and a['needle'] in lines[line-1]
        check('source anchor: '+project['repo']+'/'+a['symbol'],ok)

liquid=json.loads((ROOT/'liquid/manifest.json').read_text(encoding='utf-8'))
for project in liquid['projects']:
    for f in project['files']:
        data=(ROOT/'liquid'/f['local_path']).read_bytes()
        blob=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
        check('Git blob: liquid/'+f['local_path'],blob==f['upstream_git_blob_sha1'])

authored_docs=[ROOT/'README.md',ROOT/'SEARCH_LOG.md',ROOT/'audit-notes.md',
              ROOT/'flow/findings.md',ROOT/'cloud/findings.md',ROOT/'liquid/findings.md']
for path in authored_docs:
    for url in re.findall(r'\[[^\]]*\]\(([^)]+)\)',path.read_text(encoding='utf-8')):
        if re.match(r'^[a-zA-Z]+:',url) or url.startswith('#'):continue
        target=url.strip('<>').split('#')[0]
        resolved=(path.parent/target).resolve()
        # This invocation writes its report below, including on a failed check.
        check('authored document link: '+path.relative_to(ROOT).as_posix()+'/'+target,
              resolved.exists() or resolved==(ROOT/'verification.json').resolve())
index_report=json.loads((ROOT/'index-verification.json').read_text(encoding='utf-8'))
check('offline browser reader smoke check',index_report['ok'] and index_report['passed']==index_report['total'])
acquisition=json.loads((ROOT/'flow/acquisition-verification.json').read_text(encoding='utf-8'))
check('flow fixed-commit remote byte comparison',acquisition['ok'] and acquisition['passed']==44)
files=[p for p in sorted(ROOT.rglob('*')) if p.is_file() and p.name not in ['verification.json','HASHES.json']]
check('no .git/cache/build/runtime/archives',not any(any(part in {'.git','node_modules','__pycache__','build','run'} for part in p.relative_to(ROOT).parts) or p.suffix.lower() in {'.zip','.jar','.exe','.dll','.png','.jpg','.mp4','.hdr'} for p in files))
report={'schema':'source-dossier-verification/1','date':'2026-10-11',
    'scope':'Local source bytes, fixed versions, licenses, anchors, authored links and offline index; not upstream runtime acceptance',
    'projectCount':catalog['projectCount'],'upstreamFileCount':len(seen),
    'anchorCount':sum(len(p['anchors']) for p in catalog['projects']),
    'passed':sum(c['ok'] for c in checks),'total':len(checks),'ok':all(c['ok'] for c in checks),'checks':checks}
(ROOT/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if not report['ok']:
    print(json.dumps([c for c in checks if not c['ok']],ensure_ascii=False,indent=2))
    raise SystemExit(1)

files=[p for p in sorted(ROOT.rglob('*')) if p.is_file() and p.name!='HASHES.json']
hashes={'scope':'All bundle files except this hash manifest itself',
    'files':[{'path':p.relative_to(ROOT).as_posix(),'bytes':p.stat().st_size,
              'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]}
(ROOT/'HASHES.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
files=[p for p in sorted(ROOT.rglob('*')) if p.is_file()]
args.out.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in files:archive.write(path,ROOT.name+'/'+path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(args.out) as archive:
    assert archive.testzip() is None,'ZIP CRC failure'
    assert len(archive.namelist())==len(files),'ZIP member count mismatch'
    for path in files:
        assert archive.read(ROOT.name+'/'+path.relative_to(ROOT).as_posix())==path.read_bytes(),path
sha=hashlib.sha256(args.out.read_bytes()).hexdigest()
args.out.with_suffix(args.out.suffix+'.sha256').write_text(sha+'  '+args.out.name+'\n',encoding='utf-8')
summary={'ok':True,'projects':catalog['projectCount'],'upstreamFiles':len(seen),
    'anchors':report['anchorCount'],'checks':f"{report['passed']}/{report['total']}",
    'bundleFiles':len(files),'zipBytes':args.out.stat().st_size,'zipSha256':sha,'path':str(args.out)}
print(json.dumps(summary,ensure_ascii=False,indent=2))
