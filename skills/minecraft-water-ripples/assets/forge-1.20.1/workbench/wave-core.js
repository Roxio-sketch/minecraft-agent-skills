(function(root){
'use strict';
const defaults={schema:1,grid:65,solver:'velocity',boundary:'absorb',color:'#08616f',width:8,speed:1,damping:.995,radius:.45,strength:.18,maxHeight:.5,opacity:.24,normalStrength:1,shininess:96,reflection:1,rainRate:3};
const limits={width:[2,32],speed:[.1,1],damping:[.90,.9995],radius:[.05,2],strength:[-.5,.5],maxHeight:[.05,1],opacity:[0,1],normalStrength:[0,4],shininess:[8,256],reflection:[0,2],rainRate:[0,20]};
function validate(value){
 if(!value||typeof value!=='object'||Array.isArray(value))throw Error('参数必须是 JSON 对象');
 const p={...defaults,...value};
 if(p.schema!==1)throw Error('schema 必须为 1');
 if(![33,65,97,129].includes(p.grid))throw Error('网格应为 33 / 65 / 97 / 129');
 if(!['velocity','history'].includes(p.solver)||!['absorb','reflect'].includes(p.boundary))throw Error('求解器或边界无效');
 if(typeof p.color!=='string'||!/^#[0-9a-f]{6}$/i.test(p.color))throw Error('颜色格式应为 #RRGGBB');
 for(const [key,[min,max]] of Object.entries(limits))if(typeof p[key]!=='number'||!Number.isFinite(p[key])||p[key]<min||p[key]>max)throw Error(`${key} 应在 ${min}–${max} 之间`);
 return Object.fromEntries(Object.keys(defaults).map(k=>[k,p[k]]));
}
class WaveField{
 constructor(recipe){this.p=validate(recipe);this.n=this.p.grid;const count=this.n*this.n;this.h=new Float32Array(count);this.v=new Float32Array(count);this.nh=new Float32Array(count);this.nv=new Float32Array(count);this.prev=new Float32Array(count);}
 clamp(v){return Math.max(-this.p.maxHeight,Math.min(this.p.maxHeight,v));}
 drop(x,z,radius,strength){const n=this.n;for(let j=1;j<n-1;j++)for(let i=1;i<n-1;i++){const d=Math.max(0,1-Math.hypot(i-x,j-z)/radius),k=j*n+i,h=(.5-.5*Math.cos(Math.PI*d))*strength;this.h[k]=this.clamp(this.h[k]+h);if(this.p.solver==='history')this.v[k]=this.clamp(this.v[k]+h);}}
 worldDrop(x,z,r=this.p.radius,s=this.p.strength){const c=(this.n-1)/this.p.width;this.drop((x+this.p.width/2)*c,(z+this.p.width/2)*c,r*c,s);}
 tick(){this.prev.set(this.h);for(let i=0;i<3;i++)this.step();}
 step(){const n=this.n,p=this.p;for(let z=1;z<n-1;z++)for(let x=1;x<n-1;x++){
  const k=z*n+x,average=(this.h[k-1]+this.h[k+1]+this.h[k-n]+this.h[k+n])*.25,edge=Math.min(x,z,n-1-x,n-1-z),d=p.boundary==='absorb'&&edge<8?.90+(p.damping-.90)*edge/8:p.damping,a=(average-this.h[k])*2*p.speed*p.speed;
  if(p.solver==='history'){this.nh[k]=this.clamp((2*this.h[k]-this.v[k]+a)*d);this.nv[k]=this.h[k];}
  else{const v=(this.v[k]+a)*d;this.nh[k]=this.clamp(this.h[k]+v);this.nv[k]=this.nh[k]-this.h[k];}
 }
 if(p.boundary==='reflect'){
  for(const arr of [this.nh,this.nv]){for(let i=1;i<n-1;i++){arr[i]=arr[n+i];arr[(n-1)*n+i]=arr[(n-2)*n+i];arr[i*n]=arr[i*n+1];arr[i*n+n-1]=arr[i*n+n-2];}arr[0]=arr[n+1];arr[n-1]=arr[2*n-2];arr[(n-1)*n]=arr[(n-2)*n+1];arr[n*n-1]=arr[(n-1)*n-2];}
 }
 [this.h,this.nh]=[this.nh,this.h];[this.v,this.nv]=[this.nv,this.v];}
 sample(x,z,t=1){const k=Math.max(0,Math.min(this.n-1,z))*this.n+Math.max(0,Math.min(this.n-1,x));return this.prev[k]+(this.h[k]-this.prev[k])*t;}
}
const api={defaults,limits,validate,WaveField};if(typeof module!=='undefined')module.exports=api;else root.WaterCore=api;
})(globalThis);
