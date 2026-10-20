// Paste into Image. No Common/Buffer/iChannel inputs needed.
// Edit constants below; same core as WebGL/Forge. No artwork copied from Shadertoy.
#define u_time iTime
const float u_length=1.8;
const float u_width=0.16;
const float u_angle=0.12;
const float u_bend=0.6;
const float u_roughness=1.6;
const float u_frequency=18.0;
const float u_detail=6.0;
const float u_branches=5.0;
const float u_branchLength=0.35;
const float u_seed=91.0;
const float u_glow=1.5;
const float u_edgeThickness=0.003;
const float u_rimWidth=0.045;
const float u_rimDepth=0.07;
const float u_rimRelief=0.8;
const float u_horizon=0.0;
const float u_tipPower=1.65;
const float u_domainWarp=0.25;
const float u_edgeReach=0.032;
const float u_interiorDepth=1.4;
const float u_starDensity=1.0;
const float u_galaxyX=0.0;
const float u_galaxyY=0.0;
const float u_galaxyScale=0.65;
const float u_interiorBrightness=1.0;
const float u_viewTiltX=0.28;
const float u_viewTiltY=-0.16;
const float u_distortion=0.7;
const float u_dispersion=0.04;
const float u_insideSpeed=0.4;
const float u_opacity=1.0;
const float u_debris=0.4;
const float u_layers=3.0;
const float u_progress=1.0;
const float u_open=1.0;
const float u_shrink=0.0;
const float u_localClose=0.0;
const float u_dynamic=0.15;
const float u_speed=1.0;
const float u_zoom=1.0;
const float u_grid=1.0;
const float u_mode=1.0;
const float u_quality=2.0;
const float u_debug=0.0;
const float u_sequence=0.82;
const float u_sequenceEnabled=1.0;
const float u_corona=1.2;
const float u_filaments=0.7;
const float u_galaxies=1.2;
const float u_interiorMode=1.0;
const float u_style=0.0;
const float u_styleAmount=1.0;
const float u_styleScale=1.0;
const float u_edgeMotion=1.0;
const vec3 u_edgeColor=vec3(.31,.86,.91),u_insideColor=vec3(.03,.15,.19);
vec4 u_spine[33],u_branchA[8],u_branchB[8];
//
// psrdnoise2.glsl
//
// Authors: Stefan Gustavson (stefan.gustavson@gmail.com)
// and Ian McEwan (ijm567@gmail.com)
// Version 2021-12-02, published under the MIT license (see below)
//
// Copyright (c) 2021 Stefan Gustavson and Ian McEwan.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//

//
// Periodic (tiling) 2-D simplex noise (hexagonal lattice gradient noise)
// with rotating gradients and analytic derivatives.
//
// This is (yet) another variation on simplex noise. Unlike previous
// implementations, the grid is axis-aligned and slightly stretched in
// the y direction to permit rectangular tiling.
// The noise pattern can be made to tile seamlessly to any integer period
// in x and any even integer period in y. Odd periods may be specified
// for y, but then the actual tiling period will be twice that number.
//
// The rotating gradients give the appearance of a swirling motion, and
// can serve a similar purpose for animation as motion along z in 3-D
// noise. The rotating gradients in conjunction with the analytic
// derivatives allow for "flow noise" effects as presented by Ken
// Perlin and Fabrice Neyret.
//


//
// 2-D tiling simplex noise with rotating gradients and analytical derivative.
// "vec2 x" is the point (x,y) to evaluate,
// "vec2 period" is the desired periods along x and y, and
// "float alpha" is the rotation (in radians) for the swirling gradients.
// The "float" return value is the noise value, and
// the "out vec2 gradient" argument returns the x,y partial derivatives.
//
// Setting either period to 0.0 or a negative value will skip the wrapping
// along that dimension. Setting both periods to 0.0 makes the function
// execute about 15% faster.
//
// Not using the return value for the gradient will make the compiler
// eliminate the code for computing it. This speeds up the function
// by 10-15%.
//
// The rotation by alpha uses one single addition. Unlike the 3-D version
// of psrdnoise(), setting alpha == 0.0 gives no speedup.
//
float psrdnoise(vec2 x, vec2 period, float alpha, out vec2 gradient) {

	// Transform to simplex space (axis-aligned hexagonal grid)
	vec2 uv = vec2(x.x + x.y*0.5, x.y);

	// Determine which simplex we're in, with i0 being the "base"
	vec2 i0 = floor(uv);
	vec2 f0 = fract(uv);
	// o1 is the offset in simplex space to the second corner
	float cmp = step(f0.y, f0.x);
	vec2 o1 = vec2(cmp, 1.0-cmp);

	// Enumerate the remaining simplex corners
	vec2 i1 = i0 + o1;
	vec2 i2 = i0 + vec2(1.0, 1.0);

	// Transform corners back to texture space
	vec2 v0 = vec2(i0.x - i0.y * 0.5, i0.y);
	vec2 v1 = vec2(v0.x + o1.x - o1.y * 0.5, v0.y + o1.y);
	vec2 v2 = vec2(v0.x + 0.5, v0.y + 1.0);

	// Compute vectors from v to each of the simplex corners
	vec2 x0 = x - v0;
	vec2 x1 = x - v1;
	vec2 x2 = x - v2;

	vec3 iu, iv;
	vec3 xw, yw;

	// Wrap to periods, if desired
	if(any(greaterThan(period, vec2(0.0)))) {
		xw = vec3(v0.x, v1.x, v2.x);
		yw = vec3(v0.y, v1.y, v2.y);
		if(period.x > 0.0)
			xw = mod(vec3(v0.x, v1.x, v2.x), period.x);
		if(period.y > 0.0)
			yw = mod(vec3(v0.y, v1.y, v2.y), period.y);
		// Transform back to simplex space and fix rounding errors
		iu = floor(xw + 0.5*yw + 0.5);
		iv = floor(yw + 0.5);
	} else { // Shortcut if neither x nor y periods are specified
		iu = vec3(i0.x, i1.x, i2.x);
		iv = vec3(i0.y, i1.y, i2.y);
	}

	// Compute one pseudo-random hash value for each corner
	vec3 hash = mod(iu, 289.0);
	hash = mod((hash*51.0 + 2.0)*hash + iv, 289.0);
	hash = mod((hash*34.0 + 10.0)*hash, 289.0);

	// Pick a pseudo-random angle and add the desired rotation
	vec3 psi = hash * 0.07482 + alpha;
	vec3 gx = cos(psi);
	vec3 gy = sin(psi);

	// Reorganize for dot products below
	vec2 g0 = vec2(gx.x,gy.x);
	vec2 g1 = vec2(gx.y,gy.y);
	vec2 g2 = vec2(gx.z,gy.z);

	// Radial decay with distance from each simplex corner
	vec3 w = 0.8 - vec3(dot(x0, x0), dot(x1, x1), dot(x2, x2));
	w = max(w, 0.0);
	vec3 w2 = w * w;
	vec3 w4 = w2 * w2;

	// The value of the linear ramp from each of the corners
	vec3 gdotx = vec3(dot(g0, x0), dot(g1, x1), dot(g2, x2));

	// Multiply by the radial decay and sum up the noise value
	float n = dot(w4, gdotx);

	// Compute the first order partial derivatives
	vec3 w3 = w2 * w;
	vec3 dw = -8.0 * w3 * gdotx;
	vec2 dn0 = w4.x * g0 + dw.x * x0;
	vec2 dn1 = w4.y * g1 + dw.y * x1;
	vec2 dn2 = w4.z * g2 + dw.z * x2;
	gradient = 10.9 * (dn0 + dn1 + dn2);

	// Scale the return value to fit nicely into the range [-1,1]
	return 10.9 * n;
}

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



