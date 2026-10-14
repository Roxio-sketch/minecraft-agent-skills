#version 150
uniform float uExposure;uniform int uWorld;uniform vec3 uOrigin,uRight,uUp;
in vec2 texCoord;out vec4 fragColor;
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

vec3 bhDisplay(vec3 linearColor){
 vec3 c=1.0-exp(-max(linearColor,vec3(0))*uExposure);
 return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-.055,step(vec3(.0031308),c));
}
vec3 bhView(vec2 uv){
 vec3 ro=7.8*vec3(sin(.2)*cos(.15),sin(.15),cos(.2)*cos(.15));
 vec3 forward=normalize(-ro),right=normalize(cross(forward,vec3(0,1,0))),up=cross(right,forward);
 return bhDisplay(bhCompositeInternal(bhTrace(ro,normalize(forward+(right*uv.x+up*uv.y)*.47))));
}

void main(){
 vec2 uv=texCoord*2-1;
 if(uWorld==0){fragColor=vec4(bhView(uv),1);return;}
 vec3 point=(uRight*uv.x+uUp*uv.y)*(uOuter*1.5);
 BHResult hit=bhTrace(uOrigin,normalize(point-uOrigin));
 float alpha=1.0-hit.transmission;if(alpha<.001)discard;
 // Premultiplied emission/absorption against the existing Minecraft world.
 // It does not sample or bend real-world scene color.
 fragColor=vec4(bhDisplay(hit.light),alpha);
}
