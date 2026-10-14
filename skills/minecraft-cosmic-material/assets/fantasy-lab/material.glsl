// Original procedural materials. Shared GLSL 150 / GLSL ES 300 subset.
// entry.z = 0 at the aperture; positive ray.z points into a finite interior.
uniform int fmMode;
uniform float fmTime, fmSeed, fmScale, fmSpeed, fmDetail, fmWarp;
uniform float fmEmission, fmDepth, fmParallax;
uniform vec3 fmBase, fmAccent, fmHighlight;
uniform float fmWaveHeight, fmWaveLength, fmWindAngle, fmChoppiness, fmFoam, fmRoughness, fmWaterOpacity;
uniform float fmWaterTilt;
uniform float fmWaterLightAzimuth, fmWaterLightElevation;
uniform sampler2D fmOcean0, fmOcean1;
uniform vec2 fmOceanLengths;

const float FM_PI = 3.14159265359;

uint fmHash(uint x) {
    x ^= x >> 16u; x *= 0x7feb352du;
    x ^= x >> 15u; x *= 0x846ca68bu;
    return x ^ (x >> 16u);
}
float fmRandom(ivec3 p) {
    uint h = uint(p.x)*1597334677u ^ uint(p.y)*3812015801u;
    h ^= uint(p.z)*2798796415u ^ uint(fmSeed);
    return float(fmHash(h) >> 8u) / 16777216.0;
}
float fmNoise(vec3 p) {
    ivec3 i = ivec3(floor(p));
    vec3 f = fract(p); f = f*f*(3.0-2.0*f);
    float a = mix(fmRandom(i),fmRandom(i+ivec3(1,0,0)),f.x);
    float b = mix(fmRandom(i+ivec3(0,1,0)),fmRandom(i+ivec3(1,1,0)),f.x);
    float c = mix(fmRandom(i+ivec3(0,0,1)),fmRandom(i+ivec3(1,0,1)),f.x);
    float d = mix(fmRandom(i+ivec3(0,1,1)),fmRandom(i+ivec3(1,1,1)),f.x);
    return mix(mix(a,b,f.y),mix(c,d,f.y),f.z);
}
float fmFbm(vec3 p) {
    float v=0.0, a=0.571429;
    for (int i=0; i<3; i++) {
        v += a*fmNoise(p);
        p = p.yzx*2.07+vec3(3.2,11.7,7.5); a *= 0.5;
    }
    return v;
}
mat2 fmRotate(float a) {
    float c=cos(a), s=sin(a); return mat2(c,s,-s,c);
}
float fmLine(vec2 p,vec2 a,vec2 b) {
    vec2 ab=b-a;
    return length(p-a-ab*clamp(dot(p-a,ab)/dot(ab,ab),0.0,1.0));
}
float fmStroke(float d,float width,float aa) {
    return 1.0-smoothstep(width,max(width+0.0001,width+aa),d);
}
float fmGlow(float d,float width,float aa) {
    return fmStroke(d,width,aa) + 0.28*exp(-d/max(width*5.0,aa));
}

// Segmented glyphs have stable discrete choices, independent of animation.
float fmGlyph(vec2 p,ivec2 id) {
    float d=fmLine(p,vec2(0.0,-0.32),vec2(0.0,0.32));
    float k=fmRandom(ivec3(id,61));
    if (k<0.72) d=min(d,fmLine(p,vec2(-0.22,0.20),vec2(0.0,0.04)));
    if (k>0.26) d=min(d,fmLine(p,vec2(0.0,0.28),vec2(0.23,0.05)));
    if (k<0.40 || k>0.77) d=min(d,fmLine(p,vec2(0.0,-0.08),vec2(0.24,-0.28)));
    if (k>0.45) d=min(d,fmLine(p,vec2(-0.20,-0.18),vec2(0.0,-0.03)));
    return d;
}

vec3 fmRuneVault(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=fmBase*0.38;
    for (int j=5; j>=0; j--) {
        float layer=float(j), z=(layer+0.3)*fmDepth/6.0;
        float shrink=1.0+layer*0.14;
        vec2 p=(entry+slope*z)*fmScale/shrink;
        float direction=mod(layer,2.0)<0.5?1.0:-1.0;
        p=fmRotate(layer*0.115+direction*t*(0.22+layer*0.055)+sin(t*0.7+layer)*0.10*fmWarp)*p;
        float aa=max(0.002,fp*fmScale/shrink*0.72);
        float r=length(p), angle=atan(p.y,p.x);
        float outer=abs(r-1.34), inner=abs(r-1.07);
        float count=floor(9.0+fmDetail*5.0);
        float angular=abs(fract((angle/FM_PI+1.0)*count)-0.5);
        float ticks=fmStroke(angular,0.07,aa*2.2)*fmStroke(abs(r-1.205),0.07,aa);
        float ring=fmGlow(min(outer,inner),0.014,aa)+ticks*0.70;
        vec2 polar=vec2((angle/FM_PI+1.0)*count,r*4.0);
        ivec2 gid=ivec2(floor(polar.x),j);
        float gd=fmGlyph(vec2(fract(polar.x)-0.5,(r-0.86)*3.8),gid);
        float glyph=fmGlow(gd,0.04,aa*3.8)*fmStroke(abs(r-0.86),0.135,aa);
        vec2 q=fmRotate(FM_PI*0.25)*p;
        float square=abs(max(abs(q.x),abs(q.y))-0.47);
        float diamond=abs(abs(p.x)+abs(p.y)-0.61)*0.7071;
        float seal=fmGlow(min(square,diamond),0.013,aa);
        seal += fmGlow(abs(r-0.18),0.012,aa)*0.75;
        float lane=min(abs(p.x),abs(p.y));
        float spokes=fmGlow(lane,0.006,aa)*smoothstep(1.37,1.48,r);
        float gate=0.20+0.8*smoothstep(0.0,0.15,abs(sin(angle*3.0+layer)));
        float charge=0.58+0.42*pow(0.5+0.5*sin(angle*2.0-direction*t*2.0+layer),4.0);
        float engraving=(ring*0.8+glyph*charge*1.2+seal+spokes*gate*0.32);
        float slab=exp(-abs(r-1.2)*2.4)*0.045;
        vec3 tint=mix(fmAccent,fmHighlight,0.5+0.5*sin(layer*1.6+r*2.5));
        color=color*(1.0-slab)+fmBase*slab;
        color += tint*engraving*(0.34+fmEmission*0.53)*exp(-layer*0.26);
    }
    return color;
}