// Procedural relief cross-section. No color/normal/displacement textures.
// A view-dependent 2.5D wall, not a watertight extruded mesh or volume solver.
vec4 riftRim(vec2 p,float t,float distance,float hw,out vec4 fringe){
 // Derivatives are evaluated before region branches. Invert the screen-to-plane
 // Jacobian: dFdx(distance) alone is not a local-space normal on a rotated plane.
 vec2 px=dFdx(p),py=dFdy(p);float det=px.x*py.y-px.y*py.x;
 vec2 screenGradient=vec2(dFdx(distance),dFdy(distance));
 vec2 gradient=abs(det)>1e-12?vec2(py.y*screenGradient.x-px.y*screenGradient.y,
    -py.x*screenGradient.x+px.x*screenGradient.y)/det:vec2(1.,0.);
 // The union/left-right medial axis is not differentiable. Bounded metric
 // prevents a discontinuity from projecting a spurious wall across the opening.
 float metric=clamp(length(gradient),.65,2.5);vec2 n=gradient/metric;
 n=normalize(n+vec2(1e-7));vec2 tangent=vec2(-n.y,n.x);
 // Keep relief coverage tied to the continuous distance bound. A per-pixel
 // gradient magnitude would emboss tiny medial-axis discontinuities as spikes.
 // Thickness at sharp corners remains approximate; normals use the Jacobian.
 float d=distance,aa=max(riftFootprint*.75,1e-5);
 float gate=smoothstep(0.,.04,riftEventGrowth())*(1.-smoothstep(.91,1.,u_shrink));
 float width=min(u_rimWidth,max(0.,hw)*.40)*sqrt(max(u_open,0.))*(1.-u_shrink);
 float depth=u_rimDepth*gate*min(1.,width/max(u_rimWidth,1e-5));
 fringe=vec4(0.);
 if(width<aa*.25||depth<.00001||d>width*2.8||d<-width-depth*2.2)return vec4(0.);
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 view=rotation*vec2(u_viewTiltX,u_viewTiltY);
 float facing=dot(n,view),wallWidth=max(aa,width+depth*facing*.65);
 float across=clamp(-d/wallWidth,0.,1.);
 // Mapping to the recessed wall. Tangential coordinates are never divided by
 // u_length/u_width. Stretching a rift exposes fresh procedural material.
 vec2 q=p+view*depth*across;
 float frequency=max(8.,u_frequency*1.7);
 float low=riftValueNoise(q*frequency*.29+vec2(u_seed*.41,t*.08));
 float phase=dot(q,vec2(17.3,5.7))*frequency+low*5.-t*.7;
 float bandLimit=1.-smoothstep(.25,.8,riftFootprint*frequency*18.2);
 float grooves=sin(phase)*bandLimit;
 float crest=.5+.5*grooves;
 vec3 normal=normalize(vec3(n*(depth/max(wallWidth,1e-5))+
    tangent*cos(phase)*bandLimit*u_rimRelief*.65, .55+across));
 vec3 light=normalize(vec3(-.45,.65,1.));
 float diffuse=max(dot(normal,light),0.);
 float spec=pow(max(dot(normal,normalize(light+normalize(vec3(view,1.)))),0.),24.);
 float wall=(1.-smoothstep(wallWidth*.78-aa,wallWidth+aa,-d))*smoothstep(-aa,aa,-d);
 wall*=1.-smoothstep(max(width*2.,aa),max(width*3.,aa*2.),-distance);
 float occlusion=mix(1.,.24,smoothstep(.15,1.,across));
 vec3 violet=mix(u_edgeColor,vec3(.35,.09,.75),.45);
 vec3 pigment=mix(violet*.14,u_edgeColor*.55,crest*.65+low*.2);
 vec3 col=pigment*(.30+diffuse*1.5)*occlusion;
 col+=u_edgeColor*pow(crest,12.)*bandLimit*.20*u_rimRelief*occlusion;
 col+=vec3(.48,.72,1.)*spec*.55*occlusion;
 // A narrow back lip and a dark recess separate wall from the distant world.
 float backLip=exp(-abs(d+wallWidth*.83)/max(aa,wallWidth*.035));
 col+=violet*backLip*.32;
 // Distinct suspended strands, depth ordered by radius and brightness. Each
 // samples q in physical local units; analytically filtered, no blurred mask.
 float outer=1.-smoothstep(-aa,aa,-d);
 for(int i=0;i<6;i++){
  if(float(i)>=2.+u_quality*2.)break;
  float k=float(i),z=(k+1.)/7.;
  vec2 field=q+n*depth*z+vec2(k*7.1,u_seed*.23);
  float flow=riftValueNoise(field*frequency*.8+vec2(-t*.19,t*.11));
  float target=width*(.12+z*1.35)+(flow-.5)*width*.65;
  float strandWidth=.0007+z*.0008;
  float line=exp(-pow((d-target)/max(strandWidth,aa),2.))*min(1.,strandWidth/aa);
  float packet=.15+.85*pow(.5+.5*sin(dot(field,vec2(11.,3.))*3.-t*(1.+z)),3.);
  vec3 tint=mix(u_edgeColor,vec3(.47,.13,1.),z);
  float opacity=line*packet*outer*(1.-z*.65)*u_corona;
  fringe.rgb+=tint*opacity*(1.2-z*.4);fringe.a+=opacity*.65;
 }
 fringe.a=clamp(fringe.a,0.,1.);
 return vec4(col*wall,wall);
}


