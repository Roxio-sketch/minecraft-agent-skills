import json, urllib.request, concurrent.futures, pathlib, re, time
ROOT=pathlib.Path(__file__).parent
REPOS=['stegu/psrdnoise','stegu/webgl-noise','PavelDoGreat/WebGL-Fluid-Simulation','Scrawk/GPU-GEMS-3D-Fluid-Simulation','SebLague/Clouds','TylerDodds/VolumetricCloudsTutorial','keijiro/NoiseBall','keijiro/KinoBloom','keijiro/KvantSwarm','keijiro/KvantWig','FoundryMC/Veil','Low-Drag-MC/Photon','LodestarMC/Lodestone','Low-Drag-MC/Shimmer','effekseer/Effekseer','haxiomic/GPU-Fluid-Experiments']
def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'EnergyVFXResearch','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(req,timeout=40) as r:return r.read()
def task(repo):
    p=ROOT/'sources'/repo.replace('/','--');p.mkdir(parents=True,exist_ok=True)
    result={'repo':repo,'checked_date':'2026-10-02'}
    try:
        meta=json.loads(get('https://api.github.com/repos/'+repo))
        branch=meta['default_branch'];result.update(branch=branch,stars=meta['stargazers_count'],license_api=meta.get('license'),pushed_at=meta['pushed_at'])
        commits=json.loads(get(f'https://api.github.com/repos/{repo}/commits?sha={branch}&per_page=1'))
        sha=commits[0]['sha'];result['commit']=sha
        tree=json.loads(get(f'https://api.github.com/repos/{repo}/git/trees/{sha}?recursive=1'))
        result['tree_truncated']=tree.get('truncated',False)
        (p/'tree.json').write_text(json.dumps(tree,ensure_ascii=False),encoding='utf-8')
        files=[e['path'] for e in tree['tree'] if e['type']=='blob']
        docs=[f for f in files if '/' not in f and re.search(r'^(license|copying|readme)',f,re.I)]
        code=[f for f in files if re.search(r'\.(glsl|shader|compute|fsh|vsh|hlsl|java|cs|js)$',f,re.I) and not re.search(r'(\.min\.|Editor/|Test)',f)]
        (p/'code-index.txt').write_text('\n'.join(code),encoding='utf-8')
        result['fetched']=[]
        for f in docs:
            try:
                body=get(f'https://raw.githubusercontent.com/{repo}/{sha}/{f}')
                (p/f).write_bytes(body);result['fetched'].append(f)
            except Exception as e:result.setdefault('file_errors',[]).append(f+': '+str(e))
        result['source_count']=len(code)
        (p/'metadata.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
        print(repo,branch,sha[:10],result['license_api'],len(code),flush=True)
    except Exception as e:
        result['error']=str(e);print(repo,'ERROR',e,flush=True)
    return result
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(task,REPOS))
(ROOT/'source-snapshot.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