float fmMetalHeight(vec2 p,float t) {
    // Continuous shears transport folds while time changes each deformation scale.
    p.y -= t*0.64;
    float seedPhase=fmRandom(ivec3(31,7,19))*6.28;
    p.x += sin(p.y*1.1+t*0.47+seedPhase)*fmWarp*0.7;
    p.y += sin(p.x*1.25-t*0.62)*fmWarp*0.56;
    p.x += sin(p.y*2.2+p.x*0.45+t*0.9)*fmWarp*0.18;
    vec2 w=vec2(fmNoise(vec3(p*0.68,3.7+t*0.23)),fmNoise(vec3(p*0.68,17.2-t*0.19)))-0.5;
    p += w*fmWarp*1.1;
    float bend=p.x*2.05+sin(p.y*1.3+t*0.45)*1.25;
    float ripple=sin(bend+t*0.65)+0.37*sin(bend*1.93-p.y*0.61-t*0.41);
    return ripple*0.43+0.15*sin(p.y*2.1-p.x*0.7+t*0.34);
}
vec3 fmLiquidMithril(vec2 entry,vec2 slope,float fp,float t) {
    vec2 p=(entry+slope*fmDepth*0.21)*fmScale;
    p=fmRotate(-0.42)*p;
    float h=fmMetalHeight(p,t), e=max(0.003,fp*fmScale*0.48);
    vec2 dh=vec2(fmMetalHeight(p+vec2(e,0.0),t)-h,fmMetalHeight(p+vec2(0.0,e),t)-h)/e;
    vec3 n=normalize(vec3(-dh*(0.65+fmDepth*0.08),1.0));
    vec3 view=normalize(vec3(-slope,1.4));
    vec3 ref=reflect(-view,n);
    float strip=exp(-pow(abs(ref.y-0.29+ref.x*0.13)*6.8,2.0));
    float edge=exp(-pow(abs(ref.x+0.49)*11.0,2.0));
    float warm=exp(-pow(abs(ref.y+0.61+ref.x*0.2)*8.0,2.0));
    float fresnel=pow(1.0-max(dot(n,view),0.0),3.0);
    float contour=0.5+0.5*sin(h*5.5+p.x*0.2);
    vec3 body=mix(fmBase,fmAccent,0.09+0.17*contour);
    body *= 0.33+0.36*max(dot(n,normalize(vec3(-0.5,0.7,1.0))),0.0);
    vec3 reflection=fmHighlight*(strip*1.6+edge*0.55)+fmAccent*warm*1.05;
    float brush=sin((p.y+h*0.5)*135.0*fmDetail);
    float micro=1.0+brush*0.045*(1.0-smoothstep(0.009,0.05,fp*fmScale));
    return (body+reflection*(0.6+fmEmission*0.22)+fmAccent*fresnel*0.35)*micro;
}

float fmVeinDistance(vec3 p) {
    float bend=sin(p.y*1.3+p.z*0.6)*0.35+sin(p.y*0.47-1.8)*0.8;
    float x=p.x+bend*fmWarp;
    float trunk=abs(sin(x*1.33+p.z*0.17))/1.33;
    float branch=abs(sin(p.y*2.0-abs(sin(x*1.33))*2.5+p.z*0.37))*0.31;
    float thin=abs(sin(p.y*4.8+x*1.8+sin(p.x*2.0)*0.3))*0.13;
    branch=max(branch,0.12-abs(sin(x*1.33))*0.24);
    return min(trunk,min(branch,thin+0.036));
}
vec3 fmJadeVeins(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=vec3(0.0);
    float trans=1.0, aa=max(0.004,fp*fmScale*0.65);
    for (int i=0;i<8;i++) {
        float z=(float(i)+0.5)*fmDepth/8.0;
        vec3 p=vec3((entry+slope*z)*fmScale,z*0.58+t*0.45);
        p.xy=fmRotate(0.43)*p.xy;
        p.x += sin(p.y*1.1+p.z*0.8-t*0.65)*fmWarp*0.14;
        p.y -= t*0.22;
        float n=fmFbm(p*1.6+9.0), d=fmVeinDistance(p);
        float trunk=1.0-smoothstep(0.033,0.12+aa,d);
        float filament=fmStroke(d,0.010+0.008*fmDetail,aa);
        float mineral=smoothstep(0.33,0.72,n);
        vec3 stone=mix(fmBase,fmAccent,mineral*0.85);
        stone *= 0.18+0.82*smoothstep(0.01,0.22,d);
        float pulse=0.5+0.5*sin(p.y*2.7+p.z*1.3-t*3.2);
        float amber=0.24+1.35*pulse*pulse*pulse;
        vec3 lit=stone+fmHighlight*filament*(0.35+fmEmission*0.60)*amber;
        float opacity=0.15+0.20*mineral+0.09*trunk;
        color += trans*lit*opacity;
        trans *= 1.0-opacity;
    }
    float back=fmNoise(vec3(entry*fmScale*0.6,43.1));
    return color+trans*mix(fmBase,fmHighlight,0.06)*(.4+back*0.6);
}

