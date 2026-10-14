"""Snapshot the actual imported OBJ in world metres, with unchanged UVs/textures."""
from pathlib import Path
import json,math,shutil,hashlib
ROOT=Path(__file__).resolve().parent
HOST=Path(r'E:\五行仙途')
ASSETS=HOST/'src/main/resources/assets/wuxingxiantu'
def main():
 out=ROOT/'model';out.mkdir(parents=True,exist_ok=True)
 source=ASSETS/'models/block/cauldron_structure.obj'
 vertices=[];uvs=[];normals=[];batches={};material=None;faces=0
 def index(s,table):
  i=int(s);return table[i-1 if i>0 else i]
 for line in source.read_text(encoding='utf-8').splitlines():
  data=line.split()
  if not data:continue
  kind=data[0]
  if kind=='v':vertices.append(list(map(float,data[1:4])))
  elif kind=='vt':uvs.append(list(map(float,data[1:3])))
  elif kind=='vn':normals.append(list(map(float,data[1:4])))
  elif kind=='usemtl':material=data[1];batches.setdefault(material,[])
  elif kind=='f':
   faces+=1;face=[]
   for token in data[1:]:
    ref=token.split('/');face.append((index(ref[0],vertices),index(ref[1],uvs) if len(ref)>1 and ref[1] else [0,0],index(ref[2],normals) if len(ref)>2 and ref[2] else None))
   a,b,c=[v[0] for v in face[:3]];ab=[b[i]-a[i] for i in range(3)];ac=[c[i]-a[i] for i in range(3)]
   n=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]];length=math.sqrt(sum(v*v for v in n));n=[v/max(length,1e-12) for v in n]
   for i in range(1,len(face)-1):
    for p,uv,normal in [face[0],face[i],face[i+1]]:batches[material].extend(p+(normal or n)+uv)
 bounds={'min':[min(v[i] for v in vertices) for i in range(3)],'max':[max(v[i] for v in vertices) for i in range(3)]}
 anchors={'fire':[0,.045,0],'material':[0,1.38,0],'mouth':[0,3.2417925,0],'result':[0,4.0017925,0]}
 mesh={'schema':'cauldron-mesh/1','units':'world metres','batches':[{'material':key,'vertices':value} for key,value in batches.items()],'bounds':bounds,'anchors':anchors}
 (out/'mesh.json').write_text(json.dumps(mesh,separators=(',',':')),encoding='utf-8')
 manifest={'sourceProject':str(HOST),'model':str(source),'objSHA256':hashlib.sha256(source.read_bytes()).hexdigest(),'bounds':bounds,'size':[bounds['max'][i]-bounds['min'][i] for i in range(3)],'sourceVertices':len(vertices),'faces':faces,'triangles':sum(len(v)//24 for v in batches.values()),'anchors':anchors,'textureSHA256':{}}
 for name in batches:
  texture=ASSETS/f'textures/block/cauldron/{name}.png';shutil.copy2(texture,out/f'{name}.png');manifest['textureSHA256'][name]=hashlib.sha256(texture.read_bytes()).hexdigest()
 for ext in ['json','obj','mtl']:shutil.copy2(ASSETS/f'models/block/cauldron_structure.{ext}',out/f'cauldron_structure.{ext}')
 (ROOT/'model-audit.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({k:manifest[k] for k in ['size','sourceVertices','triangles','objSHA256']},ensure_ascii=False))
if __name__=='__main__':main()
