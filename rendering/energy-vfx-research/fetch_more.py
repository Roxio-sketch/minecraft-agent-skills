import urllib.request,pathlib,zipfile,io,json,subprocess,concurrent.futures,re
R=pathlib.Path(__file__).parent
projects=[('FoundryMC/Veil','1.20','3592dddfcfcf92b298ec933c0d9fb951a69aba51'),('Low-Drag-MC/Photon','1.20.1','507499fbe5a3fc72bb90993e059848cbb31f13ca'),('LodestarMC/Lodestone','1.20','1980158f99c4ed849753960354972b3c7b18dfcc'),('Low-Drag-MC/Shimmer','1.20','49cb20e7406de63027f8666d6dde9b73d8444f80'),('keijiro/KvantSwarm','master',None),('keijiro/KvantWig','master',None),('haxiomic/GPU-Fluid-Experiments','master',None)]
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'EnergyVFXResearch'}),timeout=90) as f:return f.read()
def task(item):
    repo,branch,sha=item
    p=R/'sources'/repo.replace('/','--');p.mkdir(parents=True,exist_ok=True)
    try:
        if sha is None:
            sha=subprocess.check_output(['git','ls-remote','https://github.com/'+repo+'.git','refs/heads/'+branch],text=True).split()[0]
        data=fetch(f'https://codeload.github.com/{repo}/zip/{sha}')
        z=zipfile.ZipFile(io.BytesIO(data));files=[];stored=[]
        for n in z.namelist():
            if n.endswith('/'):continue
            rel=n.split('/',1)[1];files.append(rel)
            if re.search(r'(\.(java|glsl|shader|compute|hlsl|fsh|vsh|cs|cginc|js|json|gradle|kts|toml|properties|md|txt|frag|vert|hx|h)$|(^|/)(LICENSE|COPYING)(\..*)?$)',rel,re.I) and z.getinfo(n).file_size<2000000 and 'node_modules/' not in rel:
                dest=p/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n));stored.append(rel)
        meta={'repo':repo,'branch':branch,'commit':sha,'checked_date':'2026-10-02','fetched':stored,'archive_bytes':len(data),'source_count':sum(bool(re.search(r'\.(java|glsl|shader|compute|fsh|vsh|cs|js)$',f,re.I)) for f in stored)}
        (p/'metadata.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf8')
        (p/'code-index.txt').write_text('\n'.join(stored),encoding='utf8')
        print(repo,sha[:10],len(stored),len(data),flush=True)
    except Exception as e: print(repo,str(e),flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:list(ex.map(task,projects))