// Cell boundaries create opaque fragments; only the metal seams emit.
vec3 fmPorcelainCells(vec2 p) {
    ivec2 cell=ivec2(floor(p));
    float a=100.0,b=100.0; vec2 nearest=vec2(0.0); float variety=0.0;
    for (int y=-1;y<=1;y++) for (int x=-1;x<=1;x++) {
        ivec2 id=cell+ivec2(x,y);
        vec2 center=vec2(id)+0.18+0.64*vec2(fmRandom(ivec3(id,13)),fmRandom(ivec3(id,29)));
        vec2 d=p-center; float r=dot(d,d);
        if(r<a) { b=a;a=r;nearest=d;variety=fmRandom(ivec3(id,71)); }
        else if(r<b) b=r;
    }
    return vec3((sqrt(b)-sqrt(a))*0.5,length(nearest),variety);
}
vec3 fmGildedPorcelain(vec2 entry,vec2 slope,float fp,float t) {
    vec2 p=(entry+slope*fmDepth*0.055)*fmScale*1.7;
    p += (vec2(fmNoise(vec3(p*0.5,3.0)),fmNoise(vec3(p*0.5,19.0)))-0.5)*fmWarp*0.5;
    vec3 cell=fmPorcelainCells(p);
    float aa=max(0.002,fp*fmScale*1.3);
    float crack=fmStroke(cell.x,0.014+fmDetail*0.008,aa);
    float bevel=exp(-cell.x*31.0);
    vec3 clay=mix(fmBase,fmAccent,smoothstep(0.28,0.91,cell.z)*0.78);
    float glaze=0.63+0.23*sqrt(max(0.0,1.0-cell.y))+0.14*sin(entry.x*0.8+entry.y*1.1);
    float etching=fmStroke(abs(fract(cell.y*8.0)-0.5),0.04,aa*8.0)*0.1;
    etching *= smoothstep(0.68,0.80,cell.z)*(1.0-smoothstep(0.12,0.29,cell.x));
    clay=clay*glaze*(1.0-etching)*(1.0-bevel*0.29);
    // Ceramic stays fixed; a continuous luminous front travels through its seams.
    float phase=p.y*3.1-p.x*2.0-t*4.4+fmNoise(vec3(p*1.2,29.0))*1.4;
    float flash=pow(0.5+0.5*sin(phase),8.0);
    vec3 gold=mix(fmHighlight*0.38,fmHighlight*1.2+vec3(0.6,0.39,0.13),flash);
    gold *= 0.85+fmEmission;
    return mix(clay,gold,crack)+fmHighlight*bevel*0.055;
}

float fmSilkHeight(vec2 p,float t) {
    float fold=p.x*1.8+p.y*0.32-t*0.95+sin(p.y*0.73-t*0.65)*fmWarp;
    float crossWave=sin(p.y*1.6+p.x*0.38-t*0.73);
    return sin(fold)*0.52+sin(fold*2.1+p.y*0.42+t*0.48)*0.16+crossWave*0.15*fmWarp;
}
vec3 fmIridescentSilk(vec2 entry,vec2 slope,float fp,float t) {
    vec2 p=(entry+slope*fmDepth*0.12)*fmScale;
    p=fmRotate(0.38)*p;
    p += (vec2(fmRandom(ivec3(19,71,3)),fmRandom(ivec3(23,41,7)))-0.5)*0.8;
    float h=fmSilkHeight(p,t),e=max(0.003,fp*fmScale*0.5);
    vec2 dh=vec2(fmSilkHeight(p+vec2(e,0.0),t)-h,fmSilkHeight(p+vec2(0.0,e),t)-h)/e;
    vec3 n=normalize(vec3(-dh*1.2,1.0)),v=normalize(vec3(-slope,1.6));
    float ndv=max(0.0,dot(n,v));
    float film=h*3.1+p.y*0.42+(1.0-ndv)*7.0;
    vec3 interference=0.5+0.5*cos(film+vec3(0.0,2.1,4.2));
    vec3 color=mix(fmBase,fmAccent,interference.x*0.75);
    color=mix(color,fmHighlight,interference.y*0.42);
    vec2 weave=vec2(p.x+h*0.21,p.y+h*0.08)*24.0*fmDetail;
    vec2 thread=abs(fract(weave)-0.5)*2.0;
    float over=mod(floor(weave.x)+floor(weave.y),2.0);
    float fiber=mix(thread.x,thread.y,over);
    float coverage=1.0-smoothstep(0.15,0.85,fp*fmScale*24.0*fmDetail);
    float weft=mix(0.84,0.62+0.38*sqrt(max(0.0,1.0-fiber*fiber)),coverage);
    vec3 light=normalize(vec3(-0.4,0.55,1.0));
    float diffuse=0.34+0.58*max(dot(n,light),0.0);
    vec3 halfv=normalize(v+light);
    float sheen=pow(max(dot(n,halfv),0.0),20.0);
    float silk=pow(max(0.0,1.0-abs(dot(normalize(vec3(1.0,0.25,dh.x)),halfv))),7.0);
    return color*weft*diffuse+mix(fmHighlight,fmAccent,interference.z)*
        (sheen*0.65+silk*0.18)*(0.72+fmEmission*0.3);
}

