/* Original tileable 3D cloud data, generated locally without reference assets. */
export function makeVortexNoise(size=64){
  function hash(x,y,z,s){let h=Math.imul(x,1597334677)^Math.imul(y,741103597)^Math.imul(z,1911520717)^s;h=Math.imul(h^(h>>>16),1580030173);h=Math.imul(h^(h>>>13),59260789);return ((h^(h>>>16))>>>0)/4294967296;}
  const grids=new Map();
  for(const count of [4,8,16]){
    const data=new Float32Array(count**3*3);
    for(let z=0;z<count;z++)for(let y=0;y<count;y++)for(let x=0;x<count;x++){
      const i=((z*count+y)*count+x)*3;
      for(let k=0;k<3;k++)data[i+k]=.12+.76*hash(x,y,z,1731+k*104729);
    }
    grids.set(count,data);
  }
  function cellular(x,y,z,count){
    const px=x*count,py=y*count,pz=z*count,ix=Math.floor(px),iy=Math.floor(py),iz=Math.floor(pz),fx=px-ix,fy=py-iy,fz=pz-iz;
    const grid=grids.get(count);let nearest=4;
    for(let dz=-1;dz<=1;dz++)for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
      const index=((((iz+dz+count)%count)*count+(iy+dy+count)%count)*count+(ix+dx+count)%count)*3;
      const ax=dx+grid[index]-fx,ay=dy+grid[index+1]-fy,az=dz+grid[index+2]-fz;
      nearest=Math.min(nearest,ax*ax+ay*ay+az*az);
    }
    return Math.max(0,1-Math.sqrt(nearest)*.85);
  }
  function value(x,y,z){
    const ix=Math.floor(x),iy=Math.floor(y),iz=Math.floor(z);let fx=x-ix,fy=y-iy,fz=z-iz;
    fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);let sum=0;
    for(let dz=0;dz<=1;dz++)for(let dy=0;dy<=1;dy++)for(let dx=0;dx<=1;dx++)sum+=hash((ix+dx)%4,(iy+dy)%4,(iz+dz)%4,3389)*(dx?fx:1-fx)*(dy?fy:1-fy)*(dz?fz:1-fz);
    return sum;
  }
  const data=new Uint8Array(size**3*4);
  for(let z=0;z<size;z++)for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    const u=(x+.5)/size,v=(y+.5)/size,w=(z+.5)/size,i=((z*size+y)*size+x)*4;
    const broad=cellular(u,v,w,4),medium=cellular(u,v,w,8),fine=cellular(u,v,w,16),low=value(u*4,v*4,w*4);
    data[i]=Math.round(255*(.72*broad+.28*low));data[i+1]=Math.round(255*medium);data[i+2]=Math.round(255*fine);data[i+3]=Math.round(255*low);
  }
  return {size,data};
}
export function uploadVortexNoise(gl,noise){
  const texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_3D,texture);
  gl.texImage3D(gl.TEXTURE_3D,0,gl.RGBA8,noise.size,noise.size,noise.size,0,gl.RGBA,gl.UNSIGNED_BYTE,noise.data);
  for(const axis of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T,gl.TEXTURE_WRAP_R])gl.texParameteri(gl.TEXTURE_3D,axis,gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_3D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  return texture;
}
