// Midpoint displacement is rebuilt only when shape parameters change; never per pixel.
export function geometry(p){
 let seed=(p.u_seed+1)>>>0;
 const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
 const x=new Float32Array(33);
 function split(a,b,amp){if(b-a<2)return;const m=(a+b)>>1;x[m]=(x[a]+x[b])/2+(random()-.5)*amp;split(a,m,amp*.53);split(m,b,amp*.53);}
 split(0,32,p.u_bend*1.65);
 const spine=new Float32Array(33*4);
 for(let i=0;i<33;i++)spine.set([x[i],(i/32-.5)*p.u_length,0,0],i*4);
 const branchA=new Float32Array(8*4),branchB=new Float32Array(8*4);
 for(let i=0;i<8;i++){
  const y=((i+.4+random()*.2)/8*1.64-.82)*p.u_length*.5;
  const s=(y/p.u_length+.5)*32,j=Math.min(31,Math.floor(s)),cx=x[j]+(x[j+1]-x[j])*(s-j);
  const side=i%2?1:-1, reach=p.u_branchLength*(.65+random()*.65),dy=(random()-.5)*reach*1.9;
  // Start inside the main opening; unioned tapered wedges ensure connected branches.
  const tipX=cx+side*(p.u_width*(p.u_mode?2:1)+reach);
  branchA.set([cx,y,cx+(tipX-cx)*.55,y+dy*.45],i*4);
  branchB.set([tipX,y+dy,.22+random()*.3,random()],i*4);
 }
 return {spine,branchA,branchB};
}