vec3 fmFoldDomain(vec3 p,float t) {
    float phase=fmRandom(ivec3(3,17,29))*6.28;
    p.xy=fmRotate(p.z*0.18+sin(p.z*0.43-t*0.45)*fmWarp*0.3)*p.xy;
    p.x += sin(p.y*0.88+p.z*0.63-t*0.73+phase)*fmWarp*0.72;
    p.y += sin(p.x*1.1-p.z*0.52+t*0.61)*fmWarp*0.56;
    p.x += sin(p.y*2.1+p.z*1.4-t*1.13)*fmWarp*0.15;
    return p;
}
vec3 fmFoldedEmber(vec2 entry,vec2 slope,float fp,float t) {
    // Soft folded lamellae with front-to-back absorption. Fixed quadrature avoids
    // binary first-hit pops when a moving shell crosses the aperture.
    float scale=fmScale*1.18, trans=1.0;
    vec3 origin=vec3(entry*scale,-t*0.72);
    vec3 ray=normalize(vec3(slope+entry*0.19,1.0));
    float ds=fmDepth*scale/64.0;
    vec3 color=vec3(0.0);
    float previousPhase=0.0;
    for(int i=0;i<64;i++) {
        float distance=(float(i)+0.5)*ds;
        vec3 p=origin+ray*distance;
        vec3 q=fmFoldDomain(p,t);
        float sheet=q.y+0.68*sin(q.x*1.25-q.z*0.86)+0.3*cos(q.z*1.9+q.x*0.67);
        float phase=sheet*1.45+q.z*0.38;
        float segmentWidth=i==0?ds:abs(phase-previousPhase);
        previousPhase=phase;
        float fold=abs(sin(phase));
        // Filter each segment's phase footprint so transported sheets do not
        // become a stack of moving sample stripes.
        float width=sqrt(0.17*0.17+segmentWidth*segmentWidth*0.5+fp*fp*scale*scale);
        float density=exp(-fold*fold/(width*width))*1.65*0.17/width;
        float wrinkle=0.5+0.5*sin(q.x*4.3+q.z*3.7+sin(q.y*2.0)*1.4-t*1.5);
        float hot=pow(wrinkle,3.0+fmDetail*1.5);
        float frontLight=0.55+0.45*cos(sheet*1.8+q.z*0.35);
        vec3 flame=mix(fmAccent,fmHighlight,hot*0.85);
        vec3 radiance=fmBase*0.2+flame*(0.15+hot*1.9)*frontLight*(0.4+fmEmission);
        float alpha=1.0-exp(-density*ds*1.7);
        color+=trans*alpha*radiance;
        trans*=1.0-alpha;
    }
    return color+trans*fmBase;
}

// A shared virtual eye looks between two emissive, deforming cloud banks.
// The recursive shears change the 3-D field itself; time never offsets aperture UVs.
vec3 fmFlowDomain(vec3 p,float t) {
    float seed=fmRandom(ivec3(73,19,5))*6.28318;
    p.x+=0.08*sin(p.z*0.71+t*0.31+seed);
    p.y+=0.06*sin(p.z*0.53-p.x*0.45+t*0.41);
    float frequency=3.8;
    float amplitude=0.115*fmWarp;
    for(int octave=0;octave<6;octave++) {
        float band=float(octave);
        vec3 wave=vec3(p.y+p.z*0.21,p.z-p.x*0.16,p.x+p.y*0.32);
        p+=amplitude*sin(wave*frequency+vec3(1.1,-0.9,0.7)*t*(1.1+band*0.13)+seed);
        frequency*=1.93;
        amplitude*=0.51;
    }
    return p;
}
vec3 fmFantasyFlowRay(vec3 origin,vec3 direction,float fp,float t) {
    vec3 color=vec3(0.0);
    float travel=0.02,trans=1.0;
    float limit=fmDepth*2.8;
    for(int i=0;i<100;i++) {
        vec3 p=origin+direction*travel;
        vec3 q=fmFlowDomain(p,t);
        float bank=abs(q.y)-0.31-0.025*sin(q.x*0.9+q.z*0.65);
        float width=0.035+min(0.09,fp*travel*0.26);
        float shell=exp(-bank*bank/(width*width));
        float interior=exp(clamp(-bank*9.5,-14.0,4.0));
        float stepSize=min(0.14,0.004+max(-bank,bank*0.25)*0.23);
        float segment=min(stepSize,limit-travel);
        float veins=0.5+0.5*sin(q.z*3.6+q.x*2.1+q.y*7.0+t*0.8);
        float fire=pow(veins,2.0+fmDetail);
        float tint=0.5+0.5*sin(bank*16.0+q.x*0.9+q.z*0.18-t*0.45);
        vec3 spectrum=mix(fmAccent,fmHighlight,pow(tint,2.0));
        float edge=0.5+0.5*cos(q.y*4.0-q.z*0.6);
        vec3 light=spectrum*(0.65+fire*0.5)+fmHighlight*pow(edge,6.0)*0.06;
        // Proximity glow emphasizes the slowly crossed folds; depth absorption
        // keeps nearer folds distinct from the luminous interior behind them.
        float glow=(interior*0.000055/stepSize+shell*stepSize*0.03)*(segment/stepSize);
        color+=trans*glow*light*(0.25+fmEmission)*exp(-travel*0.025);
        trans*=exp(-shell*segment*0.24);
        travel+=segment;
        if(travel>=limit||trans<0.012)break;
    }
    return color*color*color*1.4+trans*fmBase;
}

vec3 fmFantasyFlow(vec2 aperture,vec2 eyeShift,float fp,float t) {
    vec3 origin=vec3(eyeShift*0.16,t*0.38);
    vec3 direction=normalize(vec3(aperture/fmScale*0.78+eyeShift,1.35));
    direction.yz=fmRotate(0.065)*direction.yz;
    return fmFantasyFlowRay(origin,direction,fp,t);
}

// A single screen-space ray field is clipped by the carrier geometry. Face UVs
// and normals must not enter this path or they restart the interior at edges.
vec3 fmFantasyWindow(vec2 screenUv,vec2 cameraAngles,float footprint) {
    float t=fmSpeed>0.0 ? fmTime*fmSpeed*2.0 : 0.0;
    vec3 direction=normalize(vec3(screenUv*1.56/fmScale,1.35));
    float pitch=cameraAngles.y*fmParallax+0.065;
    float yaw=-cameraAngles.x*fmParallax;
    direction.yz=fmRotate(pitch)*direction.yz;
    direction.xz=fmRotate(-yaw)*direction.xz;
    return max(vec3(0.0),fmFantasyFlowRay(vec3(0.0,0.0,t*0.38),
        direction,max(footprint,0.00001),t));
}

