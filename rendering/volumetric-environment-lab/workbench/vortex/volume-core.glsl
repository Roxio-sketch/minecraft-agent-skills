// Original finite world medium. Shared GLSL 150 / WebGL2 implementation.
// Positions use block units in one common coordinate frame. A backend may rebase
// origin, Bounds.xyz and Layer.x together on CPU for large world coordinates.
// RGB is linear radiance. veIntegrate returns (premultiplied L, transmittance T).
uniform float Time;
uniform highp sampler3D VortexNoiseTex;
uniform vec4 VortexShape; // bottomRadius, topRadius, profile, hollow
uniform vec4 VortexFlow; // rotation radians/sec, upward units/sec, bend, turbulence
uniform vec4 VortexRing; // ring radius, width, density, height
uniform vec4 VortexWater; // water depth, waves amplitude, waves speed, foam
uniform vec4 VortexLife; // duration, inward warp, canopy density, mode
float vortexProgress(){return clamp(max(Time,0.0)/VortexLife.x,0.0,1.0);}

uniform float Seed;
uniform vec4 Bounds;
uniform vec4 Layer;
uniform vec4 Density;
uniform vec4 Flow;
uniform vec4 Optics;
uniform vec4 Glow;
uniform vec4 Flash;
uniform vec4 FlashShape;
uniform vec3 MediumColor;
uniform vec3 EmissionColor;
uniform vec3 FlashColor;
uniform vec3 SunDirection;
uniform vec3 SunColor;
uniform int ViewSteps;
uniform int LightSteps;
uniform vec4 EndWarp;
uniform vec4 EndCarve;
uniform vec4 EndDepth;

