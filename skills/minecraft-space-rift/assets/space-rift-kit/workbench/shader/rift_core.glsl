// ProceduralSpaceRiftKit derivative. Core is shared by WebGL2, Shadertoy and Forge.
// Negative distance means open space. This is a distance bound, not an exact Euclidean SDF.
float riftFootprint;
float riftHorizonMain(vec2 p,float t,out float cx,out float hw);
vec3 riftHorizonInterior(vec2 p,float t);
vec3 riftHorizonEdgeDebug(vec2 p,float t);
vec4 riftRim(vec2 p,float t,float distance,float hw,out vec4 fringe);
float riftStyleCenter(float y,float t);
float riftStyleWidth(float y,float side,float t);
float riftStyledDistance(vec2 p,float cx,float d);
vec4 riftStyleSurface(vec2 p,float t,float d,float cx,float hw,float edge,float opening);
vec2 riftStyleWarp(vec2 p,float t,float d,float cx);
vec3 riftStyleInterior(vec2 p,float t,vec3 base);
vec3 riftCosmicInterior(vec2 p,float t);
float riftEventGrowth(){if(u_horizon>.5&&u_sequenceEnabled>.5)return smoothstep(.01,.28,u_sequence)*(1.-step(.9999,u_sequence));return u_sequenceEnabled>.5?smoothstep(.01,.26,u_sequence):u_progress;}
float riftEventExpansion(){if(u_horizon>.5&&u_sequenceEnabled>.5)return mix(.025,1.,smoothstep(.23,.6,u_sequence))*(1.-smoothstep(.82,1.,u_sequence));return u_sequenceEnabled>.5?mix(.09,2.1,smoothstep(.60,.96,u_sequence)):1.;}
float riftEventScale(){if(u_horizon>.5)return 1.;return u_sequenceEnabled>.5?mix(1.,1.45,smoothstep(.62,1.,u_sequence)):1.;}

