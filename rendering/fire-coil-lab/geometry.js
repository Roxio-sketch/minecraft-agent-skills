export const TAU = Math.PI * 2;
export const add=(a,b)=>a.map((v,i)=>v+b[i]);
export const sub=(a,b)=>a.map((v,i)=>v-b[i]);
export const mul=(a,s)=>a.map(v=>v*s);
export const dot=(a,b)=>a.reduce((v,x,i)=>v+x*b[i],0);
export const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const norm=a=>mul(a,1/Math.max(1e-8,Math.hypot(...a)));
export const lerp=(a,b,f)=>a.map((v,i)=>v+(b[i]-v)*f);
export function perspective(fov,aspect,near,far){const f=1/Math.tan(fov/2),nf=1/(near-far);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0]);}
export function lookAt(eye,at,up=[0,1,0]){const z=norm(sub(eye,at)),x=norm(cross(up,z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1]);}
export function matmul(a,b){const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;}
export function randomGenerator(seed){let state=(seed>>>0)||1;return()=>{state=(Math.imul(1664525,state)+1013904223)>>>0;return state/4294967296;};}
export function coilPoint(r,s,time,strand,origin=[0,0,0]){
  const seed=(r.seed%997)*.071,wave=s*9.3+time*r.waveSpeed*1.4+seed*3.1+strand*2.9;
  const phase=TAU*(s*r.turns+strand/Math.max(r.strands,1)+time*r.rotationSpeed)+.13*r.turbulence*Math.sin(wave*.71+strand);
  const radius=r.radius+r.waveAmplitude*(.62*Math.sin(wave)+.38*Math.sin(wave*1.73-1.1));
  return [origin[0]+Math.cos(phase)*radius,origin[1]+.06+s*r.height+r.waveAmplitude*.45*Math.sin(wave*1.31),origin[2]+Math.sin(phase)*radius];
}
export function playerPosition(time,moving){return moving?[Math.sin(time*.72)*1.2,0,Math.sin(time*.44)*.45]:[0,0,0];}
// Preview blade proxy; native attachment samples the real rendered baked model.
export function weaponFrames(time,origin,animate=false){
 const a=animate?Math.sin(time*1.7)*.62:0,axis=norm([Math.sin(a),Math.cos(a)*.94,.22]);
 const side=norm(cross([0,0,1],axis)),other=cross(side,axis);
 return [{base:add(origin,[.62,.68,.04]),axis,side,other,length:1.28,radius:.045}];
}
export function weaponPoint(r,frame,s,time,strand){
 const wr={...r,radius:frame.radius+r.weaponRadius,height:frame.length,turns:r.weaponTurns,width:r.weaponWidth,waveAmplitude:r.waveAmplitude*.25};
 const p=coilPoint(wr,s,time,strand);
 return add(frame.base,add(add(mul(frame.side,p[0]),mul(frame.axis,p[1])),mul(frame.other,p[2])));
}
// The detached packets are updated only here, once per fixed 20 Hz tick.
export class Simulation{
  constructor(recipe){this.recipe=recipe;this.moving=false;this.firstPerson=false;this.reset();}
  reset(){this.time=0;this.previousTime=0;this.particles=[];this.rng=randomGenerator(this.recipe.seed);this.wispAcc=0;this.emberAcc=0;this.spawnRejected=0;this.spawned=0;this.origin=playerPosition(0,this.moving);this.previousOrigin=[...this.origin];}
  setRecipe(recipe){const reseed=recipe.seed!==this.recipe.seed||recipe.attachment!==this.recipe.attachment;this.recipe=recipe;if(reseed)this.reset();}
  tick(){const dt=.05,r=this.recipe;this.previousTime=this.time;this.time+=dt;this.previousOrigin=[...this.origin];this.origin=playerPosition(this.time,this.moving);const velocity=mul(sub(this.origin,this.previousOrigin),1/dt);
    this.particles=this.particles.filter(p=>{p.previous=[...p.position];p.age+=dt;if(p.age>=p.life)return false;const curl=r.turbulence*.18;p.velocity[0]+=Math.sin(p.seed*13.7+this.time*3.1+p.age*6)*curl*dt;p.velocity[2]+=Math.cos(p.seed*17.9+this.time*2.7+p.age*5)*curl*dt;p.velocity[1]+=r.lift*.22*dt;p.position=add(p.position,mul(p.velocity,dt));return true;});
    const ramp=Math.min(1,this.time/r.rampTime),strength=this.firstPerson?r.firstPerson:1;
    for(const kind of [1,2]){const key=kind===1?'wispAcc':'emberAcc',rate=kind===1?r.wispRate:r.emberRate;this[key]+=rate*dt*ramp*strength;while(this[key]>=1){this[key]--;if(this.particles.length>=512){this.spawnRejected++;continue;}const s=this.rng(),strand=Math.floor(this.rng()*r.strands),seed=this.rng(),useWeapon=r.attachment===1||(r.attachment===2&&this.rng()<.5),frame=weaponFrames(this.time,this.origin,this.moving)[0],position=useWeapon?weaponPoint(r,frame,s,this.time,strand):coilPoint(r,s,this.time,strand,this.origin),axis=useWeapon?frame.axis:[0,1,0],center=useWeapon?add(frame.base,mul(axis,.06+s*frame.length)):add(this.origin,[0,.06+s*r.height,0]),radial=norm(sub(position,center)),tangent=norm(cross(radial,axis)),life=kind===1?r.wispLife:r.emberLife;const outward=r.outwardSpeed*(.65+this.rng()*.7);const v=add(add(mul(radial,outward),mul(tangent,r.rotationSpeed*.35)),mul(velocity,r.motionInheritance));v[1]+=r.lift*(.55+this.rng()*.7)+(kind===2?.18:0);this.particles.push({kind,seed,life,effectStrength:useWeapon?r.weaponStrength:1,age:0,position,previous:[...position],velocity:v,size:(kind===1?r.wispSize:r.emberSize)*(useWeapon?.65:1)});this.spawned++;}}
  }
}
export function buildFireGeometry(recipe,time,origin,particles,alpha,camera,layers,firstPerson){
  const quads=[],eye=camera.eye,r=recipe,strength=Math.min(1,time/r.rampTime)*(firstPerson?r.firstPerson:1);
  const quad=(points,uvs,meta)=>{const center=mul(points.reduce((a,p)=>add(a,p),[0,0,0]),.25);quads.push({points,uvs,meta,distance:dot(sub(center,eye),sub(center,eye))});};
  if(layers.main){for(let strand=0;strand<r.strands;strand++){const seed=(strand*.381966+(r.seed%997)/997)%1;let arcLength=0;const frames=[];for(let j=0;j<=r.segments;j++){const s=j/r.segments,point=coilPoint(r,s,time,strand,origin),tangent=norm(sub(coilPoint(r,Math.min(1,s+.001),time,strand,origin),coilPoint(r,Math.max(0,s-.001),time,strand,origin))),radial=norm([point[0]-origin[0],0,point[2]-origin[2]]);let side=norm(cross(tangent,radial));if(side[1]<0)side=mul(side,-1);const taper=Math.pow(Math.max(0,Math.sin(Math.PI*s)),.55),width=r.width*(.52+.28*Math.sin(s*TAU*2.3+seed*14+time*.8)+.16*Math.sin(s*TAU*6.1+seed*28-time*.5))*taper,bend=r.waveAmplitude*.32*Math.sin(s*TAU*3.1+seed*20+time*r.waveSpeed*1.9)*taper;frames.push({point,top:add(add(point,mul(side,width)),mul(radial,bend)),radial,width});}for(let j=0;j<r.segments;j++){const a=frames[j],b=frames[j+1],u0=arcLength;arcLength+=Math.hypot(...sub(b.point,a.point));const u1=arcLength,meta=[0,0,seed,strength];quad([a.point,a.top,b.top,b.point],[[u0,0],[u0,1],[u1,1],[u1,0]],meta);const aw=mul(a.radial,a.width*.125),bw=mul(b.radial,b.width*.125);quad([sub(a.point,aw),add(a.point,aw),add(b.point,bw),sub(b.point,bw)],[[u0,0],[u0,1],[u1,1],[u1,0]],meta);}}}
  const forward=norm(sub(camera.at,eye)),right=norm(cross(forward,[0,1,0])),up=norm(cross(right,forward));
  for(const p of particles){if(!(p.kind===1?layers.wisp:layers.ember))continue;const center=lerp(p.previous,p.position,alpha),age=Math.max(0,(p.age-.05+.05*alpha)/p.life),s=p.size*(p.kind===1?1+age*.4:1);let widthAxis=right,heightAxis=up;if(p.kind===1){const projected=add(mul(right,dot(p.velocity,right)),mul(up,dot(p.velocity,up)));heightAxis=norm(add(add(projected,mul(up,.55)),mul(right,.12*Math.sin(p.seed*TAU*3))));widthAxis=norm(cross(forward,heightAxis));}const w=mul(widthAxis,s*.5*(p.kind===1?.65:1)),h=mul(heightAxis,s*(p.kind===1?1.8:.5));quad([sub(sub(center,w),h),sub(add(center,w),h),add(add(center,w),h),add(sub(center,w),h)],[[0,0],[1,0],[1,1],[0,1]],[p.kind/2,age,p.seed,p.effectStrength??1]);}
  quads.sort((a,b)=>b.distance-a.distance);const data=new Float32Array(quads.length*6*9);let cursor=0;for(const q of quads)for(const i of [0,1,2,0,2,3]){data.set(q.points[i],cursor);data.set(q.uvs[i],cursor+3);data.set(q.meta,cursor+5);cursor+=9;}return data;
}
export function cube(out,center,size,color){const [x,y,z]=center,[sx,sy,sz]=size.map(v=>v*.5),p=[[x-sx,y-sy,z-sz],[x+sx,y-sy,z-sz],[x+sx,y+sy,z-sz],[x-sx,y+sy,z-sz],[x-sx,y-sy,z+sz],[x+sx,y-sy,z+sz],[x+sx,y+sy,z+sz],[x-sx,y+sy,z+sz]];const faces=[[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]];const shades=[.75,.9,.7,.84,1,.58];faces.forEach((f,k)=>{for(const i of [0,1,2,0,2,3])out.push(...p[f[i]],...color.map(v=>v*shades[k]));});}
export function sceneGeometry(origin,model=true,frames=[]){const out=[];cube(out,[0,-.095,0],[18,.15,18],[.065,.075,.09]);if(model){const o=origin;cube(out,add(o,[0,1.61,0]),[.4,.4,.4],[.57,.51,.41]);cube(out,add(o,[0,1.01,0]),[.5,.74,.26],[.22,.29,.35]);cube(out,add(o,[-.335,1.03,0]),[.18,.69,.22],[.3,.34,.36]);cube(out,add(o,[.335,1.03,0]),[.18,.69,.22],[.3,.34,.36]);cube(out,add(o,[-.145,.35,0]),[.21,.7,.24],[.19,.22,.25]);cube(out,add(o,[.145,.35,0]),[.21,.7,.24],[.19,.22,.25]);}
 for(const f of frames){const local=[];cube(local,[0,f.length*.51,0],[.08,f.length,.025],[.68,.77,.85]);cube(local,[0,-.11,0],[.05,.22,.055],[.24,.17,.11]);cube(local,[0,.03,0],[.32,.05,.07],[.62,.45,.16]);for(let i=0;i<local.length;i+=6){const p=add(f.base,add(add(mul(f.side,local[i]),mul(f.axis,local[i+1])),mul(f.other,local[i+2])));out.push(...p,...local.slice(i+3,i+6));}}
 return new Float32Array(out);}
export function gridGeometry(){const a=[];for(let i=-16;i<=16;i++){const p=i*.5,color=i%2===0?[.13,.15,.18]:[.095,.11,.135];a.push(-8,-.015,p,...color,8,-.015,p,...color,p,-.015,-8,...color,p,-.015,8,...color);}return new Float32Array(a);}
