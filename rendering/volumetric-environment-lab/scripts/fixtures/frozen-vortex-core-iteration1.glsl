// Original finite world medium. Shared GLSL 150 / WebGL2 implementation.
// Positions use block units in one common coordinate frame. A backend may rebase
// origin, Bounds.xyz and Layer.x together on CPU for large world coordinates.
// RGB is linear radiance. veIntegrate returns (premultiplied L, transmittance T).
uniform float Time;
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
    float r=length(p), normalized=r/max(radius,1.0);
    float omega=VortexFlow.x/(.75+normalized*normalized*.25);
    float angle=-omega*Time-h*8.0;
    vec2 q=mat2(cos(angle),sin(angle),-sin(angle),cos(angle))*p;
    // Inverse map expands sampled coordinates while visible structures contract.
    // Stop compression after formation; this is bounded artistic advection.
    q*=1.0+VortexLife.y*(1.0-h)*smoothstep(0.0,VortexLife.x*2.0,Time);
    vec3 np=vec3(q.x,y-VortexFlow.y*Time,q.y)*Density.z+vec3(Seed*.013,0.0,Seed*.019);
    vec3 warp=veCurl(np*.7)*VortexFlow.w*.28;
    float n=veFbm(np+warp);
    float theta=r>0.00001?atan(p.y,p.x):0.0;
    float helix=mix(.5,.5+.5*sin(theta*4.0+h*22.0-Time*VortexFlow.x*4.0),smoothstep(0.0,1.0,r));
    float roughR=normalized+(n-.5)*VortexFlow.w*.85;
    float envelope=1.0-smoothstep(.72,1.05,roughR);
    if(VortexShape.w>0.0)envelope*=smoothstep(VortexShape.w*.35,VortexShape.w,normalized);
    float reveal=VortexLife.w>.5?smoothstep(h*.72,h*.72+.25,progress):smoothstep((1.0-h)*.72,(1.0-h)*.72+.25,progress);
    float topFade=1.0-smoothstep(fullHeight*.98,fullHeight*1.08,y);
    float body=envelope*(.08+.92*smoothstep(.30,.67,n))*(.40+.60*pow(helix,1.4))*reveal*topFade;
    float baseRadius=VortexRing.x+y*.23;
    float ring=exp(-pow((length(world.xz-Bounds.xz)-baseRadius)/max(VortexRing.y,1.0),2.0));
    ring*=exp(-pow(y/max(VortexRing.w,1.0),1.3));
    ring*=.3+.7*veFbm(vec3(q.x,y-Time*VortexFlow.y*.7,q.y)*.09+Seed*.02);
    ring*=VortexRing.z*smoothstep(.03,.35,progress);
    vec3 cp=world-vec3(Bounds.x,Layer.x+fullHeight,Bounds.z);
    float capNoise=veFbm(cp*.048+vec3(Time*.06,0,Time*.03));
    float capRadius=length(cp.xz)/Bounds.w+(capNoise-.5)*.75;
    float canopy=(1.0-smoothstep(.45,.86,capRadius))*exp(-pow(cp.y/max(Layer.y*.09,1.0),2.0));
    canopy*=VortexLife.z*(.08+.92*smoothstep(.28,.67,veFbm(vec3(q.x,cp.y,q.y)*.055)))*smoothstep(0.0,.3,progress);
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
    float stride=(span.y-span.x)/float(steps), tau=0.0;
    for(int i=0;i<12;i++) {
        if(i>=steps) break;
        tau+=veDensity(point+direction*(span.x+(float(i)+0.5)*stride))*stride*Optics.x;
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
    int steps=clamp(ViewSteps,8,96);
    float stride=(span.y-span.x)/float(steps), transmittance=1.0;
    vec3 radiance=vec3(0.0), sun=normalize(SunDirection);
    vec4 event=veFlashState();
    float solarPhase=vePhase(dot(direction,sun));
    for(int i=0;i<96;i++) {
        if(i>=steps || transmittance<0.001) break;
        vec3 point=origin+direction*(span.x+(float(i)+0.5)*stride);
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
        vec3 source=MediumColor*(Optics.z*(0.30+0.70*height));
        if(Optics.w>0.0) {
            float sunlight=veLightTransmission(point,sun,Bounds.w*3.0+Layer.y);
            source+=MediumColor*SunColor*(Optics.w*solarPhase*sunlight);
        }
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