// Independently implemented style extension. Research links do not imply copied engine code.
// Inputs/outputs are local-plane units, with the same lifetime and aperture contracts as core.
float riftSegStyle(vec2 p,vec2 a,vec2 b);
float riftStyleCenter(float y,float t){
 float s=u_style,clock=t*u_edgeMotion,scale=u_styleScale,offset=0.;
 if(s>4.5&&s<5.5){
  float row=floor(y*18.*scale),phase=clock*2.;
  float j=mix(riftHash11(row+floor(phase)+u_seed),riftHash11(row+ceil(phase)+u_seed),smoothstep(0.,1.,fract(phase)));
  offset=(j-.5)*.025;
 }else if(s>5.5&&s<6.5){offset=sin(y*5.*scale+clock*.7)*.055+sin(y*9.-clock*.3)*.018;}
 else if(s>7.5){offset=sin(y*8.*scale-clock)*.025;}
 return offset*u_styleAmount;
}
float riftStyleWidth(float y,float side,float t){
 float s=u_style,clock=t*u_edgeMotion,scale=u_styleScale,w=1.;
 if(s>1.5&&s<2.5)w=.85+.25*sin(y*11.*scale+side*1.7-clock*1.3);
 else if(s>2.5&&s<3.5){float segment=fract(y*5.*scale+side*.27);w=.7+.45*abs(segment*2.-1.);}
 else if(s>3.5&&s<4.5)w=.87+.18*cos(y*17.*scale+side*.9);
 else if(s>4.5&&s<5.5)w=.94+.12*riftHash11(floor(y*18.*scale)+side*7.+u_seed);
 else if(s>5.5&&s<6.5)w=.80+.32*riftValueNoise(vec2(y*6.*scale-clock*.32,side*13.+u_seed));
 else if(s>7.5)w=.95+.12*sin(y*12.*scale-clock*.8);
 return mix(1.,w,u_styleAmount);
}
float riftStyledDistance(vec2 p,float cx,float d){
 // Dual rift keeps an actual strip of original space between both independent openings.
 if(u_style>7.5&&u_styleAmount>0.){float divider=u_width*u_open*riftEventExpansion()*(1.-u_shrink)*.22*u_styleAmount;d=max(d,divider-abs(p.x-cx));}
 return d;
}
float riftStroke(vec2 p,vec2 a,vec2 b,float width){return exp(-riftSegStyle(p,a,b)/max(width,riftFootprint*.7))*min(1.,width/max(riftFootprint*.7,1e-7));}
float riftSegStyle(vec2 p,vec2 a,vec2 b){vec2 ab=b-a;float h=clamp(dot(p-a,ab)/max(dot(ab,ab),1e-8),0.,1.);return length(p-a-h*ab);}
float riftCellSeam(vec2 p){
 // Approximate F2-F1 boundary, used only in the finite edge/interior region, not as the main opening.
 vec2 cell=floor(p),f=fract(p);float first=100.,second=100.;
 for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
  vec2 id=cell+vec2(i,j),delta=vec2(i,j)+.15+.7*riftHash22(id+u_seed)-f;
  float d=dot(delta,delta);if(d<first){second=first;first=d;}else second=min(second,d);
 }return max(0.,sqrt(second)-sqrt(first));
}
vec4 riftStyleSurface(vec2 p,float t,float d,float cx,float hw,float edge,float opening){
 float style=u_style,clock=t*u_edgeMotion,scale=u_styleScale,outer=1.-opening;
 vec3 light=vec3(0.);float coverage=0.;
 float band=exp(-max(d,0.)/.13)*outer;
 if(style<.5||abs(p.y)>u_length*.5*riftEventGrowth()+.4)return vec4(0.);
 if(style<1.5){
  for(int i=0;i<3;i++){
   float k=float(i),wave=riftValueNoise(vec2(p.y*23.*scale+u_seed+k*17.,clock*.75+k))-.5;
   float target=.018+k*.024+wave*.045;
   float bolt=exp(-abs(d-target)/max(.0015,riftFootprint*.8));
   float packet=.18+.82*pow(.5+.5*sin(p.y*31.-clock*(4.+k)+k*7.),5.);
   light+=mix(u_edgeColor,vec3(.8,.92,1.),.55)*bolt*packet*outer;coverage+=bolt*packet*outer*.65;
  }
 }else if(style<2.5){
  float n=riftFbm(vec2(p.y*17.*scale-clock*.3,d*22.-clock*1.3+u_seed),22.*scale);
  float flame=band*pow(max(n,0.),2.)*(.5+.5*sin(d*55.-clock*2.+n*8.));
  light+=mix(u_edgeColor,vec3(.5,.04,.15),smoothstep(0.,.13,d))*flame*2.;coverage+=flame;
  vec2 q=vec2((p.x-cx)*30.,p.y*24.-clock*.55),id=floor(q);vec2 r=fract(q)-.5;
  float ember=exp(-dot(r,r)*200.)*step(.91,riftHash21(id+u_seed))*band;
  light+=vec3(1.,.24,.03)*ember*2.;coverage+=ember;
 }else if(style<3.5){
  float seam=riftCellSeam(p*16.*scale);
  float lines=exp(-seam/max(.018,riftFootprint*16.*scale))*band;
  vec3 spectrum=.5+.5*cos(vec3(0.,2.1,4.2)+p.y*6.+d*35.);
  light+=(spectrum*edge*.85+u_edgeColor*lines*.65);coverage+=lines*.32;
 }else if(style<4.5){
  float spacing=.16/scale,row=floor((p.y+.5*spacing)/spacing),localY=(p.y-row*spacing)/spacing;
  vec2 glyph=vec2((abs(p.x-cx)-hw-.065)/spacing,localY);
  float a=riftHash11(row+u_seed),sigil=0.;
  sigil+=riftStroke(glyph,vec2(-.16,-.33),vec2(-.16,.33),.014);
  sigil+=riftStroke(glyph,vec2(-.16,.20),vec2(.18,a>.5?.34:-.04),.014);
  sigil+=riftStroke(glyph,vec2(-.16,-.03),vec2(.15,-.26),.014);
  sigil+=riftStroke(glyph,vec2(.15,-.26),vec2(.15,.22),.011)*step(.4,a);
  float cadence=.4+.6*pow(.5+.5*sin(row*.7-clock*1.7),3.);
  light+=u_edgeColor*sigil*cadence;coverage+=clamp(sigil,0.,1.)*.7;
 }else if(style<5.5){
  float row=floor(p.y*38.*scale),gate=step(.64,riftHash11(row+u_seed));
  float scan=exp(-pow(fract(p.y*38.*scale-clock*.3)-.5,2.)/max(.001,riftFootprint*8.));
  light+=mix(u_edgeColor,vec3(.9,.15,.75),gate)*(scan*band*.32+edge*gate*.8);
  coverage+=scan*band*.18;
 }else if(style<6.5){
  float flow=riftFbm(vec2(p.y*7.*scale-clock*.12,d*18.+sin(p.y*5.+clock)*.9),18.*scale);
  float strokes=pow(max(.0,1.-abs(flow*2.-.7)),7.);
  light+=u_edgeColor*band*strokes*.22;coverage+=band*strokes*.55;
 }else if(style<7.5){
  float wavelength=.058/scale,phase=fract(clock*.18);
  for(int i=0;i<4;i++){
   float target=(float(i)+phase)*wavelength+.024;
   float ring=exp(-abs(d-target)/max(.0013,riftFootprint))*(1.-float(i)/4.5)*outer;
   light+=u_edgeColor*ring*.5;coverage+=ring*.36;
  }
 }else{
  float twist=.5+.5*sin(p.y*22.*scale-clock*2.5);
  vec3 tint=mix(u_edgeColor,vec3(.22,.65,1.),step(0.,p.x-cx));
  light+=tint*(edge*twist*.7+band*pow(twist,12.)*.4);coverage+=band*pow(twist,12.)*.2;
 }
 return vec4(light*u_styleAmount,clamp(coverage*u_styleAmount,0.,1.));
}
vec2 riftStyleWarp(vec2 p,float t,float d,float cx){
 float outer=(1.-smoothstep(-.005,.003,-d))*exp(-max(d,0.)/.16),clock=t*u_edgeMotion;
 vec2 v=vec2(0.);
 if(u_style>4.5&&u_style<5.5){float row=floor(p.y*38.*u_styleScale);v.x=(riftHash11(row+u_seed)-.5)*.065*(.5+.5*sin(clock*3.+row));}
 if(u_style>6.5&&u_style<7.5)v=normalize(vec2(p.x-cx+.0001,.02))*sin(d*100.-clock*4.)*.018;
 return v*outer*u_styleAmount;
}
vec3 riftStyleInteriorRaw(vec2 p,float t,vec3 base){
 float clock=t*u_insideSpeed;
 if(u_style>1.5&&u_style<2.5)return base*.15+u_insideColor*pow(riftValueNoise(p*13.+vec2(0.,-clock)),4.);
 if(u_style>2.5&&u_style<3.5){float seam=riftCellSeam(p*12.*u_styleScale);float facets=riftHash21(floor(p*9.));return base*.5+u_insideColor*(.12+facets*.28)+u_edgeColor*exp(-seam/.02)*.12;}
 if(u_style>4.5&&u_style<5.5){float scan=pow(.5+.5*sin(p.y*180.-clock*3.),5.);return base*.25+u_insideColor*.2+u_edgeColor*scan*.07;}
 if(u_style>5.5&&u_style<6.5){float ink=riftFbm(p*8.+vec2(clock*.06,0.),8.);return u_insideColor*(.08+pow(ink,3.)*.9);}
 return base;
}

