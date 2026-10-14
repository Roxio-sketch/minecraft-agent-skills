#version 150
uniform sampler2D Sampler0,Sampler1;
uniform mat4 InvViewProjection,ViewProjection;
uniform vec3 CenterRelative;
uniform float WorldScale,LensStrength,LensRadius,uExposure;
uniform float HaloStrength,HaloRing,HaloDisk,HaloAura;
vec2 worldDiskPattern(float radius,float angle);
vec3 haloEmission(vec3 q);
#define BH_PATTERN worldDiskPattern
#define BH_VOLUME_SAMPLE(q,ds,result) result.light+=result.transmission*haloEmission(q)*ds
in vec2 texCoord;out vec4 fragColor;
vec3 scenePosition(vec2 uv,float depth){
    vec4 p=InvViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
bool onScreen(vec2 uv){return all(greaterThan(uv,vec2(.001)))&&all(lessThan(uv,vec2(.999)));}
bool sceneBlocked(vec3 local){
    vec3 p=CenterRelative+local*WorldScale;
    vec4 clip=ViewProjection*vec4(p,1.0);
    if(clip.w<=0.0)return false;
    vec2 uv=clip.xy/clip.w*.5+.5;
    if(!onScreen(uv))return false;
    float depth=texture(Sampler1,uv).r;
    if(depth>=.999999)return false;
    vec3 surface=scenePosition(uv,depth);
    // Compare camera-to-point Euclidean distances for the same projected ray.
    return length(p)>length(surface)+.08;
}
#define BH_SCENE_BLOCKED(p) sceneBlocked(p)
// Emissivity follows the transported field as well as density, retaining visible
// spiral filaments instead of saturating into a uniform glowing band.
#define BH_DISK_EMISSION(pattern) ((.25+.75*pow(pattern.x,1.3))*(.65+.35*pattern.y))
// Original artistic black-hole field. Not a relativistic geodesic solver.
// Host supplies a ray in one orthonormal material space, seconds and linear colors.
uniform float uTime, uCore, uInner, uOuter, uThickness, uTilt;
uniform float uBend, uDensity, uSpeed, uInfall, uSeed;
uniform vec3 uHot, uCool, uCoreColor;
uniform int uSteps;

// Optional host hook: test a point against an independently copied scene depth.
// Internal materials leave it disabled. It is a screen-space approximation.
#ifndef BH_SCENE_BLOCKED
#define BH_SCENE_BLOCKED(p) false
#endif
#ifndef BH_DISK_EMISSION
#define BH_DISK_EMISSION(pattern) 1.0
#endif
#ifndef BH_PATTERN
#define BH_PATTERN bhDiskPattern
#endif
#ifndef BH_VOLUME_SAMPLE
#define BH_VOLUME_SAMPLE(q,ds,result)
#endif
struct BHResult { vec3 light; float transmission; vec3 direction; vec3 position; float captured; float exhausted; float blocked; };

vec3 bhRotateX(vec3 p, float a) {
    float c=cos(a), s=sin(a);
    return vec3(p.x, c*p.y-s*p.z, s*p.y+c*p.z);
}
float bhHash(vec3 p) {
    // Integer cells avoid amplifying transcendental rounding through sin/fract.
    uvec3 cell=uvec3(ivec3(p));
    uint h=(cell.x*0x8da6b343u)^(cell.y*0xd8163841u)^(cell.z*0xcb1ab31fu)^uint(uSeed);
    h^=h>>16u;h*=0x7feb352du;h^=h>>15u;h*=0x846ca68bu;h^=h>>16u;
    return float(h>>8u)*(1.0/16777216.0);
}
vec3 bhBackground(vec3 direction) {
    // Smooth low-frequency field on the sphere, without longitude seams.
    vec3 d=normalize(direction);
    float band=exp(-pow((d.y+0.24*d.x)/0.17,2.0));
    float textureField=0.5+0.5*sin(18.0*d.x+sin(13.0*d.z)+uSeed);
    vec3 col=vec3(0.0006,0.0012,0.003)+band*textureField*vec3(0.008,0.012,0.025);
    // Sparse 3-D cells sampled along a spherical shell. Neighbors prevent cell clipping.
    vec3 p=d*38.0, cell=floor(p);
    for(int z=-1;z<=1;z++) for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec3 c=cell+vec3(float(x),float(y),float(z));
        if(bhHash(c)<0.035) {
            vec3 center=c+vec3(bhHash(c+11.0),bhHash(c+37.0),bhHash(c+71.0));
            float dist=length(p-center);
            float star=exp(-dist*dist/0.035);
            col+=star*mix(vec3(0.35,0.6,1.0),vec3(1.0,0.65,0.3),bhHash(c+97.0))*1.5;
        }
    }
    return col;
}
vec2 bhSphere(vec3 ro,vec3 rd,float radius) {
    float b=dot(ro,rd), h=b*b-dot(ro,ro)+radius*radius;
    if(h<0.0) return vec2(-1.0);
    float s=sqrt(h); return vec2(-b-s,-b+s);
}
vec3 bhTurn(vec3 p,vec3 direction) {
    float r=max(length(p),0.0001);
    vec3 inward=-p/r;
    return (inward-direction*dot(inward,direction))*(uBend/max(r*r,0.08));
}
vec2 bhDiskPattern(float radius,float angle) {
    // Integer angular harmonics keep the material periodic at atan's branch cut.
    float phase=angle-uTime*uSpeed/(0.45+radius);
    // Positive radial phase transport moves a fixed feature toward smaller radius:
    // radius(t) = radius(0) - uInfall*t when angular motion is disabled.
    // Both radial frequencies are integers, so wrapping by 2*pi is continuous.
    float advectedRadius=radius+mod(uTime*uInfall,6.28318530718);
    float ribbon=0.5+0.5*sin(7.0*phase+10.0*advectedRadius+uSeed);
    float detail=0.5+0.5*sin(19.0*phase-17.0*advectedRadius+0.7*sin(3.0*phase));
    return vec2(ribbon,detail);
}
void bhDiskSample(vec3 q,float ds,inout BHResult result) {
    float radius=length(q.xz);
    if(radius<=uInner || radius>=uOuter)return;
    float radial=smoothstep(uInner,uInner+0.22,radius)*(1.0-smoothstep(uOuter-0.35,uOuter,radius));
    float vertical=exp(-0.5*q.y*q.y/(uThickness*uThickness));
    vec2 pattern=BH_PATTERN(radius,atan(q.z,q.x));
    float sigma=uDensity*radial*vertical*(0.3+0.5*pattern.x+0.2*pattern.y);
    float opacity=1.0-exp(-sigma*ds);
    float hot=pow(1.0-clamp((radius-uInner)/(uOuter-uInner),0.0,1.0),0.65);
    vec3 source=mix(uCool,uHot,hot)*(1.5+5.0*hot)*BH_DISK_EMISSION(pattern);
    result.light+=result.transmission*opacity*source;
    result.transmission*=1.0-opacity;
}
BHResult bhTrace(vec3 ro,vec3 rd) {
    BHResult result;
    result.light=vec3(0.0); result.transmission=1.0;
    result.direction=normalize(rd); result.position=ro; result.captured=0.0; result.exhausted=0.0; result.blocked=0.0;
    float bound=uOuter*1.5;
    vec2 hit=bhSphere(ro,result.direction,bound);
    if(hit.y<=0.0) return result;
    vec3 p=ro+result.direction*max(0.0,hit.x);
    result.position=p;
    // Segment length changes with quality; optical thickness follows the segment length.
    float ds=3.0*bound/float(uSteps);
    float a=radians(-uTilt);
    bool ended=false;
    for(int i=0;i<256;i++) {
        if(i>=uSteps) break;
        if(BH_SCENE_BLOCKED(p)) { result.blocked=1.0; ended=true; break; }
        float r=max(length(p),0.0001);
        if(r<uCore) {
            // Core source is behind the accumulated foreground disk, in linear RGB.
            result.light+=result.transmission*uCoreColor;
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        // Midpoint integration reduces quality-dependent deflection of the ray itself.
        vec3 midDir=normalize(result.direction+bhTurn(p,result.direction)*(0.5*ds));
        vec3 midPos=p+result.direction*(0.5*ds);
        vec3 nextDir=normalize(result.direction+bhTurn(midPos,midDir)*ds);
        // Segment/core test prevents a large integration step skipping the dark core.
        float along=clamp(-dot(p,midDir),0.0,ds);
        if(length(p+midDir*along)<uCore) {
            result.light+=result.transmission*uCoreColor;
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        BH_VOLUME_SAMPLE(bhRotateX(midPos,a),ds,result);
        vec3 q0=bhRotateX(p,a),q1=bhRotateX(p+midDir*ds,a);
        // Integrate the thin layer, instead of taking one sample that can miss it.
        // Bound is <= 8 samples for current parameter ranges (16 allows headroom).
        float slab=6.0*uThickness;
        if(min(q0.y,q1.y)<slab && max(q0.y,q1.y)>-slab) {
            int samples=int(clamp(ceil(ds/(0.5*uThickness)),1.0,16.0));
            float subDs=ds/float(samples);
            for(int j=0;j<16;j++) {
                if(j>=samples)break;
                bhDiskSample(mix(q0,q1,(float(j)+0.5)/float(samples)),subDs,result);
            }
        }
        p+=midDir*ds; result.direction=nextDir; result.position=p;
        if(length(p)>bound+0.001 && dot(p,nextDir)>0.0) { ended=true; break; }
        if(result.transmission<0.002) { result.transmission=0.0; ended=true; break; }
    }
    // Diagnostic flag: finite path budget is an approximation, never physical capture.
    if(!ended) result.exhausted=1.0;
    return result;
}
vec3 bhCompositeInternal(BHResult result) {
    return result.light+result.transmission*bhBackground(result.direction);
}
// Original periodic cell noise. The user-selected blackhole-main reference
// motivates differential rotation and multiscale granular bands, not a code copy.
float fieldCell(ivec2 p){
    uint h=uint(p.x)*0x8da6b343u^uint(p.y)*0xd8163841u;
    h^=h>>16u;h*=0x7feb352du;h^=h>>15u;h*=0x846ca68bu;h^=h>>16u;
    return float(h>>8u)/16777216.0;
}
float fieldNoise(vec2 p,vec2 period){
    vec2 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float a=fieldCell(ivec2(mod(cell,period))),b=fieldCell(ivec2(mod(cell+vec2(1,0),period)));
    float c=fieldCell(ivec2(mod(cell+vec2(0,1),period))),d=fieldCell(ivec2(mod(cell+vec2(1,1),period)));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
vec2 worldDiskPattern(float radius,float angle){
    float omega=1.2/pow(max(radius,.3),1.5);
    float azimuth=(angle-uTime*uSpeed*omega)/6.28318530718;
    float rho=radius+mod(uTime*uInfall,128.0);
    vec2 p=vec2(azimuth*24.0+rho*1.5,rho*4.0+uSeed),period=vec2(24,64);
    float coarse=fieldNoise(p,period),medium=fieldNoise(p*2.0+vec2(0,7),period*2.0);
    float fine=fieldNoise(p*4.0+vec2(0,19),period*4.0);
    float cloud=.57*coarse+.29*medium+.14*fine;
    return vec2(smoothstep(.22,.78,cloud),smoothstep(.25,.8,fine));
}

vec3 bhDisplay(vec3 linearColor){
 vec3 c=1.0-exp(-max(linearColor,vec3(0))*uExposure);
 return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-.055,step(vec3(.0031308),c));
}
vec3 bhView(vec2 uv){
 vec3 ro=7.8*vec3(sin(.2)*cos(.15),sin(.15),cos(.2)*cos(.15));
 vec3 forward=normalize(-ro),right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
 return bhDisplay(bhCompositeInternal(bhTrace(ro,normalize(forward+(right*uv.x+up*uv.y)*.47))));
}
vec3 haloEmission(vec3 q){
    float r=length(q.xz),y=abs(q.y);
    float envelope=smoothstep(uCore+.12,uInner+.1,r)*(1.0-smoothstep(uOuter,uOuter*1.38,r));
    float ring=exp(-pow((r-uInner-.12)/.11,2.0)-pow(y/.14,2.0));
    float disk=exp(-pow(y/.26,2.0))*exp(-.9*max(0.0,r-uInner));
    float aura=exp(-pow(y/(.46+.06*r),2.0))*exp(-.32*r*r);
    vec3 warm=mix(uCool,uHot,exp(-.75*max(0.0,r-uInner)));
    return HaloStrength*envelope*(uHot*ring*HaloRing*1.6+warm*disk*HaloDisk*.22+uCool*aura*HaloAura*.20);
}
vec3 sceneLinear(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));}
vec3 sceneDisplay(vec3 c){return mix(c*12.92,1.055*pow(max(c,vec3(0)),vec3(1.0/2.4))-.055,step(vec3(.0031308),c));}
void main(){
    vec3 original=texture(Sampler0,texCoord).rgb;
    vec3 rd=normalize(scenePosition(texCoord,1.0));
    vec3 ro=-CenterRelative/WorldScale;
    vec2 range=bhSphere(ro,rd,uOuter*1.5);
    if(range.y<=0.0){fragColor=vec4(original,1);return;}
    float depth=texture(Sampler1,texCoord).r;
    float foreground=depth<.999999?length(scenePosition(texCoord,depth)):1e8;
    if(foreground<max(0.0,range.x)*WorldScale+.02){fragColor=vec4(original,1);return;}
    BHResult hit=bhTrace(ro,rd);
    vec2 lensUv=texCoord;
    vec4 projected;
    if(hit.blocked>.5)projected=ViewProjection*vec4(CenterRelative+hit.position*WorldScale,1);
    else projected=ViewProjection*vec4(hit.direction,0);
    if(projected.w>0.0001){
        vec2 candidate=projected.xy/projected.w*.5+.5;
        vec4 centerClip=ViewProjection*vec4(CenterRelative,1.0);
        vec2 centerUv=centerClip.xy/max(centerClip.w,0.0001)*.5+.5;
        vec2 radial=texCoord-centerUv;
        float radialLen=max(length(radial),.0001);
        vec2 tangent=vec2(-radial.y,radial.x)/radialLen;
        vec2 endpointDelta=candidate-texCoord;
        candidate=texCoord+tangent*dot(endpointDelta,tangent);
        // Fade where the finite ray field terminates and where screen data ends.
        float impact=length(cross(ro,rd));
        float fieldFade=1.0-smoothstep(LensRadius*.62,LensRadius,impact);
        float edge=min(min(candidate.x,candidate.y),min(1-candidate.x,1-candidate.y));
        float validity=smoothstep(.005,.065,edge);
        lensUv=mix(texCoord,candidate,clamp(LensStrength*fieldFade*validity,0,1));
        if(!onScreen(lensUv))lensUv=texCoord;
        float sampleDepth=texture(Sampler1,lensUv).r;
        // Never drag a visible object in front of the effect into its background.
        if(sampleDepth<.999999){
            vec3 source=scenePosition(lensUv,sampleDepth);
            vec2 sourceRange=bhSphere(ro,normalize(source),uOuter*1.5);
            if(sourceRange.y>0.0&&length(source)<max(0.0,sourceRange.x)*WorldScale+.08)lensUv=texCoord;
        }
    }
    vec3 background=texture(Sampler0,lensUv).rgb;
    // Tone map only the emitted field; do not apply exposure to the existing world.
    vec3 emitted=1.0-exp(-max(hit.light,vec3(0))*uExposure);
    fragColor=vec4(sceneDisplay(emitted+hit.transmission*sceneLinear(background)),1.0);
}
