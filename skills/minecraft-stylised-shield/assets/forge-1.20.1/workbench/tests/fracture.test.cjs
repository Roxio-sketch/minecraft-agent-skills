'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { ShieldLifecycle, fractureMesh, freezeSurface } = require('../fracture-core.js');
const settings = { shardCount: 24, shatterSpeed: 2.8, shatterSpin: 3 };
const key = q => q.map(v => Math.round(v * 1e5)).join(',');
const edgeKey = (a,b) => a < b ? `${a}|${b}` : `${b}|${a}`;
const triangleKey = q => q.map(key).sort().join('|');
const add = (map,k) => map.set(k,(map.get(k)||0)+1);

function fixture(shape, columns = 16, rows = 8) {
 const positions = [], normals = [], uvs = [], indices = [];
 for (let y=0;y<=rows;y++) for (let x=0;x<=columns;x++) {
  const u=x/columns,v=y/rows;
  const q=shape==='sphere' ? [2*Math.sin(v*Math.PI)*Math.cos(u*Math.PI*2),2*Math.cos(v*Math.PI),2*Math.sin(v*Math.PI)*Math.sin(u*Math.PI*2)] : [(u-.5)*4,(v-.5)*3,0];
  positions.push(...q);normals.push(...(shape==='sphere'?q.map(n=>n/2):[0,0,1]));uvs.push(u,v);
 }
 for (let y=0;y<rows;y++) for(let x=0;x<columns;x++) {
  const a=y*(columns+1)+x,b=a+1,c=a+columns+1,d=c+1;
  indices.push(a,b,c,b,d,c);
 }
 return {positions,normals,uvs,indices};
}
function fracture(g, overrides={}, seed) {
 return fractureMesh(g.positions,g.normals,g.uvs,g.indices,[0,0,2],{...settings,...overrides},seed);
}
function areaOf(q) {
 const ab=q[1].map((v,k)=>v-q[0][k]),ac=q[2].map((v,k)=>v-q[0][k]);
 return Math.hypot(ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0])*.5;
}
function sourceTriangles(g) {
 const triangles = [];
 for(let i=0;i<g.indices.length;i+=3) {
  const q=g.indices.slice(i,i+3).map(n=>[...g.positions.slice(n*3,n*3+3),...g.normals.slice(n*3,n*3+3),...g.uvs.slice(n*2,n*2+2)]);
  const area=areaOf(q.map(v=>v.slice(0,3)));
  if(area>1e-7)triangles.push({q,area,outputArea:0});
 }
 return triangles;
}
function containsRecord(source,record) {
 const [a,b,c]=source.q;const ab=b.slice(0,3).map((v,k)=>v-a[k]),ac=c.slice(0,3).map((v,k)=>v-a[k]),ap=record.slice(0,3).map((v,k)=>v-a[k]);
 const dot=(x,y)=>x.reduce((n,v,k)=>n+v*y[k],0);
 const aa=dot(ab,ab),bb=dot(ac,ac),cross=dot(ab,ac),apb=dot(ab,ap),apc=dot(ac,ap),denom=aa*bb-cross*cross;
 const u=(bb*apb-cross*apc)/denom,v=(aa*apc-cross*apb)/denom;
 if(u<-.00001||v<-.00001||u+v>1.00001)return false;
 // The exact source plane and every interpolated normal/UV must be retained.
 return record.slice(0,8).every((value,k)=>Math.abs(value-(a[k]+(b[k]-a[k])*u+(c[k]-a[k])*v))<.000005);
}