vec3 riftStyleInterior(vec2 p,float t,vec3 base){return mix(base,riftStyleInteriorRaw(p,t,base),u_styleAmount);}

// Adapted from existing cosmic-sky-lab/workbench/sky-core.glsl.
// Source SHA256: cf8bb2bc5816c487b4477f8342538f09fa79ee3b0aa0152c1a2945a281c863f2
// Existing project algorithm reused; detached from world-sky uniforms.
vec3 riftInterior_skyHash(vec3 p) {
    // Integer lattice hash is stable within a recipe, across time and translation.
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float riftInterior_skyNoise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(riftInterior_skyHash(i).x,riftInterior_skyHash(i+vec3(1,0,0)).x,f.x),
                   mix(riftInterior_skyHash(i+vec3(0,1,0)).x,riftInterior_skyHash(i+vec3(1,1,0)).x,f.x),f.y),
               mix(mix(riftInterior_skyHash(i+vec3(0,0,1)).x,riftInterior_skyHash(i+vec3(1,0,1)).x,f.x),
                   mix(riftInterior_skyHash(i+vec3(0,1,1)).x,riftInterior_skyHash(i+vec3(1,1,1)).x,f.x),f.y),f.z);
}
float riftInterior_skyFbm(vec3 p, int octaves) {
    float result=0.0, amplitude=0.5, frequency=1.0;
    for(int i=0;i<6;i++) {
        if(i>=octaves) break;
        float band=1.-smoothstep(.22,.65,riftFootprint*frequency*6.);
        result+=amplitude*mix(.5,riftInterior_skyNoise(p),band);frequency*=2.03;
        p=vec3(0.8*p.x-0.6*p.z,p.y,0.6*p.x+0.8*p.z)*2.03+vec3(7.7,19.1,13.3);
        amplitude*=0.5;
    }
    return result;
}
vec3 riftInterior_galaxyDirection(vec3 d) {
    float cy=cos(vec4(0.,.18,.14,1.8).x),sy=sin(vec4(0.,.18,.14,1.8).x);
    float ct=cos(vec4(0.,.18,.14,1.8).y),st=sin(vec4(0.,.18,.14,1.8).y);
    d=vec3(cy*d.x+sy*d.z,d.y,-sy*d.x+cy*d.z);
    return vec3(ct*d.x-st*d.y,st*d.x+ct*d.y,d.z);
}
vec3 riftInterior_skyStars(vec3 d, float frequency, float layer, float clustering) {
    vec3 p=d*frequency, cell=floor(p);
    float footprint=riftFootprint*frequency;
    vec3 result=vec3(0.0);
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
        vec3 id=cell+vec3(x,y,z);
        vec3 r=riftInterior_skyHash(id+vec3(layer,u_seed*0.011,u_seed*0.017));
        float select=riftInterior_skyHash(id+vec3(173.1,u_seed*0.021,layer)).y;
        float coverage=clamp(vec4(.055,2.2,.65,.15).x*(0.68+0.65*clustering),0.0,1.0);
        float exists=1.0-step(coverage,select);
        vec3 center=id+0.12+r*0.76;
        float radius=(0.028+0.085*r.z*r.z)*vec4(.055,2.2,.65,.15).z;
        float width=max(radius,footprint*0.62);
        vec3 delta=p-center;
        float distance2=dot(delta,delta);
        // Candidates are searched only within +/-1 cells. A removed/new center is
        // at least 1.12 units away across a cell boundary. All light, including
        // tangent spikes and halos, must reach zero before that boundary.
        // Tangent-only exponential tails otherwise leave dashed grid-plane seams.
        exists*=1.0-smoothstep(0.75*0.75,1.0,distance2);
        float core=exp(-distance2/(width*width));
        float energy=radius*radius/(width*width);
        float beat=0.5+0.5*sin((u_time*u_speed*u_insideSpeed)*vec4(.2,.8,0.,0.).x*(1.1+2.0*r.x)+r.y*35.0);
        float twinkle=mix(1.0,0.18+1.82*beat*beat,vec4(.055,2.2,.65,.15).w);
        float bright=2.0+7.0*pow(r.y,7.0);
        vec3 tint=mix(vec3(.38,.58,1.),vec3(1.,.7,.44),r.x);
        result+=exists*core*energy*tint*bright*twinkle;
        // Rare larger stars gain a local spherical glow and tangent-space spikes.
        if(r.y>0.96 && layer<20.0) {
            vec3 radial=normalize(center);
            vec3 axis=abs(radial.y)<0.95?vec3(0,1,0):vec3(1,0,0);
            vec3 side=normalize(cross(radial,axis)),up=cross(side,radial);
            vec2 uv=vec2(dot(delta,side),dot(delta,up));
            float thin=max(radius*0.24,footprint*0.6),longer=radius*6.0;
            float spikes=exp(-abs(uv.x)/longer-uv.y*uv.y/(thin*thin))+
                         exp(-abs(uv.y)/longer-uv.x*uv.x/(thin*thin));
            float halo=exp(-distance2/(radius*radius*14.0));
            result+=exists*tint*(halo*0.14+spikes*0.30)*energy*twinkle;
        }
    }
    return result*vec4(.055,2.2,.65,.15).y;
}
vec3 riftInterior_skyGalaxy(vec3 d, out float clustering) {
    vec3 g=riftInterior_galaxyDirection(d);
    float band=exp(-pow(g.y/max(vec4(0.,.18,.14,1.8).z,0.04),2.0));
    vec3 offset=vec3(u_seed*0.0011,u_seed*0.0017,u_seed*0.0023);
    float grains=riftInterior_skyFbm(g*5.0+offset,int(u_quality)+3);
    float knots=riftInterior_skyFbm(g*12.0+offset+17.0,int(u_quality)+2);
    float dense=band*smoothstep(0.20,0.72,grains);
    float core=pow(max(g.z,0.0),12.0)*band;
    float ridge=1.0-abs(2.0*knots-1.0);
    float dark=pow(ridge,3.0)*exp(-pow((g.y+0.02)/(vec4(0.,.18,.14,1.8).z*0.48),2.0));
    float transmission=exp(-dark*vec4(2.8,3.2,1.4,1.).z*3.2);
    clustering=band*(0.4+0.6*grains)*transmission;
    vec3 light=vec3(.28,.36,.82)*(dense*0.38+core*0.24)*(0.55+0.7*knots);
    light+=mix(vec3(.18,.08,.45),vec3(.06,.25,.62),grains)*dense*dense*0.20;
    return light*vec4(0.,.18,.14,1.8).w*transmission;
}
vec3 riftInterior_skyNebula(vec3 d) {
    vec3 origin=vec3(u_seed*0.0013,u_seed*0.0009,u_seed*0.0019);
    // Both drift and evolving warp are perceptible over a few seconds at default speed.
    float flow=(u_time*u_speed*u_insideSpeed)*vec4(.2,.8,0.,0.).y*0.15;
    vec3 p=d*vec4(2.8,3.2,1.4,1.).y+origin;
    vec3 warp=vec3(riftInterior_skyFbm(p+vec3(flow,0,0),int(u_quality)+2),
                   riftInterior_skyFbm(p+vec3(9.1,flow*0.7,3.7),int(u_quality)+2),
                   riftInterior_skyFbm(p+vec3(4.3,8.1,-flow*0.6),int(u_quality)+2));
    float dust=riftInterior_skyFbm(p*1.65+warp*2.1+vec3(flow*0.3,-flow*0.15,0),int(u_quality)+3);
    float envelope=riftInterior_skyFbm(d*2.2+origin+vec3(17.2,1.8,6.3),3);
    float density=smoothstep(0.34,0.70,dust)*smoothstep(0.30,0.67,envelope);
    float filaments=pow(max(1.0-abs(dust*2.0-1.0),0.0),9.0);
    vec3 tint=mix(vec3(.18,.08,.45),vec3(.06,.25,.62),smoothstep(0.30,0.65,warp.y));
    return (tint*density*0.6+tint*filaments*density*0.18)*vec4(2.8,3.2,1.4,1.).x;
}