// Vertical emissive curtains: thin folded surfaces, vertical fibers, depth absorption.
vec3 fmAuroraVeil(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=vec3(0.0);
    float trans=1.0,ds=fmDepth/48.0;
    float seed=fmRandom(ivec3(29,81,17))*6.28;
    for(int i=0;i<48;i++) {
        float z=(float(i)+0.5)*ds;
        vec3 p=vec3((entry+slope*z)*fmScale,z*0.8);
        float fold=p.x*1.7+sin(p.z*1.3-t*0.8+seed)*fmWarp
                  +0.38*sin(p.x*2.0+p.z*2.1+t*0.45);
        float sheet=sin(fold),width=0.11+fp*fmScale*1.5+ds*0.22;
        float density=exp(-sheet*sheet/(width*width))*0.11/width;
        float fibers=0.52+0.48*sin(fold*29.0+p.z*9.0-t*3.3+sin(p.y*1.8)*0.65);
        fibers=mix(0.65,fibers,1.0-smoothstep(0.02,0.11,fp*fmScale));
        float hem=-0.78+0.27*sin(p.x*1.6+p.z*0.7-t*0.75+seed);
        float curtain=smoothstep(hem-0.25,hem+0.2,p.y)*exp(-max(p.y-hem,0.0)*0.5);
        float tint=smoothstep(-0.6,1.25,p.y-hem-0.42*sin(p.z*1.7));
        vec3 light=mix(fmAccent,fmHighlight,tint);
        float flare=exp(-(p.y-hem)*(p.y-hem)*16.0);
        float a=1.0-exp(-density*curtain*ds*1.9);
        color+=trans*a*light*(0.30+fibers*(0.35+fmDetail*0.4)+flare*0.45)*(0.55+fmEmission);
        trans*=1.0-a*0.55;
    }
    return color+trans*fmBase;
}

// Stable layered crystal facets enclose independently travelling, branching arcs.
vec3 fmStormPrism(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=fmBase;
    for(int layer=5;layer>=0;layer--) {
        float z=(float(layer)+0.5)*fmDepth/6.0;
        vec2 p=(entry+slope*z)*fmScale;
        p=fmRotate(float(layer)*0.41)*p+vec2(float(layer)*1.31,0.0);
        vec3 cell=fmPorcelainCells(p*1.35);
        float edge=exp(-cell.x*37.0);
        vec3 stone=mix(fmBase,fmAccent,0.12+cell.z*0.27)*(0.35+cell.y*0.28);
        float aa=max(0.004,fp*fmScale*1.2);
        float bolt=0.0;
        for(int arc=0;arc<3;arc++) {
            float a=float(arc),phase=fmRandom(ivec3(layer,arc,91))*6.28;
            float path=-1.4+a*1.4+sin(p.y*2.0+t*0.65+phase)*fmWarp*0.18;
            path+=(abs(fract(p.y*2.9+t*0.21+phase)*2.0-1.0)-0.5)*0.24;
            path+=sin(p.y*21.0-t*1.9+phase)*0.028;
            float d=abs(p.x-path);
            float branch=abs(p.x-path-(p.y-floor(p.y*0.7+phase)*1.43)*0.55);
            branch=max(branch,abs(fract(p.y*0.7+phase)-0.5)-0.24);
            d=min(d,branch);
            float wave=0.5+0.5*sin(t*4.1-p.y*2.6+phase);
            float charge=0.16+1.3*pow(wave,6.0);
            bolt+=fmGlow(d,0.008+fmDetail*0.005,aa)*charge;
        }
        float facet=0.10+edge*0.13;
        color=mix(color,stone,facet);
        color+=fmAccent*edge*0.06+mix(fmAccent,fmHighlight,0.78)*bolt*(0.18+fmEmission*0.35)*exp(-z*0.24);
    }
    return color;
}

// Analytic ray/sphere hits, moving centers, transparent shells and thin-film color.
vec3 fmAetherBubbles(vec3 origin,vec3 direction,float fp,float t) {
    vec3 color=vec3(0.0);float trans=1.0;
    float seed=fmRandom(ivec3(41,91,8))*6.28;
    for(int layer=0;layer<5;layer++) {
      float z=0.8+(float(layer)+0.5)*fmDepth/5.0;
      vec2 plane=origin.xy+direction.xy/max(direction.z,0.2)*(z-origin.z);
      // Translate stable spatial cells upward; include neighbours so identities
      // remain continuous when the ray crosses a cell. No finite-screen void.
      vec2 transport=vec2(0.0,t*0.38);
      ivec2 cell=ivec2(floor((plane-transport)/1.6));
      for(int nx=-1;nx<=1;nx++) for(int ny=-1;ny<=1;ny++) {
        ivec2 bubbleCell=cell+ivec2(nx,ny);
        ivec3 id=ivec3(bubbleCell,layer+103);
        float variety=fmRandom(id),phase=variety*6.28;
        vec2 jitter=vec2(fmRandom(id+ivec3(0,0,17)),fmRandom(id+ivec3(0,0,31)))-0.5;
        vec3 center=vec3((vec2(bubbleCell)+0.5+jitter*0.65)*1.6+transport,z);
        center.x+=sin(t*0.48+phase+center.y*0.6)*fmWarp*0.23;
        float radius=(0.21+variety*0.34)*(0.65+fmDetail*0.35);
        vec3 oc=origin-center;float b=dot(oc,direction);
        float c=dot(oc,oc)-radius*radius,h=b*b-c;
        float edgeWidth=max(0.0003,fp*max(-b,1.0)*radius*0.75);
        float coverage=smoothstep(-edgeWidth,edgeWidth,h);
        if(h < -edgeWidth || b>0.0)continue;
        float distance=-b-sqrt(max(h,0.0));
        vec3 hit=origin+direction*distance,normal=normalize(hit-center);
        float facing=clamp(dot(normal,-direction),0.0,1.0);
        float rim=pow(1.0-facing,3.0);
        float film=8.0*facing+normal.y*3.0+sin(normal.x*3.0+t*0.8+seed)*1.2;
        vec3 spectral=0.5+0.5*cos(film+vec3(0.0,2.1,4.2));
        vec3 tint=mix(fmAccent,fmHighlight,spectral.x*0.7);
        tint=mix(tint,fmHighlight.brg,spectral.y*0.33);
        float glint=pow(max(dot(normal,normalize(vec3(-0.4,0.7,-1.0))),0.0),28.0);
        float ripple=0.5+0.5*sin(normal.y*11.0+normal.x*7.0-t*2.0+phase);
        vec3 lit=tint*(0.08+rim*0.65)+fmHighlight*(glint*1.7+rim*ripple*0.12)*(0.6+fmEmission);
        float alpha=(0.12+rim*0.34)*coverage;
        color+=trans*lit*coverage*exp(-float(layer)*0.09);
        trans*=1.0-alpha;
      }
    }
    return color+trans*fmBase;
}