test('the depleted red shell survives until the following accepted hit',()=>{
 const life=new ShieldLifecycle();let health=1,breaks=0;
 for(let i=0;i<13;i++) {
  const result=life.hitAt(health,.08,[0,0,2],2.4);health=result.life;
  assert.equal(result.accepted,true);assert.equal(result.shattered,false);
 }
 assert.equal(health,0);assert.equal(life.state,'intact');
 const point=[1,0,1];const result=life.hitAt(health,.08,point,2.4);
 breaks+=Number(result.shattered);assert.equal(life.state,'shattering');point[0]=9;
 assert.deepEqual(life.hit,[1,0,1]);
 for(let i=0;i<20;i++) {
  const ignored=life.hitAt(0,.08,[0,0,2],2.4);breaks+=Number(ignored.shattered);
  assert.equal(ignored.accepted,false);
 }
 assert.equal(breaks,1);
});

test('pause, bounded duration, completion and reset preserve lifecycle invariants',()=>{
 const life=new ShieldLifecycle();life.hitAt(0,.08,[0,0,2],2.4);
 life.tick(0);life.tick(-1);assert.equal(life.age,0);assert.equal(life.state,'shattering');
 life.tick(.4);assert.equal(life.age,.4);life.tick(20);
 assert.equal(life.age,2.4);assert.equal(life.state,'broken');
 assert.equal(life.hitAt(0,.08,[0,0,2],2.4).accepted,false);
 life.reset();assert.equal(life.state,'intact');assert.equal(life.age,0);assert.equal(life.hit,null);
 assert.equal(life.hitAt(.1,.1,[0,0,2],2.4).shattered,false);
 assert.equal(life.hitAt(0,.1,[0,0,2],2.4).shattered,true);
});

test('zero damage keeps a healthy shell intact',()=>{
 const life=new ShieldLifecycle();
 for(let i=0;i<20;i++)assert.deepEqual(life.hitAt(1,0,[0,0,2],2.4),{accepted:true,life:1,shattered:false});
 assert.equal(life.state,'intact');
});

for(const shape of ['plane','sphere'])test(`${shape} clipping conserves surface area and source attributes, and only outlines patch boundaries`,()=>{
 const g=fixture(shape),result=fracture(g),source=sourceTriangles(g);const seen=new Map(),patches=new Map();
 assert.equal(result.count,24);assert.equal(result.vertices.length%57,0);assert.equal(result.outlines.length%38,0);
 for(const v of result.vertices)assert.ok(Number.isFinite(v));
 for(const v of result.outlines)assert.ok(Number.isFinite(v));
 for(let i=0;i<result.vertices.length;i+=57) {
  const records=[0,19,38].map(j=>Array.from(result.vertices.slice(i+j,i+j+19)));
  const patch=records[0].slice(8).join(',');
  assert.deepEqual(records[0].slice(8),records[1].slice(8));assert.deepEqual(records[0].slice(8),records[2].slice(8));
  add(seen,triangleKey(records.map(v=>v.slice(0,3))));
  const original=source.find(tri=>records.every(record=>containsRecord(tri,record)));
  assert.ok(original,'each clipped triangle lies wholly within a single source triangle, with correct normal/UV interpolation');
  original.outputArea+=areaOf(records.map(v=>v.slice(0,3)));
  if(!patches.has(patch))patches.set(patch,new Map());
  const edges=patches.get(patch),q=records.map(v=>key(v.slice(0,3)));
  for(let k=0;k<3;k++)add(edges,edgeKey(q[k],q[(k+1)%3]));
  const axis=records[0].slice(14,17);assert.ok(Math.abs(Math.hypot(...axis)-1)<1e-5);
 }
 assert.ok([...seen.values()].every(count=>count===1),'no duplicate surface triangles');
 for(const original of source)assert.ok(Math.abs(original.area-original.outputArea)<.00001,'each source triangle is fully partitioned');
 assert.equal(patches.size,result.count);
 const expected=new Map();for(const [patch,edges] of patches)for(const [edge,count]of edges)if(count===1)add(expected,patch+'|'+edge);
 const actual=new Map();
 for(let i=0;i<result.outlines.length;i+=38) {
  const a=Array.from(result.outlines.slice(i,i+19)),b=Array.from(result.outlines.slice(i+19,i+38));
  assert.deepEqual(a.slice(8),b.slice(8));
  add(actual,a.slice(8).join(',')+'|'+edgeKey(key(a.slice(0,3)),key(b.slice(0,3))));
 }
 assert.deepEqual(actual,expected);
});