// Actual existing sky algorithms are linked by build.py after this shared geometry module.
vec3 riftCosmicInterior(vec2 p,float t){
 vec3 d=normalize(vec3(p.y*.62,p.x*.84,1.));float clustering;
 vec3 col=riftInterior_skyGalaxy(d,clustering)+riftInterior_skyNebula(d);
 col+=riftInterior_skyStars(d,95.,3.,clustering);
 if(u_quality>.5&&u_layers>1.5)col+=riftInterior_skyStars(d,170.,7.,clustering)*.65;
 // Logarithmic spiral galaxies add resolved structures within the existing sky field.
 for(int i=0;i<3;i++){
  if(float(i)>u_quality+.5||float(i)>=u_layers)break;
  float fi=float(i);vec2 center=vec2((fi-1.)*.035,(fi-1.)*u_length*.28);
  vec2 q=(p-center)/vec2(.12+.035*fi,.21+.04*fi);
  float a=.4+fi*1.1,ca=cos(a),sa=sin(a);q=mat2(ca,-sa,sa,ca)*q;
  float radius=length(q),angle=atan(q.y,q.x),twist=angle*3.-log(radius+.08)*5.5-t*u_insideSpeed*.07;
  float noise=riftFbm(q*3.1+u_seed,22.);
  float arms=pow(.5+.5*sin(twist+noise*2.7),7.)*exp(-radius*1.4);
  float dust=pow(.5+.5*sin(twist+.6),3.)*exp(-radius*1.5);
  col+=mix(vec3(.15,.3,1.1),vec3(.7,.24,.66),noise)*(arms*.9+dust*.1)*u_galaxies;
  col+=vec3(1.5,.62,.24)*exp(-radius*9.)*u_galaxies;
 }
 return col*(.6+u_insideColor*3.5);
}

