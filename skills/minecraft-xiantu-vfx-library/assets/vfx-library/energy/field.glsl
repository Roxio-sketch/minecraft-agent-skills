// Original Energy VFX volume kernel. MIT. Shared unchanged by browser and Forge.
uniform float u[48];
uniform vec4 puffs[32];
uniform float puffStrength[32];
uniform vec4 puffFlow[32];
uniform float puffSeed[32];
uniform int puffCount;
float smokeStep;
#ifndef ENERGY_DIRECT_PARCELS
int auraIndices[32];int auraCandidateCount;int auraSingle;
#endif
uniform vec3 auraMin,auraMax,noiseOrigin;
uniform float auraOpacity;
uniform float clockTime;
uniform vec3 colorA,colorB;
#define RADIUS u[0]
#define STRETCH u[1]
float hash3(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise3(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash3(i),hash3(i+vec3(1,0,0)),f.x),mix(hash3(i+vec3(0,1,0)),hash3(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash3(i+vec3(0,0,1)),hash3(i+vec3(1,0,1)),f.x),mix(hash3(i+vec3(0,1,1)),hash3(i+vec3(1,1,1)),f.x),f.y),f.z);}
vec3 lifecycle(){
 float t=clockTime;
 if(u[31]<.5)return vec3(1,1,0);
 if(u[31]>1.5){float d=clamp(t/u[34],0.,1.);return vec3(1.-d,1.+d*.5,d);}
 float total=u[32]+u[33]+u[34];t=mod(t,total);
 if(t<u[32]){float a=t/u[32];return vec3(.08+.92*a,.35+.65*a,0);}
 t-=u[32];if(t<u[33]){float a=t/u[33];return vec3(1.-a*.25,1.+a*u[30],a);}
 float a=(t-u[33])/u[34];return vec3(.75*(1.-a),1.+u[30]+a*.35,1.);
}
// Smooth compact vortex potentials, each with its own axis, phase and strength.
// Only the eight enclosing lattice vertices can contribute (support radius = cell size).
vec4 smokeVortex(vec3 corner,float t,float seed){
 vec3 h=vec3(hash3(corner+seed),hash3(corner+seed+17.1),hash3(corner+seed+31.7));
 vec3 axis=normalize(h*2.-1.+vec3(.001));
 float omega=(2.+h.y*2.)*sin(t*(.35+h.x*.2)+h.z*6.283+seed);
 return vec4(axis,omega);
}
vec3 smokeCurl(vec3 p,float t,float seed){
 float cellSize=1.1;vec3 cell=floor(p/cellSize),velocity=vec3(0);
 for(int i=0;i<8;i++){
  vec3 corner=cell+vec3(float(i&1),float((i>>1)&1),float((i>>2)&1));
  vec3 r=p-corner*cellSize;float falloff=max(1.-dot(r,r)/(cellSize*cellSize),0.);
  vec4 vortex=smokeVortex(corner,t,seed);
  velocity+=vortex.w*falloff*falloff*falloff*cross(vortex.xyz,r);
 }
 return velocity;
}
#ifdef ENERGY_FLOW_CACHE
uniform sampler2DArray smokeVortexCache;
uniform int smokeCacheEnabled;
vec3 parcelCurl(vec3 p,float t,float seed,int parcel,int stage){
 vec3 cell=floor(p/1.1);
 // Exact direct evaluation outside the finite lattice, including extreme recipes.
 if(smokeCacheEnabled==0||any(lessThan(cell,vec3(-6)))||any(greaterThan(cell+1.,vec3(6))))return smokeCurl(p,t,seed);
 vec3 velocity=vec3(0);
 for(int i=0;i<8;i++){
  vec3 corner=cell+vec3(float(i&1),float((i>>1)&1),float((i>>2)&1));
  vec3 r=p-corner*1.1;float falloff=max(1.-dot(r,r)/(1.1*1.1),0.);
  ivec3 node=ivec3(corner)+ivec3(6);
  vec4 vortex=texelFetch(smokeVortexCache,ivec3(node.x+node.z*13,node.y,parcel*6+stage),0);
  velocity+=vortex.w*falloff*falloff*falloff*cross(vortex.xyz,r);
 }
 return velocity;
}
#else
vec3 parcelCurl(vec3 p,float t,float seed,int parcel,int stage){return smokeCurl(p,t,seed);}
#endif
// Signed sheet distance, physical thickness, sheet density and diffuse density.
// Keeping the distance lets the ray integrator resolve a thin sheet between samples.
vec4 auraSample(vec3 point,int i,vec3 life){
  float radius=max(.01,puffs[i].w);
  vec3 delta=(point-puffs[i].xyz)/radius,flow=puffFlow[i].xyz;
  vec3 axis=length(flow)>.001?normalize(flow):vec3(0,1,0);float along=dot(delta,axis);
  vec3 crossSection=delta-axis*along;float r2=dot(crossSection,crossSection)+along*along/(STRETCH*STRETCH);
  float baseWidth=max(.025,u[3]*sqrt(8./u[16]));
  if(r2>6.25)return vec4(8.,baseWidth,0,0);float support;
  vec3 q=crossSection+axis*(along/STRETCH);float age=puffFlow[i].w,phase=puffSeed[i];
  // Backtrace each parcel's density coordinates through local flow with RK2.
  // The parcel travels in world space while its own sheets fold and shear internally.
  float dt=min(age,3.)*u[7]*.8/3.,t=clockTime*u[7];
  for(int j=0;j<3;j++){vec3 v=parcelCurl(q,t,phase,i,j*2)*(u[9]+abs(u[8])*.18);vec3 mid=q-v*dt*.5;q-=parcelCurl(mid,t-dt*.5,phase,i,j*2+1)*(u[9]+abs(u[8])*.18)*dt;t-=dt;}
  // The density support is advected too, so the outer silhouette can bend and tear.
  support=exp2(-dot(q,q)*2.16404)*puffStrength[i];
  vec3 seed=vec3(phase*3.1+u[35]*.013,phase*1.7+u[35]*.037,phase*.7+u[35]*.019);
  vec3 gradient;float n=psrdnoise(q*u[5]*.65+seed,vec3(0),0.,gradient);
  vec3 normal=normalize(vec3(sin(phase),cos(phase)*.7,sin(phase*.61+1.3)));
  vec3 tangent=normalize(cross(normal,abs(normal.y)<.9?vec3(0,1,0):vec3(1,0,0)));vec3 bitangent=cross(normal,tangent);
  float x=dot(q,tangent),y=dot(q,bitangent),z=dot(q,normal),freq=u[5]*1.3;
  float wave1=y*freq+phase,wave2=x*freq*1.27-phase;
  float sheetValue=z+sin(wave1)*.38+sin(wave2)*.17+n*u[9]*.12;
  vec3 sheetGradient=normal+bitangent*cos(wave1)*freq*.38+tangent*cos(wave2)*freq*1.27*.17+gradient*u[5]*.65*u[9]*.12;
  float isoDistance=sheetValue/max(.35,length(sheetGradient));
  float detail=noise3(q*u[6]+seed+12.4);float tear=smoothstep(u[13],u[13]+u[14],.3+detail*.5+n*u[17]*.15);
  float amplitude=support*tear*u[12]*auraOpacity*life.x;
  return vec4(isoDistance,baseWidth,amplitude*u[2]*mix(.85,.55+detail*.85,u[15]),amplitude*(1.-u[2])*(.012+detail*.018));
}
float smokeErf(float x){
 float a=abs(x),t=1./(1.+.3275911*a);
 float polynomial=(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-.284496736)*t+.254829592)*t;
 return sign(x)*(1.-polynomial*exp(-a*a));
}
// Exact Gaussian integral for a linearly varying signed distance. This resolves
// sub-step crossings without pixel jitter, widening the sheet or a screen blur.
float smokeSegment(vec4 a,vec4 b){
 float width=(a.y+b.y)*.5,delta=b.x-a.x,average;
 if(abs(delta)<width*.025){float d=(a.x+b.x)*.5/width;average=exp(-.5*d*d);}
 else average=clamp(width*1.253314137*(smokeErf(b.x/(width*1.414213562))-smokeErf(a.x/(width*1.414213562)))/delta,0.,1.);
 return average*(a.z+b.z)*.5+(a.w+b.w)*.5;
}
#ifndef ENERGY_DIRECT_PARCELS
vec3 auraField(vec3 point,vec3 life){
 float density=0.;
 for(int k=0;k<32;k++){
  if(k>=auraCandidateCount)break;int i=auraIndices[k];if(auraSingle>=0&&i!=auraSingle)continue;
  vec4 s=auraSample(point,i,life);float d=s.x/s.y;density+=exp(-.5*d*d)*s.z+s.w;
 }
 return vec3(density,density/max(.01,u[12])*1.4,0);
}
#endif
// Returns extinction density, filament density, white-hot contribution.
vec3 energyField(vec3 point,vec3 life){
#ifndef ENERGY_DIRECT_PARCELS
 if(u[39]>.5)return auraField(point,life);
#endif
 vec3 p=point/(RADIUS*life.y);p.y/=STRETCH;
 float r=length(p);if(r>2.8)return vec3(0);
 float t=clockTime*u[7],ang=t*u[8]/(.4+r)+p.y*u[8]*.55;
 mat2 rot=mat2(cos(ang),-sin(ang),sin(ang),cos(ang));p.xz=rot*p.xz;
 p*=max(.05,(r-u[10]*t*.25)/(r+.001));p.y-=t*u[11];
 vec3 seed=vec3(u[35]*.013,u[35]*.037,u[35]*.019);
 vec3 grad;float coarse=psrdnoise(p*u[5]+seed,vec3(0),t,grad);
 vec3 q=p+grad*(u[9]*.12);
 float detail=noise3(q*u[6]+seed+vec3(t*.12,-t*.18,t*.09));
 float fold=abs(psrdnoise(q*u[5]*1.63+seed+12.4,vec3(0),-t*.7,grad));
 float rip=coarse*u[17];float distanceToShell=abs(r-(.76+rip));
 float shape=exp(-distanceToShell*distanceToShell/max(.0005,u[3]*u[3]));
 float cloud=mix(exp(-r*r*2.8),shape,u[2]);
 vec3 geometry=point/(RADIUS*life.y);geometry.y/=STRETCH;
 if(u[38]>.5&&u[38]<1.5){float d=length(vec2(length(geometry.xz)-.8,geometry.y));cloud=exp(-pow((d-.12-rip*.25)/max(.04,u[3]),2.));}
 if(u[38]>1.5&&u[38]<2.5){float tube=length(geometry.xz)-(.17+.42*clamp(geometry.y+.9,0.,1.8));cloud=exp(-pow((tube-rip*.4)/max(.04,u[3]),2.))*(1.-smoothstep(.9,1.5,abs(geometry.y)));}
 if(u[38]>2.5){float tube=length(geometry.xz);cloud=exp(-pow((tube-.23-rip*.25)/max(.04,u[3]),2.))*(1.-smoothstep(1.,1.4,abs(geometry.y)));}
 float torn=smoothstep(u[13]+life.z*.2,u[13]+u[14]+life.z*.2,detail*.75+coarse*.25+.16);
 // Intersections of two warped iso-bands form narrow 3-D strands, rather than a fog texture.
 float fine=exp(-fold*u[16])*exp(-abs(detail-.5)*u[16]*1.2);
 float fil=mix(.32+detail*.68,fine*9.,u[15]);
 float core=exp(-r*r*24.)*u[4];
 float d=(cloud*torn*fil+core)*u[12]*life.x;
 vec3 v=point/(RADIUS*life.y);float ct=cos(u[29]),st=sin(u[29]);v.yz=mat2(ct,-st,st,ct)*v.yz;
 float ringDist=length(vec2(length(v.xz)-u[27],v.y));
 float ring=exp(-ringDist*ringDist/(u[28]*u[28]))*u[26]*life.x;
 d+=ring;
 return vec3(d,fine*cloud*torn*5.+ring,core*u[19]*3.);
}
