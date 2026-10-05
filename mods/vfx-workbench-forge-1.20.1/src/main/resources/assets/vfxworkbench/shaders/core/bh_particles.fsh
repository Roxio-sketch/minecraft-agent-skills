#version 150
uniform vec3 CenterRelative,CameraOriginRelative,ParticleCold,ParticleHot;
uniform float WorldScale,ParticleBrightness,ParticleOuter;
in vec2 texCoord;in vec3 RayPosition;
flat in vec3 ParticleLocal;
flat in float ParticleLife,ParticleAlpha,ParticleRadius;
out vec4 fragColor;
vec2 worldDiskPattern(float radius,float angle);
bool dustEnd(vec3 p){
    vec3 cameraLocal=(CameraOriginRelative-CenterRelative)/WorldScale;
    return length(p-cameraLocal)>=length(ParticleLocal-cameraLocal);
}
#define BH_SCENE_BLOCKED(p) dustEnd(p)
uniform int uGranular;
uniform float uBandDetail,uDustScale,uDustFill,uFilament,uSpiral;
uniform float uOrbitPhase,uRadialPhase,uClassicRadialPhase,uShearLocal,uShearParity,uDustDriftA,uDustDriftB;
#define BH_SHEAR_CLOCK (uShearLocal+8.0*uShearParity)
#define BH_CLASSIC_RADIAL_PHASE uClassicRadialPhase
vec2 worldDiskPattern(float radius,float angle);
vec2 bhDiskPattern(float radius,float angle);
vec2 ribbonPattern(float radius,float angle);
vec2 lensedDustPattern(float radius,float angle);
vec2 cinematicPattern(float radius,float angle);
float lensedDustVolume(vec3 q);
vec2 previewDiskPattern(float r,float a){
 if(uGranular==3)return lensedDustPattern(r,a);
 if(uGranular==2)return ribbonPattern(r,a);
 if(uGranular==4)return cinematicPattern(r,a);
 return uGranular==1?worldDiskPattern(r,a):bhDiskPattern(r,a);
}
#define BH_PATTERN previewDiskPattern
#define BH_DISK_DENSITY(pattern) (uGranular==4?.12+pattern.x*1.7:(uGranular==3?lensedDustVolume(q)*3.0:(uGranular==2?pattern.x*3.0:(.3+.5*pattern.x+.2*pattern.y))))
#define BH_SURFACE_DISK (uGranular==2||uGranular==4)
#define BH_CINEMATIC_DISK (uGranular==4)
#define BH_DUST_STYLE (uGranular==3)
#define BH_CAPTURED_FOREGROUND_SCALE (uGranular==1?.22:1.0)
#define BH_SURFACE_CRITICAL_MIN_RADIUS (uGranular==2?2.08*uCore:0.0)
// Original artistic black-hole field. Not a relativistic geodesic solver.
// Host supplies a ray in one orthonormal material space, seconds and linear colors.
uniform float uTime, uCore, uInner, uOuter, uThickness, uTilt;
uniform float uBend, uDensity, uSpeed, uInfall, uSeed, uBeaming;
uniform vec3 uHot, uCool, uDiskMid, uCoreColor, uCoreEdgeColor;
uniform float uDiskGradientPosition, uCoreGradientStrength;
uniform int uSteps;