float riftHash11(float p){p=fract(p*.1031);p*=p+33.33;p*=p+p;return fract(p);}
float riftHash21(vec2 p){vec3 p3=fract(vec3(p.xyx)*.1031);p3+=dot(p3,p3.yzx+33.33);return fract((p3.x+p3.y)*p3.z);}
vec2 riftHash22(vec2 p){return vec2(riftHash21(p+17.13),riftHash21(p+93.47));}
float riftValueNoise(vec2 p){vec2 i=floor(p),f=fract(p),w=f*f*f*(f*(f*6.-15.)+10.);return mix(mix(riftHash21(i),riftHash21(i+vec2(1,0)),w.x),mix(riftHash21(i+vec2(0,1)),riftHash21(i+1.),w.x),w.y);}
float riftFbm(vec2 p,float scale){
 float sum=0.,amp=.5,freq=1.;mat2 spin=mat2(.8,-.6,.6,.8);
 for(int i=0;i<8;i++){
  if(float(i)>=min(u_detail,3.+u_quality*2.5))break;
  float bandWeight=1.-smoothstep(.22,.65,riftFootprint*freq*scale);
  sum+=amp*mix(.5,riftValueNoise(p),bandWeight);p=spin*p*2.03+vec2(17.8,4.2);freq*=2.03;amp*=.5;
 }return sum;
}
float riftStars(vec2 p,float cells){
 vec2 q=p*cells,cell=floor(q),f=fract(q);float sum=0.;
 float aa=max(riftFootprint*cells,.002),radius=.019;
 for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
  vec2 id=cell+vec2(x,y),j=riftHash22(id+u_seed*2.37);
  float d=length(vec2(x,y)+.15+.7*j-f),r=max(radius,aa*.65);
  sum+=step(.975,riftHash21(id+82.3))*exp(-d*d/(r*r))*min(1.,radius*radius/(r*r));
 }return sum;
}
vec3 riftBackground(vec2 q,float t){
 // Analytical TEST scene. Never presented as Minecraft scene capture.
 vec2 g=abs(fract(q*3.)-.5)/max(fwidth(q*3.),vec2(.0001));
 float grid=1.-smoothstep(.4,1.4,min(g.x,g.y));
 float horizon=exp(-abs(q.y+.35)*3.);
 return vec3(.026,.037,.065)+vec3(.05,.07,.10)*horizon+grid*vec3(.12,.16,.22)*u_grid;
}
float riftSpine(float y){
 float pos=clamp((y/max(u_length,.001)+.5)*32.,0.,31.9999);int j=int(floor(pos));
 float f=fract(pos),a=u_spine[max(j-1,0)].x,b=u_spine[j].x,c=u_spine[j+1].x,e=u_spine[min(j+2,32)].x;
 return .5*((2.*b)+(-a+c)*f+(2.*a-5.*b+4.*c-e)*f*f+(-a+3.*b-3.*c+e)*f*f*f);
}
float riftCenter(float y,float t){
 float envelope=max(0.,1.-pow(y/(u_length*.5),2.));
 return riftSpine(y)*.36+(sin(y*7.3+t*1.4+u_seed)*u_dynamic*.018+riftStyleCenter(y,t))*envelope;
}
float riftHalfWidth(float y,float side,float halfL,float t){
 float s=clamp(y/halfL,-1.,1.);
 // Long needle tips, a continuous body, and sparse swept tear lobes.
 float taper=pow(max(0.,1.-s*s),1.65);
 // Sample in local physical units; width/length changes never stretch this field.
 vec2 q=vec2(y*u_frequency,u_seed*.37+side*37.1);
 float jag=riftFbm(q*1.6,u_frequency*1.6)-.5;
 float coarse=riftValueNoise(vec2(y*6.1+u_seed,side*17.7));
 float lobes=0.;
 for(int i=0;i<7;i++){
  float k=float(i)+side*19.+u_seed*.73;
  float anchor=(riftHash11(k*3.7)-.5)*1.65;
  float span=mix(.055,.19,riftHash11(k+81.));
  float v=(s-anchor)/span;
  // Unequal slopes produce torn triangular tongues rather than round thorns.
  float tooth=max(0.,1.-abs(v)/(v<0.?.48:1.35));
  lobes+=pow(tooth,1.35)*mix(.16,.85,riftHash11(k+32.));
 }
 float notch=max(.45,1.+u_roughness*(lobes+(coarse-.5)*.23+jag*.10));
 float localOpen=1.-u_localClose*exp(-pow((y/u_length-.13)*9.,2.));
 float pulse=1.+u_dynamic*.22*sin(y*12.+t*2.1);
 return u_width*riftEventExpansion()*u_open*(1.-u_shrink)*taper*notch*localOpen*pulse*riftStyleWidth(y,side,t)*(1.+u_mode*.9);
}
float riftMain(vec2 p,float t,out float cx,out float hw){
 if(u_horizon>.5)return riftHorizonMain(p,t,cx,hw);
 float halfL=max(.00001,u_length*.5*riftEventGrowth()*(1.-u_shrink));
 float y=clamp(p.y,-halfL,halfL);
 cx=riftCenter(y,t);hw=riftHalfWidth(y,p.x<cx?-1.:1.,halfL,t);
 vec2 ds=vec2(abs(p.x-cx)-hw,abs(p.y)-halfL);
 return length(max(ds,vec2(0.)))+min(max(ds.x,ds.y),0.);
}
float riftTaperSegment(vec2 p,vec2 a,vec2 b,float wa,float wb){
 vec2 ab=b-a;float h=clamp(dot(p-a,ab)/max(dot(ab,ab),1e-9),0.,1.);
 return length(p-a-ab*h)-mix(wa,wb,h);
}
float riftBranches(vec2 p,float t){
 if(u_horizon>.5)return 100.;
 float d=100.;float count=min(u_branches,2.+u_quality*3.);
 float halfL=u_length*.5*riftEventGrowth()*(1.-u_shrink);
 for(int i=0;i<8;i++){
  if(float(i)>=count)break;
  vec2 a=u_branchA[i].xy,b=u_branchA[i].zw,c=u_branchB[i].xy;
  float branchExpansion=sqrt(riftEventExpansion());b.x=a.x+(b.x-a.x)*branchExpansion;c.x=a.x+(c.x-a.x)*branchExpansion;
  float growth=smoothstep(abs(a.y),abs(a.y)+u_length*.12,halfL);
  if(u_sequenceEnabled>.5)growth*=smoothstep(.54+float(i)*.035,.70+float(i)*.035,u_sequence);
  b=mix(a,b,growth);c=mix(a,c,growth);
  float drift=riftCenter(a.y,t)-riftSpine(a.y);a.x+=drift;b.x+=drift;c.x+=drift;
  // A broad attached root tapers into a swept tongue, never a round antenna.
  float side=sign(c.x-a.x);
  float rootWidth=riftHalfWidth(a.y,side,max(halfL,.00001),t);
  b.x=a.x+side*(rootWidth*.85+abs(b.x-a.x)*.30);
  c.x=a.x+side*(rootWidth+abs(c.x-a.x)*.38);
  b.y=a.y+(b.y-a.y)*1.4;c.y=a.y+(c.y-a.y)*1.8;
  float width=rootWidth*.62*growth;
  float v=min(riftTaperSegment(p,a,b,width,width*.48),riftTaperSegment(p,b,c,width*.48,0.));
  // Hide before root is revealed; zero growth must not leave point remnants.
  if(growth>.0001)d=min(d,v);
 }return d;
}
// Independent interior interface. Replace this function to route another procedural scene.
vec3 riftInterior(vec2 p,float t){
 if(u_interiorMode>1.5)return vec3(0.);
 if(u_horizon>.5)return riftHorizonInterior(p,t);
 if(u_interiorMode>.5)return riftCosmicInterior(p,t);
 t*=u_insideSpeed;vec3 col=vec3(.001,.002,.007);
 for(int i=0;i<6;i++){
  if(float(i)>=min(u_layers,2.+u_quality*2.))break;
  float z=float(i)+1.;vec2 q=p*(1.+z*.21)+vec2(t*.008,-t*.006)*z+u_seed*.11;
  float fog=riftFbm(q*3.4+vec2(0.,t*.04),(1.+z*.21)*3.4);
  float veil=pow(max(0.,fog),3.);
  col+=u_insideColor*veil*.75/z;
  col+=riftStars(q,25.+z*9.)*mix(vec3(.45,.61,.9),u_edgeColor,.3)/z;
 }
 return col;
}
float riftShards(vec2 p,float t){
 float d=100.;
 for(int i=0;i<12;i++){
  if(float(i)>=4.+u_quality*4.)break;
  float k=float(i),r=riftHash11(k+u_seed*1.7),y=(riftHash11(k+u_seed*3.2)-.5)*u_length*.86;
  float side=mod(k,2.)<.5?-1.:1.;float halfL=max(.00001,u_length*.5*riftEventGrowth()*(1.-u_shrink));
  if(abs(y)>halfL*.88 || abs(p.y-y)>.09)continue;
  float w=riftHalfWidth(y,side,halfL,t),cx=riftCenter(y,t);
  float phase=fract(t*.10+r),radius=.004+.013*r;
  vec2 pos=vec2(cx+side*(w+.02+phase*.09),y+phase*.035);
  vec2 q=p-pos;float a=r*6.28+t*.1;q=mat2(cos(a),-sin(a),sin(a),cos(a))*q;
  float triangle=max(-q.y-radius*.8,max(q.y*.6+q.x,q.y*.6-q.x)-radius*.6);
  d=min(d,triangle/max(.5,1.-phase*.7));
 }return d;
}
// Bounded irregular radial tessellation: 24 triangular glass facets, never a full-screen Voronoi web.
vec2 riftGlassRing(float sector,float ring){
 float angle=sector*6.28318530718/12.;float rand=riftHash11(mod(sector,12.)*17.13+u_seed);
 vec2 axis=vec2(cos(angle),sin(angle));
 vec2 radius=ring<.5?vec2(.12,.43):vec2(.70,.91);
 return axis*radius*(.8+rand*.35)*u_length;
}
float riftTriangle(vec2 p,vec2 a,vec2 b,vec2 c){
 vec2 e0=b-a,e1=c-b,e2=a-c;float orient=sign(e0.x*(c.y-a.y)-e0.y*(c.x-a.x));
 return max(max(-orient*(e0.x*(p.y-a.y)-e0.y*(p.x-a.x))/max(length(e0),1e-6),-orient*(e1.x*(p.y-b.y)-e1.y*(p.x-b.x))/max(length(e1),1e-6)),-orient*(e2.x*(p.y-c.y)-e2.y*(p.x-c.x))/max(length(e2),1e-6));
}
// Returns face opacity, rim coverage, and inverse-mapped background coordinates.
vec4 riftGlass(vec2 p,out vec2 sampleP){
 sampleP=p;if(u_horizon>.5||u_sequenceEnabled<.5||u_sequence<.275||u_sequence>.96)return vec4(0.);
 float age=smoothstep(.30,.78,u_sequence),fade=1.-smoothstep(.70,.96,u_sequence);
 float reveal=smoothstep(.275,.34,u_sequence);vec4 result=vec4(0.);
 for(int i=0;i<24;i++){
  float sector=float(i/2),rnd=riftHash11(float(i)*7.19+u_seed*2.37);
  vec2 a=riftGlassRing(sector,0.),b=riftGlassRing(sector+1.,0.),c=riftGlassRing(sector,1.),d=riftGlassRing(sector+1.,1.);
  if(mod(float(i),2.)>.5){a=b;b=d;}
  vec2 center=(a+b+c)/3.;
  float spin=(rnd-.5)*age*5.,cs=cos(spin),sn=sin(spin),tilt=max(.12,abs(cos(age*(1.4+rnd*2.))));
  vec2 moved=center+normalize(center+vec2(.0001))*age*age*(.35+rnd)*u_length+vec2(0.,-age*age*.12*u_length);
  vec2 q=mat2(cs,-sn,sn,cs)*(p-moved);q.x/=tilt;q/=1.-age*.20;q+=center;
  if(length(q-center)>u_length*.55)continue;
  float distance=riftTriangle(q,a,b,c)+(.0015+age*.012)*u_length;
  float aa=max(riftFootprint/tilt,.0001),mask=1.-smoothstep(-aa,aa,distance);
  if(mask>result.a){float rim=exp(-abs(distance)/max(.0018,aa));sampleP=q;result=vec4(rim,(.04+age*.13)*(rnd+.25),rnd,mask*fade*reveal);}
 }
 return result;
}
struct RiftSample {float distance;float opening;float interiorVisibility;vec2 warp;vec4 surface;};
RiftSample riftHorizonSurface(vec2 p,float t,float d,float cx,float hw,float opening,float alive);
float riftAperture(vec2 p,float t){
 if(riftEventGrowth()<=0.||u_shrink>=1.||u_open<=0.||u_opacity<=0.)return 0.;
 float scale=riftEventScale();p/=scale;riftFootprint/=scale;float cx,hw;
 float d=min(riftMain(p,t,cx,hw),riftBranches(p,t));if(u_horizon<.5)d=riftStyledDistance(p,cx,d);float aa=max(fwidth(d),riftFootprint*.65);
 return 1.-smoothstep(-aa,aa,d);
}
RiftSample riftEvaluate(vec2 p,float t){
 RiftSample r;r.distance=100.;r.opening=0.;r.interiorVisibility=0.;r.warp=vec2(0.);r.surface=vec4(0.);
 // Whole lifecycle gates ALL layers, including branches, debris and refraction.
 if(riftEventGrowth()<=0.||u_shrink>=1.||u_open<=0.||u_opacity<=0.)return r;
 vec2 originalP=p;float eventScale=riftEventScale();p/=eventScale;riftFootprint/=eventScale;
 float cx,hw;float d=riftMain(p,t,cx,hw);d=min(d,riftBranches(p,t));if(u_horizon<.5)d=riftStyledDistance(p,cx,d);r.distance=d;
 float aa=max(fwidth(d),riftFootprint*.65),opening=1.-smoothstep(-aa,aa,d);r.opening=opening;
 float alive=smoothstep(0.,.04,riftEventGrowth())*(1.-smoothstep(.91,1.,u_shrink));
 if(u_horizon>.5)return riftHorizonSurface(p,t,d,cx,hw,opening,alive);
 float thick=max(u_edgeThickness,.00001),edge=exp(-abs(d)/max(thick,aa*.55))*min(1.,thick/max(aa*.55,1e-7));
 float halo=exp(-max(d,0.)/max(thick*7.,.008))*(1.-opening*.8);
 float aura=exp(-max(d,0.)/.11)*(1.-opening)*.025;
 float tipEnvelope=1.-smoothstep(.72,1.04,abs(p.y)/max(.0001,u_length*.5*riftEventGrowth()*(1.-u_shrink)));
 halo*=tipEnvelope;aura*=tipEnvelope;
 float shards=0.;if(u_debris>0.){float sd=riftShards(p,t);shards=(1.-smoothstep(-aa,aa,sd))*u_debris;}
 float alpha=clamp(opening+edge*.7+halo*.22+aura+shards,0.,1.)*u_opacity*alive;
 vec3 inside=vec3(0.);if(opening>.0001)inside=riftStyleInterior(p,t,riftInterior(p,t));
 vec4 rimFringe;vec4 rim=riftRim(p,t,d,hw,rimFringe);
 r.interiorVisibility=opening*(1.-rim.a);
 // Ionized corona is a distance-band with advected multi-scale detail, not a stretched sprite.
 float corona=0.,filament=0.;
 if(d>-.02 && d<.32 && u_corona>0.){
  float flow=riftFbm(vec2(p.y*u_frequency*.7-t*.55,d*25.+t*.23+u_seed),max(u_frequency*.7,25.));
  float ragged=1.-smoothstep(.01,.07+flow*.11,max(d,0.));
  corona=ragged*pow(max(flow,.0),1.5)*exp(-max(d,0.)*14.)*(1.-opening)*u_corona*tipEnvelope*.28;
  float threads=abs(sin(d*190.+flow*17.-t*3.));
  filament=(1.-smoothstep(.02,.16,threads))*exp(-max(d,0.)*20.)*(1.-opening)*u_filaments*tipEnvelope;
 }
 alpha=clamp(alpha+(corona*.7+filament*.2)*u_opacity*alive,0.,1.);
 vec3 emission=(u_edgeColor*(edge*.75+halo*.12+aura+shards*.8+corona*1.8+filament*.6)+vec3(.7,.86,1.)*(edge*.22+corona*.4))*u_glow;
 emission+=rimFringe.rgb*tipEnvelope*u_glow;
 alpha=clamp(alpha+rimFringe.a*tipEnvelope*u_opacity*alive,0.,1.);
 vec4 styled=riftStyleSurface(p,t,d,cx,hw,edge,opening);
 alpha=clamp(alpha+styled.a*u_opacity*alive,0.,1.);
 vec3 color=(inside*(1.-rim.a)+rim.rgb)*opening+emission+styled.rgb*u_glow;
 // Premultiplied output in linear-like display space. bounded emission; no global tone map.
 r.surface=vec4((1.-exp(-max(color,vec3(0.))*1.2))*u_opacity*alive,alpha);
 vec2 normal=normalize(vec2(p.x-cx+.000001,sin(p.y*9.+t)*.12));
 float influence=exp(-max(d,0.)/.12)*(1.-opening);
 r.warp=normal*.033*u_distortion*influence*u_opacity*alive;
 vec2 glassP;vec4 glass=riftGlass(p,glassP);
 if(glass.a>0.){
  r.warp=mix(r.warp,(glassP-p),glass.a);
  vec3 highlight=(u_edgeColor*glass.r*.65+vec3(.25,.50,.64)*glass.g)*glass.a*u_opacity;
  r.surface.rgb+=highlight*(1.-opening);r.surface.a=clamp(r.surface.a+glass.g*glass.a*.5*(1.-opening),0.,1.);
 }
 r.warp+=riftStyleWarp(p,t,d,cx)*u_distortion*u_opacity*alive;
 r.warp*=eventScale;return r;
}
