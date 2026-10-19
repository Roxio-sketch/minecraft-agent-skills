// Analytic past-position sampling: trails reconstruct only the elapsed history, never a complete future orbit.
export const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
export const ease=x=>{x=clamp(x);return x*x*(3-2*x)};
export const mix=(a,b,t)=>a+(b-a)*t;
export const hash=i=>{let x=Math.sin(i*127.1+91.7)*43758.5453;return x-Math.floor(x)};
export function level(t){return 1.45*ease(t/1.35)*(1-ease((t-8.5)/1.4))}
export function phase(mode,t){return mode==='longyuan'?(t<1.4?'御剑起势':t<3.4?'升空 · 铺开剑幕':t<7.8?'九霄剑决 · 旋转落剑':t<9?'剑幕散去':'收势'):(t<1?'引剑':t<3.1?'展开 · 飞剑入阵':t<7.8?'万剑归宗 · 环形剑网':t<9?'剑光消散':'收势')}
export function count(mode,density){return mode==='longyuan'?[384,768,1152][density]:[40,72,104][density]}
// Player faces -Z. First fan from the back to four GROUND corners, then climb.
export function streamPosition(stream,u,t){
 u=clamp(u);const side=stream%2===0?-1:1,front=stream<2?-1:1;
 const source=[0,1.05+level(t),.36],corner=[side*1.75,.12,front*1.75];
 if(u<.32){
  const v=ease(u/.32),a=1-v,controlA=[side*1.25,source[1]-.1,1.10],controlB=[side*2.0,.18,front*1.6];
  return source.map((s,k)=>a*a*a*s+3*a*a*v*controlA[k]+3*a*v*v*controlB[k]+v*v*v*corner[k]);
 }
 const rise=ease((u-.32)/.68),coil=Math.sin(rise*Math.PI)*.48,phase=rise*Math.PI*4+t*1.5+stream*1.57;
 return [corner[0]+Math.cos(phase)*coil,.12+rise*11.4,corner[2]+Math.sin(phase)*coil];
}
export function fieldPosition(i,n,t,rotationClock=t){
 const h=hash(i),perLayer=n/3,tier=Math.min(2,Math.floor(i/perLayer)),j=i%perLayer;
 const bands=Math.ceil(perLayer/64),band=Math.floor(j/64),radial=band/Math.max(1,bands-1);
 const rotation=[.65,-.21,.34][tier],a=j/64*Math.PI*2+band*.34+rotationClock*rotation+tier*.75;
 const flight=clamp((t-.35-h*.95)/2.7),stream=i%4;
 if(flight<.67)return streamPosition(stream,flight/.67,t);
 const spread=ease((flight-.67)/.33),head=streamPosition(stream,1,t);
 const radius=mix([8,6,3][tier],[23,18,13][tier],radial);
 const x=Math.cos(a)*radius,z=Math.sin(a)*radius;
 const height=[8.8,12.1,15.5][tier]+(1-radial)*.6+Math.sin(a*3+band+tier)*.38+z*[.035,-.055,.045][tier];
 const final=[x,height,z];return head.map((v,k)=>mix(v,final[k],spread));
}
export function ringPosition(i,n,t,rotationClock=t){
 const h=hash(i+77),r0=9+3*hash(i+1),r1=4.2+1.2*hash(i+3),inward=ease((t-.5-h*.4)/2.5);
 const radius=mix(r0,r1,inward),a=i/n*Math.PI*2+rotationClock*(1.75+h*.7),tilt=(i%5-2)*.18*(.3+.7*inward);
 const cy=2.5+((i%4)-1.5)*.46;
 let x=Math.cos(a)*radius,z=Math.sin(a)*radius,y=Math.sin(a)*radius*Math.sin(tilt);
 const roll=(i%7)*.44,xx=x*Math.cos(roll)-z*Math.sin(roll),zz=x*Math.sin(roll)+z*Math.cos(roll);
 return [xx,cy+y,zz];
}
export function divingPosition(i,t){
 const cycle=(t-3.2-i*.17)%2.0;if(t<3.2+i*.17||cycle<0)return null;
 const a=i*2.399963+.15*Math.floor((t-3.2-i*.17)/2),r=5+hash(i+11)*12,u=clamp(cycle/1.3),endR=r*.60;
 return [Math.cos(a)*mix(r,endR,u),mix(11,.1,ease(u)),Math.sin(a)*mix(r,endR,u)];
}
export function direction(fn,t){const a=fn(t-.015),b=fn(t+.015);if(!a||!b)return [0,-1,0];let v=b.map((x,i)=>x-a[i]),l=Math.hypot(...v);return l>.000001?v.map(x=>x/l):[0,1,0]}
export function fade(mode,t){return ease(t/.6)*(1-ease((t-8.0)/1.6))}