// Opaque mechanical wheels in several depths. Teeth and spokes rotate as geometry.
vec3 fmClockworkCore(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=fmBase;
    float seed=fmRandom(ivec3(71,4,39))*6.28;
    for(int layer=3;layer>=0;layer--) {
        float z=(float(layer)+0.4)*fmDepth/4.0;
        vec2 p=(entry+slope*z)*fmScale;
        p=fmRotate(float(layer)*0.72+seed*0.12)*p;
        ivec2 cell=ivec2(floor(p/1.25));
        for(int nx=-1;nx<=1;nx++) for(int ny=-1;ny<=1;ny++) {
            ivec2 wheelCell=cell+ivec2(nx,ny);
            float variety=fmRandom(ivec3(wheelCell,layer+147));
            vec2 center=(vec2(wheelCell)+0.5)*1.25;
            float radius=0.54+variety*0.08;
            float turn=mod(float(wheelCell.x+wheelCell.y),2.0)<0.5?1.0:-1.0;
            vec2 q=p-center,local=fmRotate(turn*t*(0.58+float(layer)*0.12)+seed+float(layer)*0.3+variety*0.2)*q;
            float r=length(local),angle=atan(local.y,local.x);
            float teeth=floor(16.0+fmDetail*8.0);
            float tooth=0.5+0.5*cos(angle*teeth);
            float outside=radius+0.045*smoothstep(0.25,0.7,tooth);
            float aa=max(0.003,fp*fmScale*0.75);
            float body=fmStroke(max(r-outside,0.0),0.0,aa);
            float ring=smoothstep(radius*0.64-aa,radius*0.64+aa,r);
            float spoke=fmStroke(abs(sin(angle*3.0))*r,0.045+fmWarp*0.008,aa);
            float hub=1.0-smoothstep(0.13-aa,0.13+aa,r);
            float metal=body*max(ring,max(spoke,hub));
            float bevel=exp(-abs(r-(outside-0.014))*70.0);
            float light=0.4+0.38*cos(angle-0.7)+0.11*sin(r*57.0);
            vec3 brass=mix(fmAccent,fmHighlight,clamp(light,0.0,1.0))*(0.35+light*0.4);
            brass+=fmHighlight*bevel*0.3;
            float rivetAngle=abs(fract(angle/(2.0*FM_PI)*8.0)-0.5)*r;
            float rivet=fmGlow(length(vec2(r-radius*0.80,rivetAngle)),0.011,aa);
            brass+=fmHighlight*rivet*(0.3+fmEmission*0.35);
            color=mix(color,brass,metal);
        }
    }
    return color;
}

// Rounded branching filaments and nodules. A luminous packet follows each branch.
vec3 fmLivingMycelium(vec2 entry,vec2 slope,float fp,float t) {
    vec3 color=fmBase;
    for(int layer=5;layer>=0;layer--) {
        float z=(float(layer)+0.4)*fmDepth/6.0;
        vec2 p=(entry+slope*z)*fmScale;
        p=fmRotate(float(layer)*0.27)*p;
        ivec2 cell=ivec2(floor(p/1.6));vec2 local=p-vec2(cell)*1.6-0.8;
        float phase=fmRandom(ivec3(cell,layer+121))*6.28;
        local.x+=sin(local.y*2.6+t*0.6+phase)*fmWarp*0.07;
        float bend=sin(local.y*3.1+phase+t*0.55)*0.055*fmWarp;
        vec2 q=vec2(local.x-bend,local.y);
        float d=abs(q.x),nodes=0.0;
        for(int branch=0;branch<6;branch++) {
            float row=floor(float(branch)*0.5);
            float side=mod(float(branch),2.0)<0.5?-1.0:1.0;
            float baseY=-0.66+row*0.51;
            vec2 a=vec2(0.0,baseY),b=vec2(side*0.31,baseY+0.23),c=vec2(side*0.67,baseY+0.38);
            b.y+=sin(phase+row+t*0.65)*fmWarp*0.045;
            d=min(d,min(fmLine(q,a,b),fmLine(q,b,c)));
            float nodeDistance=length(q-c);
            nodes+=exp(-nodeDistance*nodeDistance*520.0);
        }
        float aa=max(0.003,fp*fmScale*0.9);
        float filament=fmGlow(d,0.009+fmDetail*0.007,aa);
        float sheath=exp(-d*24.0);
        float pulse=pow(0.5+0.5*sin((q.y+abs(q.x)*0.5)*8.0-t*4.0+phase),8.0);
        float nodule=nodes*(0.55+0.45*sin(t*1.4+phase));
        vec3 body=mix(fmBase,fmAccent,0.25+0.12*sin(local.y*7.0+phase));
        color=mix(color,body,sheath*0.18);
        vec3 light=mix(fmAccent,fmHighlight,pulse);
        color+=(light*filament*(0.15+pulse*1.15)+fmHighlight*nodule*0.32)*(0.4+fmEmission)*exp(-z*0.32);
    }
    return color;
}

