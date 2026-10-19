/* Scalar port of Ashima Arts' 2D simplex noise (MIT), as used by Spektr.
 * Copyright (C) 2011 Ashima Arts. See THIRD_PARTY_NOTICES.md. */
const LightningSimplex=(()=>{
 const mod289=x=>x-Math.floor(x/289)*289,permute=x=>mod289((x*34+1)*x);
 function noise(x,y){
  const c0=.211324865405187,c1=.366025403784439,c2=-.577350269189626,c3=.024390243902439;
  const skew=(x+y)*c1,ix=Math.floor(x+skew),iy=Math.floor(y+skew),unskew=(ix+iy)*c0;
  const x0=x-ix+unskew,y0=y-iy+unskew,i1=x0>=y0?1:0,j1=1-i1;
  const xs=[x0,x0+c0-i1,x0+c2],ys=[y0,y0+c0-j1,y0+c2],ox=[0,i1,1],oy=[0,j1,1];
  let sum=0;for(let k=0;k<3;k++){
   const p=permute(permute(mod289(iy)+oy[k])+mod289(ix)+ox[k]);
   const gx=2*(p*c3-Math.floor(p*c3))-1,h=Math.abs(gx)-.5,a=gx-Math.floor(gx+.5);
   const m=Math.max(.5-xs[k]*xs[k]-ys[k]*ys[k],0);
   sum+=m*m*m*m*(1.79284291400159-.85373472095314*(a*a+h*h))*(a*xs[k]+h*ys[k]);
  }return 130*sum;
 }
 return {noise};
})();
if(typeof module!=='undefined')module.exports=LightningSimplex;