// Focused, clean horizontal rift. Reuses the cached spine, lifecycle, interior
// sky functions, adapter and shared parameter schema. No peripheral particles.
float horizonFbm(vec2 p,float alpha,float scale){
 float value=0.,amplitude=.5;
 for(int i=0;i<7;i++){
  if(float(i)>=min(u_detail,3.+u_quality*2.))break;
  vec2 gradient;float n=psrdnoise(p,vec2(0.),alpha,gradient);
  float lod=1.-smoothstep(.22,.62,riftFootprint*scale);
  value+=amplitude*n*lod;
  p=mat2(.8,-.6,.6,.8)*p*2.03+vec2(13.1,7.9);scale*=2.03;amplitude*=.43;
 }return value;
}
float riftHorizonMain(vec2 p,float t,out float cx,out float hw){
 float growth=riftEventGrowth(),halfL=max(.00001,u_length*.5*growth*(1.-u_shrink));
 // The default +pi/2 rotation maps positive local y to screen-left.
 float centerY=u_length*.5-halfL;
 float y=clamp(p.y,centerY-halfL,centerY+halfL),s=(y-centerY)/halfL;
 cx=riftSpine(y)*.20+sin(y*4.7+u_seed)*u_bend*.055;
 cx+=sin(y*5.1+t*.4)*u_dynamic*.008;
 float side=p.x<cx?-1.:1.;
 // Independent sides. Domain warp is static in time; seed does not reshuffle.
 vec2 base=vec2(y*u_frequency*.20,side*31.7+u_seed*.137),g;
 float warp=psrdnoise(base*.32+19.,vec2(0.),0.,g);
 float coarse=horizonFbm(base+g*u_domainWarp*.33,0.,u_frequency*.20);
 float micro=horizonFbm(vec2(y*u_frequency*2.4,side*19.+u_seed*.31),0.,u_frequency*2.4);
 float taper=pow(max(0.,1.-s*s),u_tipPower);
 float asym=1.+u_roughness*(coarse*.32+micro*.025);
 float tongues=0.;
 for(int i=0;i<5;i++){
  float k=float(i)+u_seed*.37+side*17.;float anchor=(riftHash11(k+7.7)-.5)*1.6;
  float span=mix(.07,.21,riftHash11(k+21.));float v=(s-anchor)/span;
  tongues+=pow(max(0.,1.-abs(v)/(v<0.?.65:1.45)),1.8)*mix(.12,.48,riftHash11(k+31.));
 }
 float localOpen=1.-u_localClose*exp(-pow((y/u_length-.13)*9.,2.));
 hw=u_width*(1.+u_mode*.55)*riftEventExpansion()*u_open*(1.-u_shrink)*taper*max(.28,asym+tongues*u_roughness)*localOpen;
 vec2 ds=vec2(abs(p.x-cx)-hw,abs(p.y-centerY)-halfL);
 return length(max(ds,vec2(0.)))+min(max(ds.x,ds.y),0.);
}
vec3 riftHorizonInterior(vec2 p,float t){
 // Reuse the project's directional sky and three-dimensional nebula fields.
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 viewLocal=rotation*vec2(u_viewTiltX,u_viewTiltY),view=viewLocal.yx,uv=vec2(p.y,p.x);
 uv+=view*.10*u_interiorDepth;
 vec3 dir=normalize(vec3(uv*.63,1.2*u_interiorDepth));float clustering;
 float footprint=riftFootprint;riftFootprint*=.63/max(.24,1.2*u_interiorDepth);
 vec3 sky=riftInterior_skyGalaxy(dir,clustering)*.26+riftInterior_skyNebula(dir)*.10;
 if(u_starDensity>0.){
  sky+=riftInterior_skyStars(dir,105.*sqrt(u_starDensity),3.,clustering)*.85;
  if(u_quality>.5&&u_layers>1.5)sky+=riftInterior_skyStars(dir,177.*sqrt(u_starDensity),7.,clustering)*.50;
  if(u_quality>1.5&&u_layers>2.5)sky+=riftInterior_skyStars(dir,265.*sqrt(u_starDensity),11.,clustering)*.35;
 }
 riftFootprint=footprint;
 if(u_starDensity>0.)sky+=riftStars(uv+u_seed*.113,75.*sqrt(u_starDensity))*vec3(.48,.60,1.4)*1.8;
 // A resolved inclined spiral, independent of aperture dimensions, not three
 // equal repeating vortex stamps. Central angle is suppressed as radius→0.
 vec2 q=(uv-vec2(u_galaxyX,u_galaxyY))/max(.1,u_galaxyScale);
 q=mat2(.94,-.342,.342,.94)*q;q.y*=2.5;
 float radius=length(q),angle=atan(q.y,q.x),safeRadius=max(radius,.012);
 float phase=angle*3.-log(safeRadius)*5.3-t*u_insideSpeed*.11;
 float angularGate=smoothstep(.015,.12,radius);
 float clouds=horizonFbm(q*7.+u_seed*.017,t*u_insideSpeed*.02,18.);
 float spiral=pow(max(0.,.5+.5*cos(phase+clouds*1.1)),3.5)*angularGate;
 float envelope=exp(-radius*1.6)*(1.-smoothstep(1.8,3.4,radius));
 vec3 arms=mix(vec3(.10,.22,.85),vec3(.66,.14,.72),.5+.5*clouds);
 sky+=arms*(spiral*1.4+.045)*envelope*u_galaxies;
 sky+=vec3(1.6,.95,.72)*exp(-radius*19.)*u_galaxies;
 sky+=vec3(.018,.025,.075);
 return sky*(.7+u_insideColor*2.)*u_interiorBrightness;
}
vec3 horizonEdgeLight(vec2 p,float t,float d,float hw,out float coverage){
 float aa=max(riftFootprint*.7,.00001),reach=min(u_edgeReach,max(hw,.001)*.7+.002);
 vec2 g;vec2 flowP=vec2(p.y,p.x)*u_frequency*1.3+u_seed*.073;
 float wave=psrdnoise(flowP,vec2(0.),t*u_edgeMotion*.4,g);
 float flowLod=1.-smoothstep(.25,.7,riftFootprint*u_frequency*1.3);wave*=flowLod;g*=flowLod;
 // Two-level gradient advection, not a radial UV division with a center pinch.
 vec2 g2;float fine=psrdnoise(flowP*2.4+g*.35,vec2(0.),-t*u_edgeMotion*.27,g2);
 float freq=u_frequency*3.12,lod=1.-smoothstep(.25,.7,riftFootprint*freq);
 fine*=lod;
 float width=max(min(u_edgeThickness,reach*.22),.00001);
 float lip=exp(-abs(d)/max(width,aa))*min(1.,width/aa);
 vec3 color=mix(vec3(.17,.36,1.),u_edgeColor,.5)*lip;
 color+=vec3(.48,.78,1.)*lip*.65;
 coverage=lip*.8;
 for(int i=0;i<4;i++){
  if(float(i)>=2.+u_quality)break;
  float k=float(i),target=reach*(.18+k*.19)+(wave*.12+fine*.05)*reach;
  float strand=.00055+k*.00022;
  float line=exp(-pow((d-target)/max(aa,strand),2.))*min(1.,strand/aa);
  float pulse=.2+.8*pow(.5+.5*sin(p.y*9.+k*3.-t*u_edgeMotion*1.5+wave*3.),3.);
  color+=mix(vec3(.16,.36,1.),vec3(.61,.10,1.),k/3.)*line*pulse*u_filaments;
  coverage+=line*pulse*.5*u_filaments;
 }
 float corona=exp(-abs(d-reach*.28)/max(reach*.3,.0001))*(.35+.65*wave*wave)*u_corona*.17;
 float bounded=1.-smoothstep(reach*.9,reach*1.45,max(d,0.));
 color+=mix(vec3(.12,.21,.8),vec3(.54,.08,.9),.5+.5*wave)*corona;
 coverage=clamp((coverage+corona*.4)*bounded,0.,1.);
 return color*bounded;
}
RiftSample riftHorizonSurface(vec2 p,float t,float d,float cx,float hw,float opening,float alive){
 RiftSample r;r.distance=d;r.opening=opening;r.warp=vec2(0.);r.interiorVisibility=opening;r.surface=vec4(0.);
 float coverage;vec3 energy=horizonEdgeLight(p,t,d,hw,coverage);
 float aa=max(riftFootprint,.00001),wallWidth=min(u_rimWidth,hw*.35);
 float inner=1.-smoothstep(-wallWidth-aa,-aa,d);
 // A narrow shadowed inner lip. No heavy metallic extrusion or hanging spikes.
 float wall=opening*(1.-inner)*smoothstep(0.,.008,u_rimDepth);
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 interiorP=p+rotation*vec2(u_viewTiltX,u_viewTiltY)*u_rimDepth*wall;
 vec3 interior=vec3(0.);if(opening>.0001)interior=riftInterior(interiorP,t);
 float shaded=clamp(.5-u_rimRelief*.3,.08,.5);
 interior*=mix(1.,shaded,wall);
 r.interiorVisibility=opening*(1.-wall*(1.-shaded));
 float life=u_opacity*alive;
 vec3 col=interior*opening+energy*u_glow;
 r.surface=vec4((1.-exp(-max(col,vec3(0.))*1.2))*life,clamp(opening+coverage*.7,0.,1.)*life);
 float reach=max(.004,u_edgeReach),field=exp(-abs(d)/(reach*.35))*(1.-opening);
 field*=1.-smoothstep(reach*.9,reach*1.45,max(d,0.));
 float side=sign(p.x-cx);
 r.warp=vec2(side,sin(p.y*7.+t*.3)*.08)*.013*u_distortion*field*life;
 return r;
}
vec3 riftHorizonEdgeDebug(vec2 p,float t){float cx,hw,d=riftHorizonMain(p,t,cx,hw),coverage;return horizonEdgeLight(p,t,d,hw,coverage)*u_glow;}