// Optional host hook: test a point against an independently copied scene depth.
// Internal materials leave it disabled. It is a screen-space approximation.
#ifndef BH_SCENE_BLOCKED
#define BH_SCENE_BLOCKED(p) false
#endif
#ifndef BH_DISK_EMISSION
#define BH_DISK_EMISSION(pattern) 1.0
#endif
#ifndef BH_DISK_DENSITY
#define BH_DISK_DENSITY(pattern) (0.3+0.5*pattern.x+0.2*pattern.y)
#endif
#ifndef BH_SURFACE_DISK
#define BH_SURFACE_DISK false
#endif
#ifndef BH_CINEMATIC_DISK
#define BH_CINEMATIC_DISK false
#endif
#ifndef BH_DUST_STYLE
#define BH_DUST_STYLE false
#endif
#ifndef BH_TRACE_BOUND
#define BH_TRACE_BOUND (uOuter*1.5)
#endif
#ifndef BH_CAPTURED_FOREGROUND_SCALE
#define BH_CAPTURED_FOREGROUND_SCALE 1.0
#endif
#ifndef BH_SURFACE_CRITICAL_MIN_RADIUS
#define BH_SURFACE_CRITICAL_MIN_RADIUS 0.0
#endif
#ifndef BH_PATTERN
#define BH_PATTERN bhDiskPattern
#endif
#ifndef BH_VOLUME_SAMPLE
#define BH_VOLUME_SAMPLE(q0,q1,ds,result)
#endif
#ifndef BH_BACKGROUND_FOOTPRINT
#define BH_BACKGROUND_FOOTPRINT 0.0
#endif
#ifndef BH_SHEAR_CLOCK
#define BH_SHEAR_CLOCK (uTime*abs(uSpeed))
#endif
#ifndef BH_CLASSIC_RADIAL_PHASE
#define BH_CLASSIC_RADIAL_PHASE mod(uTime*uInfall,6.28318530718)
#endif
// Optional Schwarzschild-inspired velocity-Verlet integrator.  The preview
// host keeps this disabled unless it injects `#define
// BH_USE_GEODESIC_VERLET 1` before this kernel.  Keeping the default at zero
// preserves the established artistic integrator and its Forge-compatible
// call sites while making the alternative path available for research builds.
#ifndef BH_USE_GEODESIC_VERLET
#define BH_USE_GEODESIC_VERLET 0
#endif
#ifndef BH_VERLET_LENSING
#define BH_VERLET_LENSING clamp(.72*uBend,0.0,1.35)
#endif
#ifndef BH_VERLET_H_CONSERVE
#define BH_VERLET_H_CONSERVE 0.78
#endif
struct BHResult { vec3 light; float transmission; vec3 direction; vec3 position; float captured; float exhausted; float blocked; float closestRadius; };

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
vec3 bhStars(vec3 direction,float footprint);
vec3 bhBackground(vec3 direction) {
    // Smooth low-frequency field on the sphere, without longitude seams.
    vec3 d=normalize(direction);
    float band=exp(-pow((d.y+0.24*d.x)/0.17,2.0));
    float textureField=0.5+0.5*sin(18.0*d.x+sin(13.0*d.z)+uSeed);
    float footprint=BH_BACKGROUND_FOOTPRINT;
    float resolved=exp(-footprint*footprint*200.0);
    // A broad, low-frequency veil keeps the star field from ending at a hard
    // circular boundary.  The direction-only bands are intentionally cheap;
    // the high-frequency detail below is footprint filtered at critical rays.
    float veil=exp(-pow((d.y+.18*d.x)/.31,2.0));
    float filament=.5+.5*sin(4.0*d.x-5.0*d.z+2.0*sin(7.0*d.y+uSeed*.13));
    vec3 col=vec3(0.0006,0.0012,0.003)
        +mix(.1,band*textureField,resolved)*vec3(0.008,0.012,0.025)
        +veil*(.004+.009*filament)*vec3(.15,.28,.58);
    return col+bhStars(d,footprint);
}
// Shared star layers for the broad nebula mode.  Keeping this separate from
// bhBackground lets the UI combine stars with a full-frustum nebula without
// duplicating a screen-space texture or sampling unresolved cells at the
// critical curve.
vec3 bhStars(vec3 direction,float footprint) {
    vec3 d=normalize(direction),col=vec3(0.0);
    vec3 p=d*38.0,cell=floor(p-.5);
    float resolved=exp(-footprint*footprint*38.0*38.0/12.0);
    for(int z=0;z<=1;z++) for(int y=0;y<=1;y++) for(int x=0;x<=1;x++) {
        vec3 c=cell+vec3(float(x),float(y),float(z));
        float gate=bhHash(c+101.0);
        if(gate<.035){
            vec3 center=c+vec3(bhHash(c+11.0),bhHash(c+37.0),bhHash(c+71.0));
            float variance=.035+min(1.0,footprint*footprint*1444.0/12.0);
            col+=exp(-dot(p-center,p-center)/variance)*(.035/variance)*resolved
                *mix(vec3(.35,.6,1.0),vec3(1.0,.65,.3),bhHash(c+97.0))*1.5;
        }
    }
    vec3 fineP=d*112.0,fineCell=floor(fineP-.5);
    float fineResolved=exp(-footprint*footprint*112.0*112.0/18.0);
    for(int z=0;z<=1;z++) for(int y=0;y<=1;y++) for(int x=0;x<=1;x++) {
        vec3 c=fineCell+vec3(float(x),float(y),float(z));
        if(bhHash(c+193.0)<.022){
            vec3 center=c+vec3(bhHash(c+211.0),bhHash(c+233.0),bhHash(c+257.0));
            float variance=.018+min(1.0,footprint*footprint*12544.0/10.0);
            col+=exp(-dot(fineP-center,fineP-center)/variance)*(.012/variance)*fineResolved
                *mix(vec3(.42,.68,1.0),vec3(1.0,.76,.42),bhHash(c+271.0));
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
    // Schwarzschild-inspired ray bending.  The conserved angular momentum
    // keeps the bend tangent to the ray plane, unlike the previous radial
    // pull which could create a visible kink at the photon ring.  uBend is
    // retained as an artistic multiplier used by the existing recipes.
    float r=max(length(p),0.0001),r2=max(dot(p,p),.01);
    vec3 h=cross(p,direction);
    float h2=dot(h,h);
    // The preview's radius is an artistic scene unit, not a Schwarzschild
    // radius.  A reduced coefficient keeps the zoom control usable at every
    // supported camera distance while retaining the angular-momentum shape.
    vec3 geodesic=(-.4*uBend*h2/max(r2*r2*r,.02))*p;
    // A small residual term preserves the established look at large radii,
    // where the relativistic term is correctly almost imperceptible.
    vec3 inward=-p/r;
    vec3 artistic=(inward-direction*dot(inward,direction))
        *(.12*uBend/max(r2,.08));
    return geodesic+artistic;
}
vec2 bhClassicField(float radius,float phase,float seed) {
    // Integer angular harmonics keep the material periodic at atan's branch cut.
    // Positive radial phase transport moves a fixed feature toward smaller radius:
    // radius(t) = radius(0) - uInfall*t when angular motion is disabled.
    // Both radial frequencies are integers, so wrapping by 2*pi is continuous.
    float advectedRadius=radius+BH_CLASSIC_RADIAL_PHASE;
    float ribbon=0.5+0.5*sin(7.0*phase+10.0*advectedRadius+seed);
    float detail=0.5+0.5*sin(19.0*phase-17.0*advectedRadius+0.7*sin(3.0*phase));
    return vec2(ribbon,detail);
}
vec2 bhDiskPattern(float radius,float angle) {
    // A pair of eight-radian material lifetimes keeps differential motion while
    // smoothly replacing an over-sheared field. The parity swap makes the two
    // sides of each renewal boundary exactly the same material.
    float cycle=8.0,clock=BH_SHEAR_CLOCK,local=mod(clock,cycle);
    float parity=mod(floor(clock/cycle),2.0),omega=1.0/(.45+radius);
    vec2 older=bhClassicField(radius,angle-local*omega,uSeed+37.0*parity);
    vec2 younger=bhClassicField(radius,angle-(local-cycle)*omega,uSeed+37.0*(1.0-parity));
    return mix(older,younger,smoothstep(0.0,1.0,local/cycle));
}
vec3 bhDiskColor(float heat) {
    // Zero preserves the two-endpoint palette of earlier preview recipes.
    if(uDiskGradientPosition<=0.001)return mix(uCool,uHot,heat);
    float knot=clamp(uDiskGradientPosition,0.001,0.999);
    if(heat<knot)return mix(uCool,uDiskMid,smoothstep(0.0,knot,heat));
    return mix(uDiskMid,uHot,smoothstep(knot,1.0,heat));
}
#ifndef BH_TEMPERATURE_CLOCK
#define BH_TEMPERATURE_CLOCK mod(uTime,2048.0)
#endif
#ifndef BH_HOTSPOT_GAIN
#define BH_HOTSPOT_GAIN (BH_CINEMATIC_DISK ? .34 : 0.0)
#endif
// A bounded, palette-preserving temperature tint. It adds a restrained
// inner-hot/outer-cool response without drawing a screen-space ring.
vec3 bhTemperatureTint(float heat01) {
    float x=clamp(heat01,0.0,1.0);
    vec3 warm=vec3(1.18,.24,.035);
    vec3 amber=vec3(1.10,.58,.16);
    vec3 pale=vec3(1.02,.90,.68);
    vec3 whiteBlue=vec3(.70,.86,1.0);
    vec3 c=mix(warm,amber,smoothstep(0.0,.28,x));
    c=mix(c,pale,smoothstep(.20,.68,x));
    return mix(c,whiteBlue,smoothstep(.62,1.0,x));
}
float bhTemperatureHeat(float radius,float hot,float patternY,float spots) {
    float span=max(uOuter-uInner,.01);
    float radial01=clamp((radius-uInner)/span,0.0,1.0);
    float orbitRatio=max(radius/max(uInner,.02),1.0);
    float boundary=pow(max(1.0-.88*inversesqrt(orbitRatio),.04),.24);
    float decay=pow(inversesqrt(orbitRatio),.72);
    float taper=1.0-smoothstep(.56,1.0,radial01);
    float profile=clamp(boundary*decay*taper,0.0,1.0);
    return clamp(.58*hot+.30*profile+.08*patternY+.18*spots,0.0,1.0);
}
// Four finite Gaussian patches provide moving heat structure. The angular
// distance uses sin/cos, so atan's branch cut cannot create a seam.
float bhHotspotField(float radius,float angle) {
    if(!BH_CINEMATIC_DISK)return 0.0;
    float clock=BH_TEMPERATURE_CLOCK;
    float rate=max(abs(uSpeed),.02);
    // Wrap the finite-lived constellation with a short crossfade.  A plain
    // mod(clock,2048) would snap every hot spot at the renewal boundary.
    float renewal=smoothstep(2032.0,2048.0,clock);
    float span=max(uOuter-uInner,.8);
    float sum=0.0;
    for(int k=0;k<4;k++){
        float fk=float(k);
        float rs=min(uInner+.62+span*(fk+.35)/4.35,uOuter-.48);
        float omega=.24/(.82+rs*sqrt(max(rs,.08)));
        float center=mod(fk*2.39996-omega*clock*rate+40.0*6.28318530718,6.28318530718);
        float dphi=atan(sin(angle-center),cos(angle-center));
        float rw=.13+.045*rs;
        float aw=.16+.035*rs;
        float dr=(radius-rs)/max(rw,.05);
        float da=dphi*radius/max(aw,.06);
        float pulse=.66+.34*sin(clock*rate*(.15+.025*fk)+fk*1.91);
        float spot=exp(-1.35*(dr*dr+da*da))*pulse;
        if(renewal>0.0){
            float nextClock=clock-2048.0;
            float nextCenter=mod(fk*2.39996-omega*nextClock*rate
                +40.0*6.28318530718,6.28318530718);
            float nextAngle=atan(sin(angle-nextCenter),cos(angle-nextCenter));
            float nextDa=nextAngle*radius/max(aw,.06);
            float nextPulse=.66+.34*sin(nextClock*rate*(.15+.025*fk)+fk*1.91);
            float nextSpot=exp(-1.35*(dr*dr+nextDa*nextDa))*nextPulse;
            spot=mix(spot,nextSpot,renewal);
        }
        sum+=spot;
    }
    return clamp(sum*.52,0.0,1.0);
}
vec3 bhCoreEmission(vec3 surfacePoint,vec3 rayDirection) {
    // The incidence on the captured sphere varies continuously from the face
    // to the silhouette. This keeps the gradient attached to the bent ray,
    // rather than painting a screen-space circle across foreground light.
    float facing=abs(dot(normalize(surfacePoint),-rayDirection));
    float edge=1.0-smoothstep(0.08,0.9,facing);
    return mix(uCoreColor,uCoreEdgeColor,edge*uCoreGradientStrength);
}
void bhDiskSample(vec3 q,vec3 rayDirection,float ds,inout BHResult result) {
    float radius=length(q.xz);
    if(radius<=uInner || radius>=uOuter)return;
    float radial=smoothstep(uInner,uInner+0.22,radius)*(1.0-smoothstep(uOuter-0.35,uOuter,radius));
    if(BH_SURFACE_DISK && !BH_CINEMATIC_DISK){
        float outFade=1.0-smoothstep(uInner,uOuter,radius);
        radial=(1.0-pow(outFade,100.0))*outFade*2.0;
    }
    if(BH_CINEMATIC_DISK){
        // Keep the event-horizon silhouette clean.  A short near-inner
        // exclusion removes the sub-pixel filament fringe that can resolve as
        // a rough wire ring under strong lensing or low step counts; the disk
        // resumes smoothly farther out where its structure is readable.
        float outerWidth=clamp(.072*max(uOuter-uInner,.8),.24,.42);
        radial=smoothstep(uInner+.34,uInner+.68,radius)*(1.0-smoothstep(uOuter-outerWidth,uOuter,radius));
    }
    if(BH_DUST_STYLE){
        // The granular reference is a broad dusty volume.  Its inner edge is
        // intentionally kept outside the photon orbit: unresolved multiple
        // crossings otherwise collapse into a stack of bright wire rings at
        // the preview pixel scale.  A wider Gaussian preserves the depth of
        // the cloud without turning it into a flat texture sheet.
        radial=smoothstep(uInner+1.12,uInner+1.72,radius)
            *(1.0-smoothstep(uOuter-.8,uOuter,radius));
    }
    float dustThickness=max(uThickness*(BH_DUST_STYLE?1.65:1.0),.012);
    float vertical=exp(-0.5*q.y*q.y/(dustThickness*dustThickness));
    vec2 pattern=BH_PATTERN(radius,atan(q.z,q.x));
    float sigma=uDensity*radial*vertical*BH_DISK_DENSITY(pattern);
    if(BH_DUST_STYLE)sigma*=.25;
    if(BH_SURFACE_DISK && !BH_CINEMATIC_DISK)sigma*=.43;
    // The cinematic layer is a finite plasma volume, not a uniformly painted
    // sheet. Couple optical thickness to both the lane and filament fields so
    // dark dust lanes remain visible after HDR compositing and bloom.
    if(BH_CINEMATIC_DISK){
        float laneContrast=smoothstep(.08,.72,clamp(pattern.x,0.0,1.0));
        float filamentContrast=smoothstep(.15,.9,clamp(pattern.y,0.0,1.4));
        sigma*=mix(.28,1.08,laneContrast)*(.76+.32*filamentContrast);
    }
    float opacity=1.0-exp(-sigma*ds);
    float hot=pow(1.0-clamp((radius-uInner)/(uOuter-uInner),0.0,1.0),BH_DUST_STYLE?1.25:.65);
    if(BH_DUST_STYLE)hot*=.73;
    // Relativistic-looking colour weighting: the approaching side is slightly
    // boosted while the far side is dimmed by a gravitational redshift.  It
    // is deliberately bounded so it remains an artistic material control and
    // cannot blow out the HDR buffer at grazing angles.
    vec3 tangent=normalize(vec3(-q.z,0.0,q.x));
    float approach=dot(-rayDirection,tangent);
    float beta=clamp(.72*sqrt(.5/max(radius-uCore,.35)),0.0,.82);
    float gamma=inversesqrt(max(1.0-beta*beta,.12));
    float doppler=1.0/max(gamma*(1.0-beta*approach),.22);
    float redshift=sqrt(max(.12,1.0-uCore/max(radius,.001)));
    float relativistic=clamp(doppler*redshift,.35,1.85);
    float sourceStrength=BH_DUST_STYLE?.38+1.45*hot:
        (BH_SURFACE_DISK?.38+1.45*hot:1.2+3.6*hot);
    vec3 source=bhDiskColor(hot)*sourceStrength*BH_DISK_EMISSION(pattern);
    source*=mix(.84,relativistic,BH_CINEMATIC_DISK?.86:.38);
    if(BH_CINEMATIC_DISK){
        float beam=pow(clamp(1.0+.7*uBeaming*approach,.45,1.7),2.0);
        float heated=clamp(.68*hot+.32*max(approach,0.0)*uBeaming,0.0,1.0);
        float thread=clamp(.42+.58*pattern.y+.35*pattern.x,.22,1.3);
        source=bhDiskColor(heated)*(.32+1.28*hot)*BH_DISK_EMISSION(pattern)*thread*beam
            *mix(.84,relativistic,.86);
        float hotspots=bhHotspotField(radius,atan(q.z,q.x));
        float tempHeat=bhTemperatureHeat(radius,hot,pattern.y,hotspots);
        source*=mix(vec3(1.0),bhTemperatureTint(tempHeat),.28)
            *(1.0+BH_HOTSPOT_GAIN*hotspots);
    }
    result.light+=result.transmission*opacity*source;
    result.transmission*=1.0-opacity;
}
BHResult bhTraceClassic(vec3 ro,vec3 rd) {
    BHResult result;
    result.light=vec3(0.0); result.transmission=1.0;
    result.direction=normalize(rd); result.position=ro; result.captured=0.0; result.exhausted=0.0; result.blocked=0.0; result.closestRadius=1e6;
    float bound=BH_TRACE_BOUND;
    vec2 hit=bhSphere(ro,result.direction,bound);
    if(hit.y<=0.0) return result;
    vec3 p=ro+result.direction*max(0.0,hit.x);
    result.position=p;
    // Segment length changes with quality; optical thickness follows the segment length.
    float ds=3.0*bound/float(uSteps);
    float a=radians(-uTilt);
    bool ended=false;
    // A lensed thin sheet can be crossed repeatedly while a ray skims the
    // photon orbit.  Keeping the first few physical crossings preserves the
    // upper/lower images while preventing sub-pixel re-crossings from drawing
    // a stack of concentric wire rings.
    int diskCrossings=0;
    // A critical ray can skim the sheet for many integration steps without
    // crossing its centre. Bound that auxiliary volume estimate separately;
    // otherwise repeated grazing samples accumulate into a hard bright ring.
    int grazingSegments=0;
    float closestRadius=1e6;
    // Allow rays close to the critical orbit to finish additional turns without
    // coarsening their integration step. Most rays still leave much earlier.
    for(int i=0;i<512;i++) {
        if(i>=uSteps*2) break;
        if(BH_SCENE_BLOCKED(p)) { result.blocked=1.0; ended=true; break; }
        float r=max(length(p),0.0001);
        closestRadius=min(closestRadius,r);
        if(r<uCore) {
            // Core source is behind the accumulated foreground disk, in linear RGB.
            // High-order granular volume images collapse onto the capture
            // silhouette at preview resolution; the host style hook reduces
            // that foreground contribution so the horizon stays clean.
            result.light*=BH_CAPTURED_FOREGROUND_SCALE;
            result.light+=result.transmission*bhCoreEmission(p,result.direction);
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        // Adaptive integration resolves the rapidly changing field close to
        // the photon orbit while retaining long steps in the far field.
        float stepScale=clamp(.22+.18*r,.24,1.0);
        float localDs=ds*stepScale;
        // Midpoint integration reduces quality-dependent deflection of the ray itself.
        vec3 midDir=normalize(result.direction+bhTurn(p,result.direction)*(0.5*localDs));
        vec3 midPos=p+result.direction*(0.5*localDs);
        vec3 nextDir=normalize(result.direction+bhTurn(midPos,midDir)*localDs);
        // Segment/core test prevents a large integration step skipping the dark core.
        float along=clamp(-dot(p,midDir),0.0,localDs);
        if(length(p+midDir*along)<uCore) {
            vec2 coreCrossing=bhSphere(p,midDir,uCore);
            vec3 corePoint=p+midDir*clamp(coreCrossing.x,0.0,localDs);
            result.light*=BH_CAPTURED_FOREGROUND_SCALE;
            result.light+=result.transmission*bhCoreEmission(corePoint,midDir);
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        vec3 q0=bhRotateX(p,a),q1=bhRotateX(p+midDir*localDs,a);
        // Simpson integration keeps a thin circumferential halo continuous
        // when a bent ray crosses its radial peak between two march steps.
        // The three analytic samples remove the dotted inner edge without a
        // screen-space mask or a large adaptive loop.
        BH_VOLUME_SAMPLE(q0,q1,localDs,result);
        // Integrate the thin layer, instead of taking one sample that can miss it.
        // Bound is <= 8 samples for current parameter ranges (16 allows headroom).
        float slab=6.0*uThickness;
        if(BH_SURFACE_DISK){
            if(BH_CINEMATIC_DISK){
                // Cinematic bands use a small finite-depth quadrature. A single
                // plane crossing reads as a flat texture and makes the lensed
                // disk unnaturally smooth; these bounded samples expose volume
                // structure without reintroducing long-time aliasing.
                // Three Gaussian scale heights retain essentially all emission
                // while keeping the finite-depth pass bounded near the sheet.
                float volumeSlab=3.0*uThickness;
                if(min(q0.y,q1.y)<volumeSlab&&max(q0.y,q1.y)>-volumeSlab){
                    float dy=q1.y-q0.y;
                    if(q0.y*q1.y<=0.0&&abs(dy)>.000001&&diskCrossings<1){
                        // Centre five taps on the analytic plane crossing. The
                        // offsets cover the Gaussian thickness and close the
                        // subpixel gaps that become a dotted critical ring.
                        float crossing=clamp(-q0.y/dy,0.0,1.0);
                        float span=clamp(.75*uThickness/max(abs(dy),.0001),.06,.22);
                        for(int j=0;j<5;j++){
                            float t=clamp(crossing+(float(j)-2.0)*span,0.0,1.0);
                            bhDiskSample(mix(q0,q1,t),bhRotateX(midDir,a),localDs*.2,result);
                        }
                        diskCrossings++;
                    }else if(diskCrossings<1&&grazingSegments<1){
                        int samples=int(clamp(ceil(localDs/max(.75*uThickness,.002)),2.0,4.0));
                        float subDs=localDs/float(samples);
                        for(int j=0;j<4;j++){
                            if(j>=samples)break;
                            float t=(float(j)+.5)/float(samples);
                            bhDiskSample(mix(q0,q1,t),bhRotateX(midDir,a),subDs,result);
                        }
                        grazingSegments++;
                    }
                }
            }else{
                // A thin emitting sheet retains radial detail at grazing angles.
                if(q0.y*q1.y<=0.0&&abs(q1.y-q0.y)>.000001&&diskCrossings<1){
                    float crossing=clamp(-q0.y/(q1.y-q0.y),0.0,1.0);
                    vec3 diskPoint=mix(q0,q1,crossing);
                    float diskRadius=length(diskPoint.xz);
                    // A plane crossing outside the annulus is not a disk hit.
                    // Spending the single physical-hit budget there can erase
                    // a later lensed image that actually intersects the disk.
                    if(diskRadius>uInner&&diskRadius<uOuter&&closestRadius>BH_SURFACE_CRITICAL_MIN_RADIUS){
                        float incidence=abs(q1.y-q0.y)/max(localDs,.000001);
                        vec3 beforeLight=result.light;
                        float beforeTransmission=result.transmission;
                        bhDiskSample(diskPoint,bhRotateX(midDir,a),min(1.0,uThickness*2.0/max(.15,incidence)),result);
                        float visibility=smoothstep(BH_SURFACE_CRITICAL_MIN_RADIUS,
                            BH_SURFACE_CRITICAL_MIN_RADIUS+.30*uCore,closestRadius);
                        result.light=mix(beforeLight,result.light,visibility);
                        result.transmission=mix(beforeTransmission,result.transmission,visibility);
                        diskCrossings++;
                    }
                }else if(abs(q0.y)<2.0*uThickness&&abs(q1.y)<2.0*uThickness&&diskCrossings<1&&grazingSegments<1){
                    // Strictly coplanar viewing still sees a finite physical slab.
                    vec3 grazingPoint=.5*(q0+q1);
                    float grazingRadius=length(grazingPoint.xz);
                    if(grazingRadius>uInner&&grazingRadius<uOuter){
                        bhDiskSample(grazingPoint,bhRotateX(midDir,a),min(localDs,1.5*uThickness),result);
                        grazingSegments++;
                    }
                }
            }
        }else if(min(q0.y,q1.y)<slab && max(q0.y,q1.y)>-slab) {
            int samples=int(clamp(ceil(localDs/(0.5*uThickness)),1.0,16.0));
            float subDs=localDs/float(samples);
            for(int j=0;j<16;j++) {
                if(j>=samples)break;
                bhDiskSample(mix(q0,q1,(float(j)+0.5)/float(samples)),bhRotateX(midDir,a),subDs,result);
            }
        }
        p+=midDir*localDs; result.direction=nextDir; result.position=p;
        if(length(p)>bound+0.001 && dot(p,nextDir)>0.0) { ended=true; break; }
        if(result.transmission<0.002) { result.transmission=0.0; ended=true; break; }
    }
    // Diagnostic flag: finite path budget is an approximation, never physical capture.
    if(!ended) result.exhausted=1.0;
    // A grazing ray that misses the core can still pile several foreground
    // samples onto the critical silhouette. Apply the same host style hook to
    // that narrow annulus; it removes a rough wire edge without painting over
    // the black horizon or the readable outer disk.
    if(result.captured<0.5 && closestRadius<uCore*1.22)
        result.light*=BH_CAPTURED_FOREGROUND_SCALE;
    result.closestRadius=closestRadius;
    return result;
}
// Schwarzschild-inspired null-ray prototype.  This follows the reference
// study's radial 1/r^4 acceleration, but keeps the material's finite capture
// sphere, volumetric hooks, and anti-ring crossing budget.  A velocity-Verlet
// step gives a symmetric position/velocity update; the small angular-momentum
// correction only counters drift caused by the variable step length and is
// blended by BH_VERLET_H_CONSERVE rather than forcing an energy projection.
BHResult bhTraceVerlet(vec3 ro,vec3 rd) {
    BHResult result;
    result.light=vec3(0.0); result.transmission=1.0;
    result.direction=normalize(rd); result.position=ro; result.captured=0.0; result.exhausted=0.0; result.blocked=0.0; result.closestRadius=1e6;
    float bound=BH_TRACE_BOUND;
    vec2 hit=bhSphere(ro,result.direction,bound);
    if(hit.y<=0.0) return result;
    vec3 p=ro+result.direction*max(0.0,hit.x);
    result.position=p;
    vec3 vel=normalize(result.direction);
    // Radial acceleration leaves h = p x v invariant.  Computing it at the
    // entry point is equivalent to computing it at the camera for a straight
    // incoming ray, because the two positions differ by a multiple of v.
    vec3 h0=cross(p,vel);
    float h02=dot(h0,h0);
    float lens=BH_VERLET_LENSING;
    float K=1.5*h02*lens;
    float dsBase=3.0*bound/float(max(uSteps,1));
    float a=radians(-uTilt);
    int diskCrossings=0;
    int grazingSegments=0;
    float closestRadius=1e6;
    bool ended=false;
    for(int i=0;i<512;i++) {
        if(i>=uSteps*2) break;
        if(BH_SCENE_BLOCKED(p)) { result.blocked=1.0; ended=true; break; }
        float r=max(length(p),.0001);
        closestRadius=min(closestRadius,r);
        if(r<uCore) {
            result.light*=BH_CAPTURED_FOREGROUND_SCALE;
            result.light+=result.transmission*bhCoreEmission(p,result.direction);
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        float radialSpeed=abs(dot(normalize(p),normalize(vel)));
        float dt=dsBase*clamp(.12+.13*r,.08,1.0);
        // Do not let a low-quality step tunnel across the event sphere.  The
        // cap is only active for an incoming ray and has no effect at large r.
        float safeDt=.46*max(r-uCore,.012)/max(radialSpeed,.22);
        dt=min(dt,max(.001,safeDt));
        float r2=max(dot(p,p),.01);
        vec3 acc=-K*p/(r2*r2*r);
        // Velocity-Verlet: half kick, drift, second half kick.
        vec3 halfVel=vel+.5*acc*dt;
        vec3 nextP=p+halfVel*dt;
        float nextR2=max(dot(nextP,nextP),.01);
        float nextR=sqrt(nextR2);
        vec3 nextAcc=-K*nextP/(nextR2*nextR2*nextR);
        vec3 nextVel=halfVel+.5*nextAcc*dt;
        // Variable dt introduces a small h drift. Preserve the radial speed
        // while gently restoring the tangential component h x p / r^2.
        vec3 hNow=cross(nextP,nextVel);
        float hDrift=length(hNow-h0)/max(length(h0),.0001);
        vec3 nextRadial=nextP/max(nextR,.0001);
        vec3 corrected=nextRadial*dot(nextVel,nextRadial)+cross(h0,nextP)/nextR2;
        float hMix=clamp(hDrift*BH_VERLET_H_CONSERVE*16.0,0.0,1.0);
        nextVel=mix(nextVel,corrected,hMix);
        vec3 segment=nextP-p;
        float segLen=length(segment);
        vec3 segDir=segLen>.00001?segment/segLen:normalize(vel);
        // Segment/core test prevents a large outer step from skipping capture.
        float along=clamp(-dot(p,segDir),0.0,segLen);
        if(length(p+segDir*along)<uCore) {
            vec2 coreCrossing=bhSphere(p,segDir,uCore);
            vec3 corePoint=p+segDir*clamp(coreCrossing.x,0.0,segLen);
            result.light*=BH_CAPTURED_FOREGROUND_SCALE;
            result.light+=result.transmission*bhCoreEmission(corePoint,normalize(vel));
            result.captured=1.0; result.transmission=0.0; ended=true; break;
        }
        vec3 q0=bhRotateX(p,a),q1=bhRotateX(nextP,a);
        // The critical geodesic can revisit the same halo volume for many
        // turns. Smoothly attenuate unresolved near-horizon samples instead
        // of letting them collapse into a bright wire ring. The disk crossing
        // below still retains the readable photon-side emission.
        float volumeGate=smoothstep(uCore*1.10,uCore*1.80,min(length(q0),length(q1)));
        vec3 volumeBefore=result.light;
        BH_VOLUME_SAMPLE(q0,q1,max(segLen,.00001),result);
        result.light=mix(volumeBefore,result.light,volumeGate);
        float slab=6.0*uThickness;
        if(BH_SURFACE_DISK){
            if(BH_CINEMATIC_DISK){
                float volumeSlab=3.0*uThickness;
                if(min(q0.y,q1.y)<volumeSlab&&max(q0.y,q1.y)>-volumeSlab){
                    float dy=q1.y-q0.y;
                    if(q0.y*q1.y<=0.0&&abs(dy)>.000001&&diskCrossings<1){
                        float crossing=clamp(-q0.y/dy,0.0,1.0);
                        float span=clamp(.75*uThickness/max(abs(dy),.0001),.06,.22);
                        for(int j=0;j<5;j++){
                            float t=clamp(crossing+(float(j)-2.0)*span,0.0,1.0);
                            bhDiskSample(mix(q0,q1,t),bhRotateX(normalize(nextVel),a),max(segLen,.00001)*.2,result);
                        }
                        diskCrossings++;
                    }else if(diskCrossings<1&&grazingSegments<1){
                        int samples=int(clamp(ceil(max(segLen,.00001)/max(.75*uThickness,.002)),2.0,4.0));
                        float subDs=max(segLen,.00001)/float(samples);
                        for(int j=0;j<4;j++){
                            if(j>=samples)break;
                            float t=(float(j)+.5)/float(samples);
                            bhDiskSample(mix(q0,q1,t),bhRotateX(normalize(nextVel),a),subDs,result);
                        }
                        grazingSegments++;
                    }
                }
            }else{
                if(q0.y*q1.y<=0.0&&abs(q1.y-q0.y)>.000001&&diskCrossings<1){
                    float crossing=clamp(-q0.y/(q1.y-q0.y),0.0,1.0);
                    vec3 diskPoint=mix(q0,q1,crossing);
                    float diskRadius=length(diskPoint.xz);
                    if(diskRadius>uInner&&diskRadius<uOuter&&closestRadius>BH_SURFACE_CRITICAL_MIN_RADIUS){
                        float incidence=abs(q1.y-q0.y)/max(segLen,.000001);
                        vec3 beforeLight=result.light;
                        float beforeTransmission=result.transmission;
                        bhDiskSample(diskPoint,bhRotateX(normalize(nextVel),a),min(1.0,uThickness*2.0/max(.15,incidence)),result);
                        float visibility=smoothstep(BH_SURFACE_CRITICAL_MIN_RADIUS,
                            BH_SURFACE_CRITICAL_MIN_RADIUS+.30*uCore,closestRadius);
                        result.light=mix(beforeLight,result.light,visibility);
                        result.transmission=mix(beforeTransmission,result.transmission,visibility);
                        diskCrossings++;
                    }
                }else if(abs(q0.y)<2.0*uThickness&&abs(q1.y)<2.0*uThickness&&diskCrossings<1&&grazingSegments<1){
                    vec3 grazingPoint=.5*(q0+q1);
                    float grazingRadius=length(grazingPoint.xz);
                    if(grazingRadius>uInner&&grazingRadius<uOuter){
                        bhDiskSample(grazingPoint,bhRotateX(normalize(nextVel),a),min(segLen,1.5*uThickness),result);
                        grazingSegments++;
                    }
                }
            }
        }else if(min(q0.y,q1.y)<slab && max(q0.y,q1.y)>-slab) {
            int samples=int(clamp(ceil(max(segLen,.00001)/max(.5*uThickness,.002)),1.0,16.0));
            float subDs=max(segLen,.00001)/float(samples);
            for(int j=0;j<16;j++){
                if(j>=samples)break;
                bhDiskSample(mix(q0,q1,(float(j)+.5)/float(samples)),bhRotateX(normalize(nextVel),a),subDs,result);
            }
        }
        p=nextP; vel=nextVel; result.direction=normalize(vel); result.position=p;
        if(length(p)>bound+.001&&dot(p,vel)>0.0){ ended=true; break; }
        if(result.transmission<.002){ result.transmission=0.0; ended=true; break; }
    }
    if(!ended) result.exhausted=1.0;
    if(result.captured<.5&&closestRadius<uCore*1.22)
        result.light*=BH_CAPTURED_FOREGROUND_SCALE;
    result.closestRadius=closestRadius;
    return result;
}
BHResult bhTrace(vec3 ro,vec3 rd) {
#if BH_USE_GEODESIC_VERLET
    return bhTraceVerlet(ro,rd);
#else
    return bhTraceClassic(ro,rd);
#endif
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
vec2 worldDiskField(float radius,float angle,float seed){
    float azimuth=angle/6.28318530718;
    float rho=radius+uRadialPhase;
    vec2 p=vec2(azimuth*24.0+rho*1.5,rho*4.0+seed),period=vec2(24,64);
    float coarse=fieldNoise(p,period),medium=fieldNoise(p*2.0+vec2(0,7),period*2.0);
    float fine=fieldNoise(p*4.0+vec2(0,19),period*4.0);
    float cloud=.57*coarse+.29*medium+.14*fine;
    return vec2(smoothstep(.22,.78,cloud),smoothstep(.25,.8,fine));
}
vec2 worldDiskPattern(float radius,float angle){
    float cycle=8.0,clock=BH_SHEAR_CLOCK,local=mod(clock,cycle);
    float parity=mod(floor(clock/cycle),2.0),omega=1.2/pow(max(radius,.3),1.5);
    vec2 older=worldDiskField(radius,angle-local*omega,uSeed+37.0*parity);
    vec2 younger=worldDiskField(radius,angle-(local-cycle)*omega,uSeed+37.0*(1.0-parity));
    return mix(older,younger,smoothstep(0.0,1.0,local/cycle));
}
// Original lensed emission fields. Every feature is sampled along the bent ray,
// including the upper/lower disk images; no screen-space sprite overlay.
float volumeNoise(vec3 p,vec3 period);
vec2 cinematicPattern(float radius,float angle){
    // All features share a seamless cylindrical coordinate and bounded orbital
    // phase. The three frequency bands describe plasma, filaments and sparks.
    float phase=angle-uOrbitPhase;
    float radial=(radius-uInner)/max(.01,uOuter-uInner);
    float flow=mod(uRadialPhase,64.0)/max(.01,uOuter-uInner);
    float rho=radial+flow;
    vec2 orbit=vec2(cos(phase),sin(phase));
    float cloud=volumeNoise(vec3(rho*3.7,orbit*2.6)+vec3(uSeed*.13,11.0,7.0),vec3(128.0));
    float structure=volumeNoise(vec3(rho*8.2,orbit*5.1)+vec3(29.0,uSeed*.17,4.0),vec3(128.0));
    float speck=volumeNoise(vec3(rho*23.0,orbit*12.0)+vec3(17.0,41.0,uSeed*.21),vec3(128.0));
    // A second, finer field breaks the broad lanes into finite plasma wisps.
    // It is deliberately bounded and cylindrical so it cannot accumulate the
    // long-time radial shear that produced the old moiré rings.
    float micro=volumeNoise(vec3(rho*41.0,orbit*22.0)+vec3(53.0,uSeed*.31,19.0),vec3(128.0));
    // A few wide plasma lanes carry the image. Fine filaments sit inside them;
    // they must not turn the entire lensed disk into evenly spaced wire lines.
    float radialPhase=rho*uBandDetail*.62;
    float warped=radialPhase+.065*sin(radialPhase*2.1+2.0*phase)
        +.13*uFilament*(cloud-.5)+.045*sin(3.0*phase+2.0*structure)
        +.045*(micro-.5)*uFilament;
    // Continuous lanes and fine threads avoid hard, isolated emission blocks.
    // The angular terms are periodic, including at atan's branch cut.
    float lane=.5+.5*cos(6.28318530718*warped);
    float broad=smoothstep(.16,.80,lane);
    float ridge=pow(lane,3.0);
    float hair=pow(.5+.5*sin(6.28318530718*(warped*2.12+.16*structure)),9.0);
    float spiral=.5+.5*sin(2.0*phase-3.0*uSpiral*log(max(radius/uInner,1.0))+2.0*(cloud-.5));
    float arm=mix(1.0,.48+.52*spiral,uSpiral);
    float knots=smoothstep(.58,.82,speck)*smoothstep(.48,.72,structure);
    float microKnots=smoothstep(.62,.84,micro)*smoothstep(.57,.78,speck);
    // Dark dust lanes are part of the emission field, instead of an opaque
    // screen-space overlay. They provide depth cues when the sheet is lensed.
    float dustLane=1.0-.72*smoothstep(.48,.75,cloud);
    float filamentMask=.34+.66*smoothstep(.28,.74,micro);
    float plasma=(.075+.34*ridge+.22*uFilament*hair*filamentMask
        +.11*knots+.07*microKnots)
        *(.42+.58*broad)*(.7+.6*structure)*dustLane*arm;
    return vec2(max(0.0,plasma),clamp(.18+.37*broad+.22*ridge+.15*knots+.10*microKnots,0.0,1.0));
}
vec2 ribbonPattern(float radius,float angle){
    // Uniform angular advection follows the Unity reference. A radius-dependent
    // time phase produces ever finer shear and aliases after minutes of playback.
    float phase=angle-uOrbitPhase;
    float radial=(radius-uInner)/max(uOuter-uInner,.01);
    float rho=radial*uBandDetail*1.2+uRadialPhase*.16;
    vec2 orbit=vec2(cos(phase),sin(phase));
    float warp=volumeNoise(vec3(rho*1.7,orbit*3.0)+vec3(uSeed*.13,9.0,13.0),vec3(128.0));
    float grain=volumeNoise(vec3(rho*5.2,orbit*8.0)+vec3(3.0,uSeed*.29,17.0),vec3(128.0));
    float lane=.5+.5*cos(6.28318530718*(rho+.11*(warp-.5)));
    float filament=pow(.5+.5*cos(6.28318530718*(rho*2.3+.16*(grain-.5))),7.0);
    float shade=(.16+.64*pow(lane,2.0)+.20*filament)*(.46+.54*warp);
    return vec2(shade,clamp(.18+.58*lane+.24*grain,0.0,1.0));
}
vec2 lensedDustPattern(float radius,float angle){
    return vec2(1.0);
}
float volumeCell(ivec3 p){
    uint h=uint(p.x)*0x8da6b343u^uint(p.y)*0xd8163841u^uint(p.z)*0xcb1ab31fu;
    h^=h>>16u;h*=0x7feb352du;h^=h>>15u;h*=0x846ca68bu;h^=h>>16u;
    return float(h>>8u)/16777216.0;
}
float volumeNoise(vec3 p,vec3 period){
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float value=0.0;
    for(int z=0;z<2;z++)for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec3 corner=vec3(float(x),float(y),float(z));
        vec3 weight=mix(1.0-f,f,corner);
        value+=volumeCell(ivec3(mod(cell+corner,period)))*weight.x*weight.y*weight.z;
    }
    return value;
}
float lensedDustVolume(vec3 q){
    float radius=length(q.xz),angle=atan(q.z,q.x);
    float phase=(angle-uOrbitPhase)/6.28318530718;
    float rho=radius+uRadialPhase;
    vec3 p=vec3(rho*2.0,phase*12.0,atan(q.y,max(radius,.01))*8.0);
    vec3 period=vec3(256.0,12.0,128.0);
    float field=1.0;
    // Bounded angular offsets between octaves follow the reference's moving
    // density field without accumulating radial shear indefinitely.
    for(int i=0;i<4;i++){
        float scale=uDustScale*pow(2.0,float(i));
        vec3 drift=vec3(0.0,(i%2==0?uDustDriftA:uDustDriftB)*scale,0.0);
        float n=volumeNoise(p*scale+drift+vec3(uSeed,float(i)*13.0,17.0),period*scale);
        field*=.12+.88*n;
    }
    float porous=pow(smoothstep(.11,.34,field),2.2);
    return porous*uDustFill;
}
void main(){
    vec2 uv=texCoord*2.0-1.0;
    float shape=exp(-3.5*dot(uv,uv))*(1.0-smoothstep(.7,1.0,abs(uv.x)))*(1.0-smoothstep(.65,1.0,abs(uv.y)));
    if(shape*ParticleAlpha<.008)discard;
    // The same bent-ray core and foreground disk extinction used by the world
    // pass. Stop at this particle; a foreground particle is not hidden by a
    // core that lies farther down the ray. Sprites themselves use straight projection.
    BHResult path=bhTrace((CameraOriginRelative-CenterRelative)/WorldScale,normalize(RayPosition-CameraOriginRelative));
    float alpha=shape*ParticleAlpha*path.transmission;
    if(alpha<.005)discard;
    float heat=1.0-clamp((ParticleRadius-uCore)/(ParticleOuter-uCore),0.0,1.0);
    vec3 source=mix(ParticleCold,ParticleHot,heat*heat)*ParticleBrightness*(.4+1.6*heat);
    vec3 light=1.0-exp(-source);
    light=mix(light*12.92,1.055*pow(light,vec3(1.0/2.4))-.055,step(vec3(.0031308),light));
    fragColor=vec4(light*alpha,0.0);
}
