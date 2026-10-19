"""Check published Skill metadata, portable entry points and HTML resources."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.request import urlopen
from urllib.parse import urljoin,urlsplit
import ast,importlib.util,json,threading
from datetime import date

ROOT=Path(__file__).resolve().parents[1]
class Resources(HTMLParser):
    def __init__(self):super().__init__();self.urls=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='script' and a.get('src'):self.urls.append(a['src'])
        if tag=='link' and a.get('rel')=='stylesheet' and a.get('href'):self.urls.append(a['href'])

def check():
    failures=[];skills=[];entries=[]
    for folder in sorted((ROOT/'skills').iterdir()):
        if not folder.is_dir():continue
        p=folder/'SKILL.md'
        if not p.exists():failures.append(f'{folder.name}: missing SKILL.md');continue
        text=p.read_text(encoding='utf-8-sig')
        if not text.startswith('---') or f'name: {folder.name}' not in text or 'description:' not in text:failures.append(f'{folder.name}: incomplete metadata')
        skills.append(folder.name)
    for p in (ROOT/'rendering').rglob('*.py'):
        try:ast.parse(p.read_text(encoding='utf-8-sig'),filename=str(p))
        except SyntaxError as e:failures.append(f'{p.relative_to(ROOT)}: {e}')
    spec=importlib.util.spec_from_file_location('rendering_launcher',ROOT/'rendering/启动全部工作台.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    for number,entry in enumerate(module.ENTRIES,1):
        server=None;thread=None;record={'number':number,'name':entry[0],'ok':False,'resources':0}
        try:
            server,url=module.create_server(entry)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            with urlopen(url,timeout=10) as response:
                assert response.status==200
                text=response.read().decode('utf-8-sig')
            assert '<html' in text.lower() or '<!doctype html' in text.lower()
            parser=Resources();parser.feed(text)
            for resource in parser.urls:
                if urlsplit(resource).scheme or resource.startswith('//'):continue
                with urlopen(urljoin(url,resource),timeout=10) as response:assert response.status==200
                record['resources']+=1
            record['ok']=True
        except Exception as e:
            record['error']=str(e);failures.append(f'{entry[0]}: {e}')
        finally:
            if server:server.shutdown();server.server_close()
            if thread:thread.join()
        entries.append(record);print(f"{'PASS' if record['ok'] else 'FAIL'} {number:02d} {entry[0]}",flush=True)
    result={'date':date.today().isoformat(),'scope':'Skill metadata, Python syntax, loopback HTTP entry points and HTML script/CSS resources; no game or visual certification','skills':skills,'entry_count':len(entries),'entries':entries,'failures':failures,'ok':not failures}
    return result

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path);args=parser.parse_args()
    result=check()
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f"{len(result['skills'])} Skills, {result['entry_count']} entries, {len(result['failures'])} failures")
    if result['failures']:print('\n'.join(result['failures']))
    raise SystemExit(0 if result['ok'] else 1)