float veHash(vec3 p) {
    // Independent integer mixer. Quantize event keys, keep lattice samples
    // deterministic without vendor-dependent sin() or third-party hash code.
    uvec3 q=uvec3(ivec3(floor(p*256.0)));
    uint h=(q.x*1580030173u)^(q.y*59260789u)^(q.z*1911520717u)^1831565813u;
    h=(h^(h>>16u))*741103597u;
    h=(h^(h>>13u))*1597334677u;
    h=h^(h>>16u);
    return float(h&16777215u)*(1.0/16777216.0);
}
float veNoise(vec3 p) {
    vec3 cell=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(veHash(cell),veHash(cell+vec3(1,0,0)),f.x),
                   mix(veHash(cell+vec3(0,1,0)),veHash(cell+vec3(1,1,0)),f.x),f.y),
               mix(mix(veHash(cell+vec3(0,0,1)),veHash(cell+vec3(1,0,1)),f.x),
                   mix(veHash(cell+vec3(0,1,1)),veHash(cell+vec3(1,1,1)),f.x),f.y),f.z);
}
float veFbm(vec3 p) {
    return veNoise(p)*0.57142857+veNoise(p*2.03+vec3(11.3,3.7,8.2))*0.28571429
           +veNoise(p*4.11+vec3(7.1,19.2,2.4))*0.14285714;
}
vec3 veCurl(vec3 p) {
    // Analytic curl of A=(sin(y)sin(z),sin(z)sin(x),sin(x)sin(y)).
    // This bounded field is divergence-free; the warped density is artistic.
    vec3 s=sin(p), c=cos(p);
    return vec3(s.x*(c.y-c.z),s.y*(c.z-c.x),s.z*(c.x-c.y));
}
float veSupport(vec3 world){
    vec3 p=world-Bounds.xyz;float y=world.y-Layer.x;
    return (1.0-smoothstep(Bounds.w*.93,Bounds.w,length(p.xz)))*step(0.0,y)*step(y,Layer.y);
}
float veEndDensity(vec3 world, float support) {
    vec3 local=world-Bounds.xyz;
    float clock=max(Time,0.0);
    vec3 p=vec3(local.x,local.y/EndWarp.y,local.z)*Density.z;
    float angle=local.y*EndWarp.z+clock*EndWarp.w;
    float c=cos(angle),s=sin(angle);
    vec2 turned=vec2(c*p.x-s*p.z,s*p.x+c*p.z);
    float twistFade=1.0-smoothstep(Bounds.w*0.25,Bounds.w*0.65,length(local));
    p.xz=mix(p.xz,turned,twistFade);
    p+=vec3(Seed*0.0131,Seed*0.0097,Seed*0.0173);
    float broad=veFbm(p);
    // Sparse coherent low-noise strands, stretched in Y then twisted by height.
    // Independent formulation of the mechanism studied in Bliss 2.1.2.
    float body=pow(max(1.0-broad,0.0),mix(7.0,2.4,Density.y))*1.5;
    vec3 detail=p*6.3-vec3(Flow.x,0.15,Flow.y)*clock*Flow.z*Density.z;
    body=max(body-Density.w*veNoise(detail)*0.32,0.0);
    vec3 cavity=vec3(local.x,local.y*EndCarve.y,local.z);
    float centerCarve=EndCarve.x>0.0?pow(max(1.0-length(cavity)/max(EndCarve.x,0.01),0.0),2.0)*3.0:0.0;
    float ringCarve=0.0;
    if(EndCarve.z>0.0 && EndCarve.w>0.0) {
        float torusDistance=length(vec2(length(local.xz)-EndCarve.z,local.y*EndCarve.y));
        ringCarve=1.0-smoothstep(EndCarve.w*0.35,EndCarve.w,torusDistance);
    }
    // The lower medium is added after carving. The cavity is not an absolute
    // vacuum whenever voidDensity is enabled, just as the reference topology.
    float rise=max(world.y-EndDepth.y,0.0);
    float lower=EndDepth.x*exp2(-sqrt(rise)*EndDepth.z);
    return (max(body-centerCarve-ringCarve,0.0)+lower)*Density.x*support;
}
float vePurpleBrightness(vec3 world) {
    vec3 local=world-Bounds.xyz;
    vec3 p=vec3(local.x,local.y/max(EndWarp.y,1.0),local.z)*Density.z;
    p.x+=max(Time,0.0)*Flow.z*Density.z;
    float noise=veNoise(p+vec3(Seed*0.019,5.7,Seed*0.013));
    // World brightness modulation only. Density / transmittance stays separate.
    return 0.5+0.15*sin(noise*11.0+max(Time,0.0)*Flow.z*0.4);
}
vec2 vortexAxis(float h){
    return VortexFlow.z*sin(h*3.14159265)*vec2(sin(h*4.0+Time*.13),cos(h*3.0+Time*.11));
}
float veDensity(vec3 world){
    float support=veSupport(world);if(support<=0.0||Density.x<=0.0)return 0.0;
    float progress=vortexProgress();if(progress<=0.0)return 0.0;
    float fullHeight=Layer.y*.84;float y=world.y-Layer.x;float h=clamp(y/fullHeight,0.0,1.0);
    vec2 p=world.xz-Bounds.xz-vortexAxis(h);
    float radius=mix(VortexShape.x,VortexShape.y,pow(h,VortexShape.z));
    float profileNoise=textureLod(VortexNoiseTex,vec3(Seed*.021,h*.62-Time*.009,.43),0.0).a;
    radius*=.87+.28*profileNoise;
    float r=length(p), normalized=r/max(radius,1.0);
    // Bound radial shear: multiplying radius-dependent speed by unlimited time
    // winds texture coordinates into subpixel concentric filaments.
    float angle=-VortexFlow.x*Time-h*1.6+(profileNoise-.5)*VortexFlow.w*.55;
    vec2 q=mat2(cos(angle),sin(angle),-sin(angle),cos(angle))*p;
    q*=1.0+VortexLife.y*(1.0-h)*smoothstep(0.0,VortexLife.x*2.0,Time);
    float frequency=Density.z*.16;
    vec3 np=vec3(q.x,(y-VortexFlow.y*Time)*.72,q.y)*frequency+vec3(Seed*.013,.17,Seed*.019);
    vec3 warp=veCurl(np*9.0+Time*.1)*VortexFlow.w*.018;
    vec4 cells=textureLod(VortexNoiseTex,np+warp,0.0);
    float roughR=normalized+((.55-cells.r)*.72+(.55-cells.g)*.28+(.50-cells.b)*.17)*VortexFlow.w;
    float envelope=1.0-smoothstep(.66,1.09,roughR);
    if(VortexShape.w>0.0)envelope*=smoothstep(VortexShape.w*.4,VortexShape.w,normalized);
    float reveal=VortexLife.w>.5?smoothstep(h*.72,h*.72+.25,progress):smoothstep((1.0-h)*.72,(1.0-h)*.72+.25,progress);
    float topFade=1.0-smoothstep(fullHeight*.98,fullHeight*1.08,y);
    float chunk=clamp((mix(cells.r,cells.g,.30)-.24)*2.65+(1.0-normalized)*.22,0.0,1.0);
    float erosion=(1.0-cells.g)*.24+(1.0-cells.b)*.13;
    float body=max(envelope*chunk-erosion*VortexFlow.w*.45,0.0)*reveal*topFade;
    // Expose the liquid core and climbing strands in the water mode. The upper
    // condensation funnel stays volumetric; lower mist must not hide the water.
    if(VortexLife.w>.5)body*=mix(.12,1.0,smoothstep(.43,.82,h));
    if(VortexLife.w>1.5)body=0.0;
    vec2 rp=world.xz-Bounds.xz;float ringR=length(rp);float ra=ringR>.00001?atan(rp.y,rp.x):0.0;
    float burst=.5+.5*sin(ra*7.0-Time*VortexFlow.x*1.8+y*.17);
    float fanAngle=ra-Time*VortexFlow.x*.55-y*.025;
    float fan=pow(max(cos(fanAngle+.6),0.0),8.0)+.70*pow(max(cos(fanAngle-2.2),0.0),12.0)+.30*pow(max(cos(fanAngle+2.2),0.0),10.0);
    float baseRadius=VortexRing.x+y*(.30+.16*burst);
    vec3 sp=vec3(rp.x,y-Time*VortexFlow.y*.85,rp.y)*.022;
    vec4 sprayCells=textureLod(VortexNoiseTex,sp+vec3(Time*.011,Seed*.013,0.0),0.0);
    float ringDistance=(ringR-baseRadius)/max(VortexRing.y*(.65+.6*sprayCells.r),1.0);
    float ring=exp(-ringDistance*ringDistance);
    ring*=exp(-pow(y/max(VortexRing.w*(.65+.65*burst),1.0),1.4));
    ring*=max(0.0,(sprayCells.r-.25)*2.7)*( .4+.6*sprayCells.b);
    ring*=VortexLife.w>.5?.10+.90*min(fan,1.0):.24+.76*min(fan*1.4,1.0);
    float fragmentDistance=(ringR-VortexRing.x*1.45)/max(VortexRing.y*.8,1.0);
    float fragments=exp(-fragmentDistance*fragmentDistance)*exp(-y/max(VortexRing.w*.6,1.0));
    fragments*=smoothstep(.63,.82,sprayCells.b)*fan*.15;
    ring+=fragments;
    ring*=VortexRing.z*smoothstep(.03,.35,progress);
    if(VortexLife.w>.5)ring*=.40;
    if(VortexLife.w>1.5)ring*=.50;
    vec3 cp=world-vec3(Bounds.x,Layer.x+fullHeight,Bounds.z);
    float capAngle=-Time*VortexFlow.x*.24;
    vec2 capQ=mat2(cos(capAngle),sin(capAngle),-sin(capAngle),cos(capAngle))*cp.xz;
    vec4 capCells=textureLod(VortexNoiseTex,vec3(capQ.x,cp.y*.85,capQ.y)*frequency*1.06+vec3(.37,Seed*.021,Time*.009),0.0);
    float capRadius=length(cp.xz*vec2(.92,1.08))/Bounds.w+(.53-capCells.r)*.26+(.5-capCells.g)*.12;
    float capThickness=Layer.y*(.12+.10*capCells.a);
    float capShape=1.0-smoothstep(.69,.89,capRadius);
    capShape*=1.0-smoothstep(capThickness*.4,capThickness,abs(cp.y)+((.5-capCells.r)*.9+(.5-capCells.g)*.3+(.5-capCells.b)*.2)*capThickness);
    float canopy=max(capShape*clamp((mix(capCells.r,capCells.g,.32)-.22)*2.6,0.0,1.0)-(1.0-capCells.b)*.15,0.0);
    canopy*=VortexLife.z*smoothstep(0.0,.3,progress);
    if(VortexLife.w>1.5)canopy=0.0;
    return (body+ring+canopy)*Density.x*support;
}
bool veSphereInterval(vec3 origin, vec3 direction, vec3 center, float radius, out vec2 span) {
    vec3 p=origin-center;
    float b=dot(p,direction), c=dot(p,p)-radius*radius, d=b*b-c;
    span=vec2(0.0);
    if(d<0.0) return false;
    float root=sqrt(max(d,0.0));
    span=vec2(-b-root,-b+root);
    return span.y>0.0;
}
bool veInterval(vec3 origin, vec3 direction, float limit, out vec2 span) {
    span=vec2(0.0);
    if(limit<=0.0) return false;
    if(int(Layer.z+0.5)==1) {
        if(!veSphereInterval(origin,direction,Bounds.xyz,Bounds.w,span)) return false;
    } else {
        vec2 p=origin.xz-Bounds.xz;
        float a=dot(direction.xz,direction.xz), b=dot(p,direction.xz);
        float c=dot(p,p)-Bounds.w*Bounds.w;
        span=vec2(-1e10,1e10);
        if(a<1e-8) { if(c>=0.0) return false; }
        else {
            float d=b*b-a*c; if(d<0.0) return false;
            float root=sqrt(max(d,0.0)); span=vec2((-b-root)/a,(-b+root)/a);
        }
        if(abs(direction.y)<1e-7) {
            if(origin.y<=Layer.x || origin.y>=Layer.x+Layer.y) return false;
        } else {
            vec2 vertical=(vec2(Layer.x,Layer.x+Layer.y)-origin.y)/direction.y;
            span=vec2(max(span.x,min(vertical.x,vertical.y)),min(span.y,max(vertical.x,vertical.y)));
        }
    }
    span=vec2(max(span.x,0.0),min(span.y,limit));
    return span.y>span.x;
}
float veLightTransmission(vec3 point, vec3 direction, float limit) {
    if(Optics.x<=0.0 || Density.x<=0.0) return 1.0;
    vec2 span;
    if(!veInterval(point,direction,limit,span)) return 1.0;
    int steps=clamp(LightSteps,1,12);
    float lengthToExit=span.y-span.x,tau=0.0;
    for(int i=0;i<12;i++) {
        if(i>=steps) break;
        // A cubic spacing gives two fine near samples, then covers the far span.
        float a=float(i)/float(steps),b=float(i+1)/float(steps);
        float lo=a*a*a*lengthToExit,hi=b*b*b*lengthToExit;
        tau+=veDensity(point+direction*(span.x+(lo+hi)*.5))*(hi-lo)*Optics.x;
    }
    return exp(-min(tau,40.0));
}
float vePhase(float cosine) {
    float g=Optics.y;
    return min((1.0-g*g)/(12.5663706*pow(max(1.0+g*g-2.0*g*cosine,0.01),1.5)),16.0);
}
vec4 veFlashState() {
    if(Flash.x<=0.0 || Flash.w<=0.0) return vec4(Bounds.xyz,0.0);
    float clock=max(Time,0.0), cycle=floor(clock/Flash.y), local=clock-cycle*Flash.y;
    vec3 key=vec3(cycle+0.5,Seed+0.7,9.2);
    float start=(Flash.y-Flash.z)*veHash(key+vec3(2,0,0))*0.65;
    float age=(local-start)/Flash.z;
    float envelope=0.0;
    if(age>0.0 && age<1.0) {
        // Two reproducible pulses inside one finite event, independent of FPS.
        envelope=sin(3.14159265*age);
        envelope*=0.45+0.55*pow(0.5+0.5*cos(age*18.8495559),2.0);
    }
    float angle=veHash(key+vec3(0,3,0))*6.2831853;
    float radial=Bounds.w*FlashShape.y*sqrt(veHash(key+vec3(0,0,5)));
    int kind=int(Layer.z+0.5);
    if(kind==2) radial=Layer.w+(Bounds.w-Layer.w)*
                         (0.2+0.55*FlashShape.y*sqrt(veHash(key+vec3(0,0,7))));
    vec3 position=Bounds.xyz+vec3(cos(angle)*radial,0.0,sin(angle)*radial);
    position.y=kind==1?Bounds.y+(veHash(key+vec3(7,2,1))-0.5)*Bounds.w*0.6:
                       Layer.x+Layer.y*(0.25+0.40*veHash(key+vec3(7,2,1)));
    return vec4(position,envelope*Flash.w*smoothstep(.6,1.0,vortexProgress()));
}
float veLocalFalloff(float distanceToLight) {
    float r=distanceToLight/FlashShape.x;
    float edge=max(1.0-r*r,0.0);
    return edge*edge/(1.0+6.0*r*r);
}
vec3 veSurfaceLight(vec3 world, vec3 normal) {
    if(FlashShape.w<=0.0) return vec3(0.0);
    vec4 event=veFlashState();
    if(event.w<=0.0) return vec3(0.0);
    vec3 offset=event.xyz-world;
    float distanceToLight=length(offset);
    if(distanceToLight>=FlashShape.x) return vec3(0.0);
    vec3 direction=offset/max(distanceToLight,1e-5);
    float facing=max(dot(normal,direction),0.0);
    float transmission=veLightTransmission(world,direction,distanceToLight);
    // Medium attenuation only: opaque terrain shadowing needs a separate
    // shadow/depth producer and is deliberately not inferred from one depth.
    return FlashColor*(event.w*FlashShape.w*veLocalFalloff(distanceToLight)*facing*transmission);
}
vec4 veIntegrate(vec3 origin, vec3 direction, float opaqueDistance) {
    if(Density.x<=0.0) return vec4(0.0,0.0,0.0,1.0);
    vec2 span;
    if(!veInterval(origin,direction,opaqueDistance,span)) return vec4(0.0,0.0,0.0,1.0);
    int steps=clamp(ViewSteps,8,96)*2;
    float stride=(span.y-span.x)/float(steps), transmittance=1.0;
    vec3 radiance=vec3(0.0), sun=normalize(SunDirection);
    vec4 event=veFlashState();
    float solarPhase=vePhase(dot(direction,sun));
    float jitter=veHash(vec3(floor(gl_FragCoord.xy),Seed+19.5));
    for(int i=0;i<192;i++) {
        if(i>=steps || transmittance<0.001) break;
        vec3 point=origin+direction*(span.x+(float(i)+.25+jitter*.5)*stride);
        float rho=veDensity(point);
        if(int(EndWarp.x+0.5)==1 && EndDepth.w>0.0) {
            float clear=clamp(length(point-origin)/EndDepth.w,0.0,1.0);
            rho=min(rho,clear*clear*Density.x);
        }
        if(rho<=0.000001) continue;
        float sigma=rho*Optics.x;
        float alpha=1.0-exp(-min(sigma*stride,40.0));
        float weight=sigma>1e-6?alpha/sigma:stride;
        float height=int(Layer.z+0.5)==1?0.5:clamp((point.y-Layer.x)/Layer.y,0.0,1.0);
        float localSky=exp(-rho*1.15);
        vec3 source=MediumColor*(Optics.z*(.055+.17*localSky+.075*height));
        if(Optics.w>0.0) {
            float sunlight=veLightTransmission(point,sun,Bounds.w*3.0+Layer.y);
            float softScatter=.08*(1.0-exp(-rho*1.8));
            source+=MediumColor*SunColor*(Optics.w*(solarPhase*4.0*sunlight+softScatter*sqrt(sunlight)));
        }
        float baseMist=1.0-smoothstep(VortexRing.w*.65,VortexRing.w*2.0,point.y-Layer.x);
        if(VortexLife.w<.5)source*=mix(vec3(1.0),vec3(1.12,.91,.66),baseMist*.65);
        if(event.w>0.0 && FlashShape.z>0.0) {
            vec3 offset=event.xyz-point;
            float distanceToLight=length(offset);
            if(distanceToLight<FlashShape.x) {
                vec3 toward=offset/max(distanceToLight,1e-5);
                float transmission=veLightTransmission(point,toward,distanceToLight);
                source+=FlashColor*(event.w*FlashShape.z*veLocalFalloff(distanceToLight)*
                        (0.25+vePhase(dot(direction,toward)))*transmission);
            }
        }
        if(int(EndWarp.x+0.5)==2) source*=vePurpleBrightness(point);
        vec3 emission=vec3(0.0);
        if(Glow.x>0.0) {
            vec3 q=(point-Bounds.xyz)*Density.z*Glow.y;
            q+=vec3(Seed*0.023,max(Time,0.0)*Glow.w*0.17,Seed*0.017);
            float mask=smoothstep(Glow.z,min(Glow.z+0.15,1.0),veNoise(q));
            emission=EmissionColor*(rho*Glow.x*mask*0.018);
        }
        radiance+=transmittance*weight*(sigma*source+emission);
        transmittance*=1.0-alpha;
    }
    return vec4(radiance,transmittance);
}
