// Original finite world medium. Shared GLSL 150 / WebGL2 implementation.
// Positions use block units in one common coordinate frame. A backend may rebase
// origin, Bounds.xyz and Layer.x together on CPU for large world coordinates.
// RGB is linear radiance. veIntegrate returns (premultiplied L, transmittance T).
uniform float Time;
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
float veSupport(vec3 world) {
    vec3 local=world-Bounds.xyz;
    if(int(Layer.z+0.5)==1) {
        float radial=length(local)/Bounds.w;
        return 1.0-smoothstep(0.70,1.0,radial);
    }
    float h=(world.y-Layer.x)/Layer.y;
    float radius=length(local.xz);
    float edge=1.0-smoothstep(Bounds.w*0.82,Bounds.w,radius);
    float profile=smoothstep(0.0,0.15,h)*(1.0-smoothstep(0.70,1.0,h));
    if(int(Layer.z+0.5)==2) {
        float width=max(min(Layer.w*0.25,(Bounds.w-Layer.w)*0.5),1.0);
        edge*=smoothstep(Layer.w,Layer.w+width,radius);
    }
    return edge*profile;
}
float veDensity(vec3 world) {
    if(Density.x<=0.0 || Density.y<=0.0) return 0.0;
    float support=veSupport(world);
    if(support<=0.0) return 0.0;
    float clock=max(Time,0.0);
    vec3 p=(world-Bounds.xyz)*Density.z;
    p-=vec3(Flow.x,0.0,Flow.y)*clock*Flow.z*Density.z;
    p+=vec3(Seed*0.0131,Seed*0.0097,Seed*0.0173);
    p+=veCurl(p*0.65+vec3(0.0,clock*Flow.z*0.025,0.0))*Flow.w*0.42;
    float shape=veFbm(p);
    float height=int(Layer.z+0.5)==1?0.0:clamp((world.y-Layer.x)/Layer.y,0.0,1.0);
    // Weaker columns fade earlier toward the cloud top, preserving billows
    // instead of making a uniformly opaque, perfectly flat ceiling.
    float threshold=mix(0.82,0.35,Density.y)+height*height*0.18;
    float body=smoothstep(threshold-0.08,threshold+0.14,shape);
    float erosion=(1.0-veNoise(p*4.7+vec3(8.4,3.2,17.1)))*(1.0-body)*Density.w*0.7;
    return max(body-erosion,0.0)*support*Density.x;
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
    if(Optics.x<=0.0 || Density.x<=0.0 || Density.y<=0.0) return 1.0;
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
    return vec4(position,envelope*Flash.w);
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
    if(Density.x<=0.0 || Density.y<=0.0) return vec4(0.0,0.0,0.0,1.0);
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