// Original television snow material inspired by the supplied CRT/VHS studies.
// Each output pixel is an independent integer-hash sample; no scanline or
// neighboring-pixel interpolation is used, so the field has no banded rhythm.
vec3 fmTvSnow(vec2 entry,vec2 slope,float fp,float t) {
    vec2 uv=fract(entry*0.52+0.5);
    float cellScale=mix(0.72,1.35,clamp(fmDepth/6.0,0.0,1.0));
    vec2 pixel=floor(uv*vec2(800.0,540.0)*cellScale);
    float frame=floor(t*(23.0+fmWarp*7.0));
    float signal=fmRandom(ivec3(pixel,int(frame)));
    float threshold=mix(0.62,0.38,clamp(fmDetail/2.0,0.0,1.0));
    // Quantize directly: every output channel is exactly black or white.
    float bit=step(threshold,signal);
    return vec3(bit);
}

// Two-domain spectral water. The host supplies genuine complex-spectrum iFFT
// displacement maps. The material marches their heightfield inside one shared
// viewing window; original Minecraft carrier geometry remains unchanged.
vec4 fmOceanField(vec2 p,float footprint) {
    float l0=max(0.0,log2(max(footprint*128.0/fmOceanLengths.x,1.0)));
    float l1=max(0.0,log2(max(footprint*128.0/fmOceanLengths.y,1.0)));
    vec4 a=textureLod(fmOcean0,p/fmOceanLengths.x,min(l0,7.0));
    vec4 b=textureLod(fmOcean1,p/fmOceanLengths.y,min(l1,7.0));
    vec4 f=(a+b*fmDetail)*fmWaveHeight;
    f.yz*=fmChoppiness;
    return f;
}
float fmOceanHeight(vec2 p,float footprint) {
    vec2 q=p;
    for(int i=0;i<2;i++)q=p-fmOceanField(q,footprint).yz;
    return fmOceanField(q,footprint).x;
}
vec3 fmWaterSky(vec3 d,bool showSun) {
    // The window is a water-only material. This environment is a dark water
    // reflection tint, never a visible sky or cloud layer.
    float grazing=1.0-abs(d.y);
    vec3 c=mix(vec3(0.006,0.028,0.045),vec3(0.035,0.16,0.20),clamp(grazing,0.0,1.0));
    return c;
}
vec3 fmWaterLight() {
    float az=fmWaterLightAzimuth*FM_PI/180.0;
    float el=clamp(fmWaterLightElevation,-0.2,1.4);
    // Water lies in XY; its visible normal points toward -Z.
    return normalize(vec3(cos(az)*cos(el),sin(az)*cos(el),-sin(el)));
}
vec3 fmWaterSurface(vec3 origin,vec3 direction,float fp,float t) {
    // Water is a top-down sheet: remove the horizon and camera grazing angle.
    // The small lateral term preserves gentle parallax without exposing sky.
    direction=normalize(vec3(direction.xy*0.65,1.0));
    vec3 eye=vec3(origin.xy*1.8,-(2.8+fmWaveHeight*0.5));
    direction=normalize(direction);
    // First crossing through a bounded wave-height slab, then bisection. Distant
    // waves use filtered height maps, not a time-reseeded noise field.
    float bound=max(fmWaveHeight*4.5,0.01);
    float nearT=max(0.0,(-bound-eye.z)/direction.z);
    float farT=min((bound-eye.z)/direction.z,1600.0);
    if(nearT>=farT){nearT=0.0;farT=1600.0;}
    float previousT=nearT,hitT=farT;bool found=false;
    // Uniform bound keeps ANGLE from expanding the nested texture samples into
    // a huge straight-line shader. The allowed detail range yields 24 steps.
    int steps=24+int(min(fmDetail,0.99));
    for(int i=1;i<=steps;i++) {
        float candidate=mix(nearT,farT,float(i)/float(steps));
        vec3 p=eye+direction*candidate;
        float footprint=max(fp*candidate*fmScale*0.32/sqrt(max(direction.z,0.08)),0.003);
        if(p.z+fmOceanHeight(p.xy*fmScale*1.8,footprint)>=0.0) {
            hitT=candidate;found=true;break;
        }
        previousT=candidate;
    }
    if(!found){
        hitT=clamp((-eye.z)/max(direction.z,0.08),nearT,farT);
        previousT=max(nearT,hitT-0.02);
    }
    for(int i=0;i<5;i++) {
        float mid=(previousT+hitT)*0.5;vec3 p=eye+direction*mid;
        float footprint=max(fp*mid*fmScale*0.32/sqrt(max(direction.z,0.08)),0.003);
        if(p.z+fmOceanHeight(p.xy*fmScale*1.8,footprint)>0.0)hitT=mid;else previousT=mid;
    }
    vec3 hit=eye+direction*((previousT+hitT)*0.5);
    // The top-down sheet needs a tighter world-space footprint than the old
    // horizon view; this exposes several overlapping short-wave groups.
    vec2 p=hit.xy*fmScale*1.8;
    float footprint=max(fp*hitT*fmScale*0.32/sqrt(max(direction.z,0.08)),0.003);
    // Invert lateral transport at the visible hit for coherent crest shading.
    vec2 q=p;
    for(int i=0;i<3;i++)q=p-fmOceanField(q,footprint).yz;
    vec4 center=fmOceanField(q,footprint);
    float e=max(fmOceanLengths.y/256.0,footprint*0.35);
    vec4 dx=(fmOceanField(q+vec2(e,0),footprint)-fmOceanField(q-vec2(e,0),footprint))/(2.0*e);
    vec4 dy=(fmOceanField(q+vec2(0,e),footprint)-fmOceanField(q-vec2(0,e),footprint))/(2.0*e);
    vec3 tx=vec3(1.0+dx.y,dx.z,-dx.x),ty=vec3(dy.y,1.0+dy.z,-dy.x);
    vec3 n=normalize(-cross(tx,ty)),v=-direction;
    n=normalize(n+v*max(0.025-dot(n,v),0.0));
    float nv=max(dot(n,v),0.001);
    float fresnel=0.0204+0.9796*pow(1.0-nv,5.0*exp(-2.69*fmRoughness))
        /(1.0+22.7*pow(fmRoughness,1.5));
    // The directional BRDF already contains the sun: adding its sharp disk to
    // the reflected sky again gives broad white metallic streaks.
    vec3 reflection=fmWaterSky(reflect(direction,n),false);
    vec3 refracted=refract(direction,n,1.0/1.333);
    vec2 bottom=q+center.yz+refracted.xy/max(refracted.z,0.12)*fmDepth;
    float sand=fmNoise(vec3(bottom*1.8,2.17));
    vec3 transmission=exp(-vec3(0.75,0.23,0.14)*fmDepth/max(refracted.z,0.12));
    float micro=fmFbm(vec3(q*0.9,7.1));
    vec3 water=mix(fmBase+fmAccent*0.20,fmAccent*(0.55+0.35*sand),transmission);
    water*=0.82+0.18*micro;
    vec3 l=fmWaterLight(),h=normalize(l+v);
    float nl=max(dot(n,l),0.001),nh=max(dot(n,h),0.0),vh=max(dot(v,h),0.0);
    float a2=max(pow(fmRoughness,4.0),min(footprint*footprint*0.002,0.07)+0.00004);
    float denom=nh*nh*(a2-1.0)+1.0;
    float distribution=a2/(FM_PI*denom*denom);
    float gV=2.0*nv/(nv+sqrt(a2+(1.0-a2)*nv*nv));
    float gL=2.0*nl/(nl+sqrt(a2+(1.0-a2)*nl*nl));
    float specular=distribution*gV*gL*(0.0204+0.9796*pow(1.0-vh,5.0))/(4.0*nv);
    float scatter=pow(max(dot(l,-v),0.0),4.0)*max(center.x,0.0)*0.7;
    vec3 color=water*(0.48+0.52*nl)*(1.0-fresnel)+reflection*fresnel;
    color+=fmAccent*scatter+fmHighlight*specular*(1.0+fmEmission*3.0);
    float determinant=(1.0+dx.y)*(1.0+dy.z)-dy.y*dx.z;
    float compression=1.0-determinant;
    float crest=smoothstep(-0.10,0.32,center.x/max(fmWaveHeight,0.001));
    float foamPatch=fmFbm(vec3(q*4.3,5.12));
    float foam=fmFoam*max(smoothstep(0.075,0.40,compression),0.35*crest)*crest*smoothstep(0.30,0.66,foamPatch);
    foam*=1.0-smoothstep(0.3,1.8,footprint);
    color=mix(color,vec3(0.72,0.80,0.83)*(0.45+0.55*nl),clamp(foam,0.0,0.95));
    float haze=1.0-exp(-hitT*0.008);
    return mix(color,vec3(0.38,0.52,0.65),haze);
}

