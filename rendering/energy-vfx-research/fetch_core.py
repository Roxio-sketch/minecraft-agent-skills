import pathlib,json,urllib.request,concurrent.futures,re,tarfile,io
R=pathlib.Path(__file__).parent
rules={'stegu--psrdnoise':r'^src/psrdnoise[23]\.glsl$','stegu--webgl-noise':r'^src/(noise3Dgrad|noise4D|cellular3D)\.glsl$','PavelDoGreat--WebGL-Fluid-Simulation':r'^script.js$','Scrawk--GPU-GEMS-3D-Fluid-Simulation':r'^Assets/FluidSim3D/.*\.(cs|compute|shader)$','SebLague--Clouds':r'^Assets/Scripts/Clouds/(Shaders/Clouds.shader|CloudMaster.cs|Noise/Compute/NoiseGenCompute.compute)$','TylerDodds--VolumetricCloudsTutorial':r'^Assets/VolumetricCloudsTutorial/.*\.(shader|cginc|cs|md)$','keijiro--NoiseBall':r'^Assets/NoiseBall/.*\.(shader|cginc|cs)$','keijiro--KinoBloom':r'^Assets/Kino/Bloom/.*\.(shader|cginc|cs)$'}
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'EnergyVFXResearch'}),timeout=40) as f:return f.read()
jobs=[]
for folder,pattern in rules.items():
    p=R/'sources'/folder;m=json.loads((p/'metadata.json').read_text('utf8'));t=json.loads((p/'tree.json').read_text('utf8'))
    for e in t['tree']:
        if e['type']=='blob' and re.search(pattern,e['path']):jobs.append((p,m,e['path']))
def run(j):
    p,m,f=j
    try:
        dest=p/f;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(fetch(f"https://raw.githubusercontent.com/{m['repo']}/{m['commit']}/{f}"));return f
    except Exception as e: return 'ERROR '+f+' '+str(e)
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    results=list(pool.map(run,jobs))
(R/'core-fetch-log.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
print('Downloaded',len([x for x in results if not x.startswith('ERROR')]),'files',flush=True)
for x in results:
    if x.startswith('ERROR'):print(x,flush=True)
page=fetch('https://www.cs.ubc.ca/~rbridson/').decode()
urls=re.findall(r'href=[\"\']([^\"\']*curl[^\"\']*)',page,re.I)
print('Bridson URLs',urls,flush=True)
for url in urls:
    if not re.search(r'\.(tar\.gz|tar\.bz2|zip)$',url):continue
    import urllib.parse
    url=urllib.parse.urljoin('https://www.cs.ubc.ca/~rbridson/',url)
    data=fetch(url);p=R/'sources'/'bridson--curlnoise';p.mkdir(exist_ok=True)
    tar=tarfile.open(fileobj=io.BytesIO(data),mode='r:*')
    paths=[]
    for member in tar.getmembers():
        if member.isfile() and not member.name.startswith('/') and '..' not in pathlib.PurePosixPath(member.name).parts:
            dest=p/member.name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(tar.extractfile(member).read());paths.append(member.name)
    (p/'metadata.json').write_text(json.dumps({'repo':'Bridson curl noise','source_url':url,'checked_date':'2026-10-02','fetched':paths},indent=2),encoding='utf8')
    print('Bridson extracted',len(paths),flush=True)