void mainImage(out vec4 fragColor,in vec2 fragCoord){
 u_spine[0]=vec4(0.,-.5*u_length,0.,0.);u_spine[32]=vec4(0.,.5*u_length,0.,0.);
 for(int level=0;level<5;level++){int stride=32/int(exp2(float(level)));for(int j=0;j<16;j++){int a=j*stride;if(a>=32)break;int b=a+stride,m=(a+b)/2;float x=(u_spine[a].x+u_spine[b].x)*.5+(riftHash11(float(m)+u_seed+float(level)*71.)-.5)*u_bend*1.65*pow(.53,float(level));u_spine[m]=vec4(x,(float(m)/32.-.5)*u_length,0.,0.);}}
 for(int i=0;i<8;i++){float fi=float(i),y=(fi/7.*1.6-.8)*u_length*.5;float cx=riftSpine(y);float side=mod(fi,2.)<.5?-1.:1.;float reach=u_branchLength*(.65+riftHash11(fi+u_seed)*.65);vec2 a=vec2(cx,y),b=a+vec2(side*(u_width*(1.+u_mode)+reach),reach*(riftHash11(fi+13.)-.5));u_branchA[i]=vec4(a,mix(a,b,.55));u_branchB[i]=vec4(b,.3,.4);}
 vec2 uv=(fragCoord-.5*iResolution.xy)*2./iResolution.y/u_zoom;
 mat2 rot=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));vec2 p=rot*uv;
 riftFootprint=max(length(dFdx(p)),length(dFdy(p)));RiftSample r=riftEvaluate(p,iTime*u_speed);
 vec2 q=uv+transpose(rot)*r.warp;vec3 bg=vec3(riftBackground(q*(1.+u_dispersion),iTime).r,riftBackground(q,iTime).g,riftBackground(q*(1.-u_dispersion),iTime).b);
 fragColor=vec4(bg*(1.-r.surface.a)+r.surface.rgb,1.);
}
