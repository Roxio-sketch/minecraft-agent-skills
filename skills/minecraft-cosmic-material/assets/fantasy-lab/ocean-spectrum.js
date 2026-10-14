// Original CPU spectral ocean. Two independent periodic domains, complex
// Gaussian h0, conjugate time evolution and radix-2 inverse FFT. No copied
// source or reference textures. Shared once per state by preview canvases.
(function(root){
  const N=128,COUNT=N*N,TAU=2*Math.PI;
  function hash(x){x^=x>>>16;x=Math.imul(x,0x7feb352d);x^=x>>>15;x=Math.imul(x,0x846ca68b);return(x^(x>>>16))>>>0;}
  function random(x){return((hash(x)>>>8)+.5)/16777216;}
  function gaussian(id,seed){const u=random(Math.imul(id+1,1597334677)^seed),v=random(Math.imul(id+1,3812015801)^seed^0x6a09e667);const r=Math.sqrt(-2*Math.log(u));return[r*Math.cos(TAU*v),r*Math.sin(TAU*v)];}
  const reverse=new Int32Array(N),cos=new Float64Array(N/2),sin=new Float64Array(N/2);
  for(let i=0;i<N;i++){let v=i,r=0;for(let b=0;b<7;b++){r=r*2+(v&1);v>>>=1;}reverse[i]=r;}
  for(let i=0;i<N/2;i++){cos[i]=Math.cos(TAU*i/N);sin[i]=Math.sin(TAU*i/N);}
  function transform1(r,im,offset,stride){
    for(let i=0;i<N;i++){const j=reverse[i];if(j>i){let a=offset+i*stride,b=offset+j*stride,t=r[a];r[a]=r[b];r[b]=t;t=im[a];im[a]=im[b];im[b]=t;}}
    for(let size=2;size<=N;size*=2){const half=size/2,jump=N/size;
      for(let start=0;start<N;start+=size)for(let j=0;j<half;j++){
        const a=offset+(start+j)*stride,b=a+half*stride,k=j*jump;
        const tr=r[b]*cos[k]-im[b]*sin[k],ti=r[b]*sin[k]+im[b]*cos[k];
        r[b]=r[a]-tr;im[b]=im[a]-ti;r[a]+=tr;im[a]+=ti;
      }
    }
  }
  function inverse2(r,im){for(let y=0;y<N;y++)transform1(r,im,y*N,1);for(let x=0;x<N;x++)transform1(r,im,x,N);for(let i=0;i<COUNT;i++){r[i]/=COUNT;im[i]/=COUNT;}}
  class Cascade {
    constructor(p,layer){
      this.length=p.waveLength*(layer?3.7:32);
      const peak=p.waveLength*(layer?.45:4),cut=p.waveLength*(layer?.025:.10),wind=p.windAngle%360*Math.PI/180;
      this.h0r=new Float64Array(COUNT);this.h0i=new Float64Array(COUNT);
      this.omega=new Float64Array(COUNT);this.kx=new Float64Array(COUNT);this.ky=new Float64Array(COUNT);
      this.fields=Array.from({length:3},()=>({r:new Float64Array(COUNT),im:new Float64Array(COUNT)}));
      this.map=new Float32Array(COUNT*4);let sum=0;
      for(let y=0;y<N;y++)for(let x=0;x<N;x++){
        const i=y*N+x,kx=(x<=N/2?x:x-N)*TAU/this.length,ky=(y<=N/2?y:y-N)*TAU/this.length,k2=kx*kx+ky*ky,k=Math.sqrt(k2);
        if(k===0||x===N/2||y===N/2)continue;
        const alignment=(kx*Math.cos(wind)+ky*Math.sin(wind))/k;
        const direction=(.035+.965*Math.pow(Math.abs(alignment),(layer?2:4)*(1.3-.3*(p.warp??.9))))*(alignment<0?.09:1);
        const power=Math.exp(-1/(k2*Math.pow(peak/TAU,2)))/Math.pow(k2,2)*direction*Math.exp(-k2*cut*cut);
        const g=gaussian(i,p.seed^(layer?0x243f6a88:0x13198a2e));
        this.h0r[i]=g[0]*Math.sqrt(power);this.h0i[i]=g[1]*Math.sqrt(power);sum+=power;
        this.omega[i]=Math.sqrt(9.81*k*Math.tanh(k*p.depth));this.kx[i]=kx/k;this.ky[i]=ky/k;
      }
      const scale=(layer?.10:.90)*COUNT/Math.sqrt(Math.max(4*sum,1e-20));
      for(let i=0;i<COUNT;i++){this.h0r[i]*=scale;this.h0i[i]*=scale;}
    }
    update(t){
      const [h,dx,dy]=this.fields;
      for(let y=0;y<N;y++)for(let x=0;x<N;x++){
        const i=y*N+x,j=((N-y)%N)*N+(N-x)%N,c=Math.cos(this.omega[i]*t),s=Math.sin(this.omega[i]*t);
        const a=this.h0r[i],b=this.h0i[i],ar=this.h0r[j],bi=this.h0i[j];
        const r=(a+ar)*c-(b+bi)*s,im=(a-ar)*s+(b-bi)*c;
        h.r[i]=r;h.im[i]=im;
        dx.r[i]=-im*this.kx[i];dx.im[i]=r*this.kx[i];dy.r[i]=-im*this.ky[i];dy.im[i]=r*this.ky[i];
      }
      for(const field of this.fields)inverse2(field.r,field.im);
      let imaginary=0;
      for(let i=0;i<COUNT;i++){this.map[i*4]=h.r[i];this.map[i*4+1]=dx.r[i];this.map[i*4+2]=dy.r[i];this.map[i*4+3]=0;imaginary=Math.max(imaginary,Math.abs(h.im[i]),Math.abs(dx.im[i]),Math.abs(dy.im[i]));}
      this.imaginaryResidual=imaginary;
    }
  }
  class OceanSpectrum {
    constructor(){this.cache=new Map();this.serial=0;}
    get(p,seconds){
      const key=JSON.stringify([p.seed,p.waveLength,p.windAngle%360,p.depth,p.warp??.9]);let state=this.cache.get(key);
      if(!state){state={cascades:[new Cascade(p,0),new Cascade(p,1)],time:NaN,version:0};this.cache.set(key,state);if(this.cache.size>4)this.cache.delete(this.cache.keys().next().value);}
      const t=p.speed===0?0:Math.fround(Math.fround(seconds)*Math.fround(p.speed));
      if(state.time!==t){state.cascades.forEach(c=>c.update(t));state.time=t;state.version=++this.serial;}
      return state;
    }
  }
  const api={OceanSpectrum,inverse2,N};
  if(typeof module==='object'&&module.exports)module.exports=api;else root.FantasyOcean=api;
})(typeof globalThis!=='undefined'?globalThis:this);