vec3 fmNewInteriorRay(vec3 origin,vec3 direction,float fp,float t) {
    vec2 slope=direction.xy/max(direction.z,0.2);
    vec2 entry=origin.xy-slope*origin.z;
    if(fmMode==7)return fmAuroraVeil(entry,slope,fp,t);
    if(fmMode==8)return fmStormPrism(entry,slope,fp,t);
    if(fmMode==9)return fmAetherBubbles(origin*vec3(fmScale,fmScale,1.0),
        normalize(direction*vec3(fmScale,fmScale,1.0)),fp,t);
    if(fmMode==10)return fmClockworkCore(entry,slope,fp,t);
    if(fmMode==12)return fmWaterSurface(origin,direction,fp,t);
    if(fmMode==13)return fmTvSnow(entry,slope,fp,t);
    return fmLivingMycelium(entry,slope,fp,t);
}

// New interior styles share one ray field across carrier faces, like mode 6.
vec3 fmNewInteriorWindow(vec2 screenUv,vec2 cameraAngles,float footprint) {
    float t=fmSpeed>0.0?fmTime*fmSpeed:0.0;
    vec3 direction=normalize(vec3(screenUv*1.56,1.8));
    direction.yz=fmRotate(cameraAngles.y*fmParallax)*direction.yz;
    direction.xz=fmRotate(cameraAngles.x*fmParallax)*direction.xz;
    return max(vec3(0.0),fmNewInteriorRay(vec3(0.0,0.0,-1.0),direction,
        max(footprint,0.00001),t));
}

vec3 fantasyMaterial(vec3 entry,vec3 ray,float footprint) {
    float t=fmSpeed>0.0 ? fmTime*fmSpeed : 0.0;
    vec2 slope=ray.xy/max(ray.z,0.2)*fmParallax;
    float fp=max(footprint,0.00001);
    if(fmMode>=7) {
        vec2 eyeShift=(ray.xy/max(ray.z,0.2)-entry.xy/3.0)*fmParallax;
        vec3 direction=normalize(vec3(entry.xy*0.52+eyeShift,1.8));
        return max(vec3(0.0),fmNewInteriorRay(vec3(eyeShift*0.16,-1.0),direction,fp,t));
    }
    if(fmMode==6) {
        vec2 eyeShift=(ray.xy/max(ray.z,0.2)-entry.xy/3.0)*fmParallax;
        return max(vec3(0.0),fmFantasyFlow(entry.xy,eyeShift,fp,t*2.0));
    }
    if(fmMode==0) return max(vec3(0.0),fmRuneVault(entry.xy,slope,fp,t));
    if(fmMode==1) return max(vec3(0.0),fmLiquidMithril(entry.xy,slope,fp,t));
    if(fmMode==2) return max(vec3(0.0),fmJadeVeins(entry.xy,slope,fp,t));
    if(fmMode==3) return max(vec3(0.0),fmGildedPorcelain(entry.xy,slope,fp,t));
    if(fmMode==4) return max(vec3(0.0),fmIridescentSilk(entry.xy,slope,fp,t));
    return max(vec3(0.0),fmFoldedEmber(entry.xy,slope,fp,t));
}
