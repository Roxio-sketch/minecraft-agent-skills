(function(root){
'use strict';
const length=v=>Math.hypot(...v),unit=v=>{const n=length(v)||1;return v.map(x=>x/n)},sub=(a,b)=>a.map((v,i)=>v-b[i]);
function random(seed){return ()=>{seed|=0;seed=seed+0x6D2B79F5|0;let t=Math.imul(seed^seed>>>15,1|seed);t=t+Math.imul(t^t>>>7,61|t)^t;return ((t^t>>>14)>>>0)/4294967296;};}
const fract=x=>x-Math.floor(x),mix=(a,b,t)=>a+(b-a)*t;
function hash3(q){q=q.map(v=>fract(v*.1031));const d=q[0]*(q[1]+33.33)+q[1]*(q[2]+33.33)+q[2]*(q[0]+33.33);q=q.map(v=>v+d);return fract((q[0]+q[1])*q[2])}
function noise3(q){const i=q.map(Math.floor),f=q.map(v=>{const t=fract(v);return t*t*(3-2*t)}),h=(x,y,z)=>hash3([i[0]+x,i[1]+y,i[2]+z]);return mix(mix(mix(h(0,0,0),h(1,0,0),f[0]),mix(h(0,1,0),h(1,1,0),f[0]),f[1]),mix(mix(h(0,0,1),h(1,0,1),f[0]),mix(h(0,1,1),h(1,1,1),f[0]),f[1]),f[2])}
// Freeze the actual deformed surface at the breaking hit before rigid motion.
function freezeSurface(positions,normals,settings,time){const out=positions.slice(),pulse=Math.sin(time*settings.pulseFrequency*6.283185)*settings.pulseAmplitude;for(let k=0;k<positions.length;k+=3){const q=positions.slice(k,k+3).map(v=>v*settings.distortionScale+time*settings.distortionSpeed),offset=(noise3(q)-.5)*settings.distortion+pulse;for(let j=0;j<3;j++)out[k+j]+=normals[k+j]*offset}return out}
class ShieldLifecycle{
 constructor(){this.reset()}
 reset(){this.state='intact';this.age=0;this.duration=0;this.hit=null}
 hitAt(life,damage,point,duration){
  if(this.state!=='intact')return {accepted:false,life,shattered:false};
  if(life>1e-6)return {accepted:true,life:Math.max(0,life-damage)<1e-6?0:Math.max(0,life-damage),shattered:false};
  this.state='shattering';this.age=0;this.duration=duration;this.hit=point.slice();
  return {accepted:true,life:0,shattered:true};
 }
 tick(dt){if(this.state==='shattering'){this.age=Math.min(this.duration,this.age+Math.max(0,dt));if(this.age>=this.duration)this.state='broken'}}
}
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
function triangleArea(q){const ab=sub(q[1].slice(0,3),q[0].slice(0,3)),ac=sub(q[2].slice(0,3),q[0].slice(0,3));return length([ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]])*.5}
function clipPolygon(poly,n,d){
 const out=[];for(let i=0;i<poly.length;i++){const a=poly[i],b=poly[(i+1)%poly.length],da=dot(a,n)-d,db=dot(b,n)-d;
  if(da<=0)out.push(a);
  if((da<=0)!==(db<=0)){const t=da/(da-db);out.push(a.map((v,j)=>v+(b[j]-v)*t))}
 }return out;
}
// Cut actual surface triangles by Voronoi bisectors for clean polygon borders.
// Every polygon retains its source normals and UVs, and its cell moves rigidly.
function fractureMesh(positions,normals,uvs,indices,hit,settings,seed=20261012){
 const rng=random(seed),triangles=[];
 for(let k=0;k<indices.length;k+=3){const ids=indices.slice(k,k+3),q=ids.map(i=>positions.slice(i*3,i*3+3));
  const ab=sub(q[1],q[0]),ac=sub(q[2],q[0]);const area=length([ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]])*.5;
  if(area>1e-7){const center=[0,1,2].map(j=>(q[0][j]+q[1][j]+q[2][j])/3);triangles.push({area,center,radius:Math.max(...q.map(v=>length(sub(v,center)))),vertices:ids.map((id,i)=>[...q[i],...normals.slice(id*3,id*3+3),...uvs.slice(id*2,id*2+2)])})}
 }
 const count=Math.min(Math.round(settings.shardCount),triangles.length);if(!count)return {vertices:new Float32Array(),outlines:new Float32Array(),count:0};
 const centers=[triangles[Math.floor(rng()*triangles.length)].center],distance=new Float64Array(triangles.length).fill(Infinity);
 for(let s=1;s<count;s++){let far=-1,best=0;const last=centers[centers.length-1];for(let j=0;j<triangles.length;j++){const d=length(sub(triangles[j].center,last));distance[j]=Math.min(distance[j],d*d);const score=distance[j]*(.88+rng()*.24);if(score>far){far=score;best=j}}centers.push(triangles[best].center)}
 const planes=centers.map(a=>centers.map(b=>({n:sub(b,a),d:(dot(b,b)-dot(a,a))*.5})));
 const groups=centers.map(()=>[]);
 for(const tri of triangles){
  const distances=centers.map(c=>length(sub(tri.center,c))),nearest=Math.min(...distances),candidates=[];
  for(let i=0;i<centers.length;i++)if(distances[i]<=nearest+2*tri.radius+1e-6)candidates.push(i);
  for(const i of candidates){
   // Triangle bounds exclude cells that cannot touch this surface triangle.
   let poly=tri.vertices;for(const j of candidates){if(poly.length<3)break;if(i!==j){const plane=planes[i][j];poly=clipPolygon(poly,plane.n,plane.d)}}
   for(let j=1;j+1<poly.length;j++){const vertices=[poly[0],poly[j],poly[j+1]],area=triangleArea(vertices);if(area>1e-10)groups[i].push({vertices,area,center:[0,1,2].map(k=>(vertices[0][k]+vertices[1][k]+vertices[2][k])/3)})}
  }
 }
 const vertices=[],outlines=[];let actual=0;
 // Match seams by position, not duplicated UV/pole vertex indices.
 const key=v=>v.slice(0,3).map(x=>Math.round(x*1e5)).join(',');
 for(const patch of groups){if(!patch.length)continue;actual++;const area=patch.reduce((a,t)=>a+t.area,0),center=[0,1,2].map(j=>patch.reduce((a,t)=>a+t.center[j]*t.area,0)/area);
  const n=unit([0,1,2].map(j=>patch.reduce((a,t)=>a+t.vertices.reduce((v,q)=>v+q[j+3],0)*t.area/3,0)));
  const tangent=unit([rng()*2-1,rng()*2-1,rng()*2-1]),away=unit(sub(center,hit));
  const velocity=n.map((v,j)=>(v*(.6+rng()*.5)+away[j]*.35+tangent[j]*.28)*settings.shatterSpeed);
  const axis=unit([rng()*2-1,rng()*2-1,rng()*2-1]),delay=.045+Math.min(1,length(sub(center,hit))/4.5)*.22,spin=(rng()<.5?-1:1)*(.45+rng());
  const append=(dst,v)=>dst.push(...v,...center,...velocity,...axis,delay,spin);
  const edges=new Map();for(const tri of patch){for(const v of tri.vertices)append(vertices,v);for(let j=0;j<3;j++){const a=tri.vertices[j],b=tri.vertices[(j+1)%3],ka=key(a),kb=key(b);if(ka===kb)continue;const ek=ka<kb?ka+'|'+kb:kb+'|'+ka;const edge=edges.get(ek);if(edge)edge.count++;else edges.set(ek,{a,b,count:1})}}
  for(const edge of edges.values())if(edge.count===1){append(outlines,edge.a);append(outlines,edge.b)}
 }
 return {vertices:new Float32Array(vertices),outlines:new Float32Array(outlines),count:actual};
}
const api={ShieldLifecycle,fractureMesh,freezeSurface};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.ShieldFracture=api;
})(typeof globalThis!=='undefined'?globalThis:this);