test('zero initial spin retains angular bases so the animation uniform can enable rotation live',()=>{
 const g=fixture('plane'),zero=fracture(g,{shatterSpin:0}),moving=fracture(g,{shatterSpin:3});
 assert.deepEqual(zero,moving);
 for(let i=18;i<zero.vertices.length;i+=19)assert.ok(Math.abs(zero.vertices[i])>=.45);
});

test('seeded grouping is deterministic and shard count is capped by available triangles',()=>{
 const g=fixture('plane',2,2),a=fracture(g,{shardCount:240},5),b=fracture(g,{shardCount:240},5),c=fracture(g,{shardCount:240},6);
 assert.equal(a.count,8);assert.deepEqual(a,b);assert.notDeepEqual(a.vertices,c.vertices);
 const zero=fracture({positions:[0,0,0,0,0,0,0,0,0],normals:[0,0,1,0,0,1,0,0,1],uvs:[0,0,0,0,0,0],indices:[0,1,2]});
 assert.equal(zero.count,0);assert.equal(zero.vertices.length,0);assert.equal(zero.outlines.length,0);
});

const deformation = { distortion: .4, distortionScale: 2.2, distortionSpeed: .5, pulseAmplitude: .2, pulseFrequency: .5 };

test('freezing an undeformed surface is identity and preserves both input arrays',()=>{
 const g=fixture('sphere'),positions=g.positions.slice(),normals=g.normals.slice();
 const frozen=freezeSurface(g.positions,g.normals,{...deformation,distortion:0,pulseAmplitude:0},10);
 assert.ok(frozen.every((value,i)=>value===positions[i]));assert.notEqual(frozen,g.positions);
 assert.deepEqual(g.positions,positions);assert.deepEqual(g.normals,normals);
});

test('pulse freeze displaces exactly along the original normal at known phases',()=>{
 const positions=[1,2,3,-2,1,0],normals=[.6,.8,0,0,0,1],parameters={...deformation,distortion:0};
 for(const [time,offset]of [[0,0],[.5,.2],[1,0],[1.5,-.2]]) {
  const result=freezeSurface(positions,normals,parameters,time);
  // GLSL uses the decimal 6.283185; allow its tiny error at a zero crossing.
  for(let i=0;i<positions.length;i++)assert.ok(Math.abs(result[i]-(positions[i]+normals[i]*offset))<5e-8);
 }
});

test('noise freeze remains normal displacement, bounded, deterministic, and seamless for duplicate vertices',()=>{
 const positions=[1,2,3,1,2,3,-.5,0,1],normals=[.6,.8,0,.6,.8,0,0,0,1],parameters={...deformation,pulseAmplitude:0};
 const result=freezeSurface(positions,normals,parameters,2.3);
 assert.deepEqual(result,freezeSurface(positions,normals,parameters,2.3));
 assert.deepEqual(result.slice(0,3),result.slice(3,6));
 assert.notDeepEqual(result,freezeSurface(positions,normals,parameters,4.7));
 for(let i=0;i<positions.length;i+=3) {
  const delta=result.slice(i,i+3).map((v,k)=>v-positions[i+k]),normal=normals.slice(i,i+3),distance=delta.reduce((s,v,k)=>s+v*normal[k],0);
  assert.ok(Math.abs(distance)<=parameters.distortion*.5+1e-12);
  for(let k=0;k<3;k++)assert.ok(Math.abs(delta[k]-normal[k]*distance)<1e-12);
 }
 assert.deepEqual(positions,[1,2,3,1,2,3,-.5,0,1]);assert.deepEqual(normals,[.6,.8,0,.6,.8,0,0,0,1]);
});
