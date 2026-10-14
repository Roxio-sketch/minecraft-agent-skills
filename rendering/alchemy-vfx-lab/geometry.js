export const add=(a,b)=>a.map((v,i)=>v+b[i]);
export const sub=(a,b)=>a.map((v,i)=>v-b[i]);
export const mul=(a,s)=>a.map(v=>v*s);
export const dot=(a,b)=>a.reduce((v,x,i)=>v+x*b[i],0);
export const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const norm=a=>mul(a,1/Math.max(1e-8,Math.hypot(...a)));
export function perspective(fov,aspect,near,far){const f=1/Math.tan(fov/2),nf=1/(near-far);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0]);}
export function lookAt(eye,at,up=[0,1,0]){const z=norm(sub(eye,at)),x=norm(cross(up,z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1]);}
export function matmul(a,b){const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;}
export function inverseMatrix(m){const a=Array.from({length:4},(_,r)=>[m[r],m[4+r],m[8+r],m[12+r],...Array.from({length:4},(_,c)=>r===c?1:0)]);for(let c=0;c<4;c++){let best=c;for(let r=c+1;r<4;r++)if(Math.abs(a[r][c])>Math.abs(a[best][c]))best=r;[a[c],a[best]]=[a[best],a[c]];const d=a[c][c];if(Math.abs(d)<1e-12)throw new Error('相机矩阵不可逆');for(let k=0;k<8;k++)a[c][k]/=d;for(let r=0;r<4;r++){if(r===c)continue;const f=a[r][c];for(let k=0;k<8;k++)a[r][k]-=f*a[c][k];}}return Float32Array.from(Array.from({length:16},(_,i)=>a[i%4][4+Math.floor(i/4)]));}
export function cube(out,center,size,color){const [x,y,z]=center,[sx,sy,sz]=size.map(v=>v*.5),p=[[x-sx,y-sy,z-sz],[x+sx,y-sy,z-sz],[x+sx,y+sy,z-sz],[x-sx,y+sy,z-sz],[x-sx,y-sy,z+sz],[x+sx,y-sy,z+sz],[x+sx,y+sy,z+sz],[x-sx,y+sy,z+sz]];[[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]].forEach((face,k)=>{for(const i of [0,1,2,0,2,3])out.push(...p[face[i]],...color.map(v=>v*[.68,.86,.7,.8,1,.55][k]));});}
export function floorGeometry(){const a=[];cube(a,[0,-.095,0],[30,.15,30],[.045,.051,.065]);return new Float32Array(a);}
export function humanGeometry(){const a=[],x=-3.65,z=.25;cube(a,[x,1.59,z],[.37,.42,.37],[.66,.55,.4]);cube(a,[x,1.02,z],[.48,.72,.25],[.14,.24,.28]);cube(a,[x-.32,1.03,z],[.17,.67,.21],[.23,.32,.34]);cube(a,[x+.32,1.03,z],[.17,.67,.21],[.23,.32,.34]);cube(a,[x-.13,.35,z],[.2,.7,.23],[.14,.17,.21]);cube(a,[x+.13,.35,z],[.2,.7,.23],[.14,.17,.21]);return new Float32Array(a);}
export function gridGeometry(){const a=[];for(let i=-10;i<=10;i++){const c=i===0?[.26,.3,.33]:[.11,.13,.16];a.push(-10,-.016,i,...c,10,-.016,i,...c,i,-.016,-10,...c,i,-.016,10,...c);}return new Float32Array(a);}
export function debugGeometry(bounds,anchors=[]){const a=[],c=[.25,.57,.72];for(let axis=0;axis<3;axis++){const other=[0,1,2].filter(k=>k!==axis);for(let b=0;b<4;b++){const p=[0,0,0],q=[0,0,0];p[axis]=bounds.min[axis];q[axis]=bounds.max[axis];for(let k=0;k<2;k++)p[other[k]]=q[other[k]]=(b>>k)&1?bounds.max[other[k]]:bounds.min[other[k]];a.push(...p,...c,...q,...c);}}for(const {point,color=[1,.7,.3]} of anchors){for(let k=0;k<3;k++){const p=[...point],q=[...point];p[k]-=.14;q[k]+=.14;a.push(...p,...color,...q,...color);}}return new Float32Array(a);}
// The preview owns the only clock. Every state change runs at a fixed 20 Hz.
export class Simulation{
 constructor(recipe){this.recipe=recipe;this.stage=1;this.phase=.55;this.reset();}
 reset(){this.time=0;this.previousTime=0;this.ticks=0;}
 setRecipe(recipe){if(recipe.seed!==this.recipe.seed)this.reset();this.recipe=recipe;}
 setStage(stage,phase=this.phase){if(!Number.isInteger(stage)||stage<0||stage>3)throw new Error('阶段必须为 0–3');if(!Number.isFinite(phase)||phase<0||phase>1)throw new Error('阶段进度必须为 0–1');this.stage=stage;this.phase=phase;}
 tick(){this.previousTime=this.time;this.ticks++;this.time=this.ticks*.05;}
 seek(seconds){if(!Number.isFinite(seconds)||seconds<0||seconds>600)throw new Error('时间须在 0–600 秒');this.reset();this.ticks=Math.floor(seconds/.05);this.time=this.ticks*.05;this.previousTime=Math.max(0,this.time-.05);return seconds-this.time;}
}
