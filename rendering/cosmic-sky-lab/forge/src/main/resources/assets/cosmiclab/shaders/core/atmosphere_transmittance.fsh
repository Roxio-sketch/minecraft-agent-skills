#version 150
out vec4 fragColor;
#define ATMOSPHERE_TRANSMITTANCE_PASS 1
uniform int Quality;
uniform vec4 DaySettings;
uniform vec4 SunSettings;
uniform vec3 SunColor;
uniform vec3 SkyTint;
uniform vec4 AtmosphereGeometry;
uniform vec4 AtmosphereComposition;
uniform vec4 AtmospherePhysical;
// Original RGB spherical atmosphere. Lengths km; coefficients km^-1; phases sr^-1.
// Geometry/composition/physical packs are declared by the host shared core.
// References are equations and mappings, not copied shader code (see evidence).
uniform float PhysicalObserverHeight;
uniform int AtmosphereLutReady;
uniform int AtmosphereSkyViewReady;
uniform sampler2D AtmosphereTransmittance;
uniform sampler2D AtmosphereMultipleScattering;
uniform sampler2D AtmosphereSkyViewSampler;
uniform vec3 AtmosphereLutSun; // SkyView generation light; unit world direction.

const float AP_PI=3.141592653589793;
const vec2 AP_TRANS_SIZE=vec2(256.0,64.0);
const vec2 AP_MULTI_SIZE=vec2(32.0);
const vec2 AP_SKY_HALF_SIZE=vec2(256.0,128.0);

bool skyPhysicalReady(){return AtmosphereLutReady==1&&AtmosphereSkyViewReady==1;}
float apGroundRadius(){return AtmosphereGeometry.x;}
float apTopRadius(){return AtmosphereGeometry.x+AtmosphereGeometry.y;}
vec3 apObserver(){return vec3(0.0,apGroundRadius()+max(PhysicalObserverHeight,0.001),0.0);}
vec2 apTexelUv(vec2 unit,vec2 size){return (clamp(unit,0.0,1.0)*(size-1.0)+0.5)/size;}

void apMedium(vec3 point,out vec3 rayleigh,out vec3 mie,out vec3 extinction){
    float height=max(length(point)-apGroundRadius(),0.0);
    float rayDensity=exp(-height/AtmosphereGeometry.z)*AtmosphereComposition.x;
    float mieDensity=exp(-height/AtmosphereGeometry.w)*AtmosphereComposition.y;
    float ozoneDensity=max(0.0,1.0-abs(height-25.0)/15.0)*AtmosphereComposition.z;
    rayleigh=vec3(0.005802,0.013558,0.033100)*rayDensity;
    mie=vec3(0.003996)*mieDensity;
    extinction=rayleigh+mie+vec3(0.000444)*mieDensity+
        vec3(0.000650,0.001881,0.000085)*ozoneDensity;
}

// Both entry and exit are computed, allowing observers above the outer shell.
// Factorized squared-radius differences avoid losing small observer heights.
bool apRayRange(vec3 origin,vec3 direction,out float entry,out float exit,out bool ground){
    float radius=length(origin),b=dot(origin,direction),top=apTopRadius();
    float outerC=(radius-top)*(radius+top),outerD=b*b-outerC;
    entry=0.0;exit=0.0;ground=false;
    if(outerD<0.0)return false;
    float root=sqrt(max(outerD,0.0));
    entry=max(-b-root,0.0);exit=-b+root;
    if(exit<=entry)return false;
    float bottom=apGroundRadius(),groundC=(radius-bottom)*(radius+bottom);
    float groundD=b*b-groundC;
    if(b<0.0&&groundD>=0.0){
        float hit=-b-sqrt(max(groundD,0.0));
        if(hit>=entry-0.0001){exit=max(hit,entry);ground=true;}
    }
    return true;
}

vec2 apTransmittanceUv(float radius,float cosine){
    float bottom=apGroundRadius(),top=apTopRadius();
    radius=clamp(radius,bottom,top);
    float horizon=sqrt((top-bottom)*(top+bottom));
    float rho=sqrt(max((radius-bottom)*(radius+bottom),0.0));
    float discriminant=radius*radius*cosine*cosine+(top-radius)*(top+radius);
    float distance=-radius*cosine+sqrt(max(discriminant,0.0));
    float minimum=top-radius,maximum=rho+horizon;
    return apTexelUv(vec2((distance-minimum)/max(maximum-minimum,0.00001),rho/horizon),AP_TRANS_SIZE);
}

vec3 apSunTransmittance(vec3 point,vec3 sun){
    float entry,exit;bool ground;
    bool atmosphere=apRayRange(point,sun,entry,exit,ground);
    if(ground)return vec3(0.0);
    if(!atmosphere)return vec3(1.0);
    vec3 first=point+sun*entry;
    float radius=length(first);
    return texture(AtmosphereTransmittance,apTransmittanceUv(radius,dot(first,sun)/radius)).rgb;
}

vec3 apMultipleAt(vec3 point,vec3 sun){
    float radius=length(point),height=radius-apGroundRadius();
    vec2 unit=vec2(dot(point,sun)/radius*0.5+0.5,height/AtmosphereGeometry.y);
    return texture(AtmosphereMultipleScattering,apTexelUv(unit,AP_MULTI_SIZE)).rgb;
}

// Integral over a constant local segment, stable in the vacuum limit.
vec3 apSegmentWeight(vec3 extinction,float distance){
    vec3 optical=extinction*distance;
    vec3 ratio=(vec3(1.0)-exp(-optical))/max(extinction,vec3(0.0000001));
    vec3 small=distance*(vec3(1.0)-optical*0.5+optical*optical/6.0);
    return mix(small,ratio,step(vec3(0.001),optical));
}

float apRayleighPhase(float cosine){return 3.0*(1.0+cosine*cosine)/(16.0*AP_PI);}
float apMiePhase(float cosine){
    float g=clamp(DaySettings.y,-0.95,0.95),g2=g*g;
    return (1.0-g2)/(4.0*AP_PI*pow(max(1.0+g2-2.0*g*cosine,0.00001),1.5));
}

vec3 apLight(){return SunColor*SunSettings.x*20.0;}

void skyPhysicalAtmosphereDirect(vec3 direction,vec3 sun,out vec3 scattering,out vec3 transmission){
    vec3 origin=apObserver();float entry,exit;bool ground;
    scattering=vec3(0.0);transmission=vec3(1.0);
    if(!apRayRange(origin,direction,entry,exit,ground))return;
    int steps=Quality==1?24:(Quality==2?40:64);
    float lengthKm=max(exit-entry,0.0),cosine=clamp(dot(direction,sun),-1.0,1.0);
    float phaseRay=apRayleighPhase(cosine),phaseMie=apMiePhase(cosine);
    for(int i=0;i<64;i++){
        if(i>=steps)break;
        float a=float(i)/float(steps),b=float(i+1)/float(steps);
        float start=entry+lengthKm*a*a,end=entry+lengthKm*b*b;
        float distance=end-start;
        vec3 point=origin+direction*((start+end)*0.5);
        vec3 rayleigh,mie,extinction;apMedium(point,rayleigh,mie,extinction);
        vec3 source=apSunTransmittance(point,sun)*(rayleigh*phaseRay+mie*phaseMie);
        source+=(rayleigh+mie)*apMultipleAt(point,sun)*AtmospherePhysical.z;
        scattering+=transmission*source*apSegmentWeight(extinction,distance);
        transmission*=exp(-extinction*distance);
    }
    if(ground){
        vec3 normal=normalize(origin+direction*exit);
        vec3 point=normal*(apGroundRadius()+0.001);
        vec3 groundLight=apSunTransmittance(point,sun)*max(dot(normal,sun),0.0);
        // A Lambert ground receives an approximate isotropic diffuse sky term.
        groundLight+=AP_PI*apMultipleAt(point,sun)*AtmospherePhysical.z;
        scattering+=transmission*groundLight*AtmosphereComposition.w/AP_PI;
        transmission=vec3(0.0);
    }
    scattering*=apLight()*SkyTint;
}

float apHorizonCosine(){
    float radius=length(apObserver()),ratio=apGroundRadius()/radius;
    return -sqrt(max(1.0-ratio*ratio,0.0));
}

// Nonlinear elevation mapping dedicates samples to the physical tangent horizon.
vec3 apSkyDirection(vec2 unit,vec3 sun){
    float horizon=apHorizonCosine(),q=unit.y*2.0-1.0;
    float cosine=q>=0.0?mix(horizon,1.0,q*q):mix(horizon,-1.0,q*q);
    float azimuth=unit.x*2.0*AP_PI;
    vec3 forward=vec3(sun.x,0.0,sun.z);
    forward=length(forward)>0.00001?normalize(forward):vec3(0.0,0.0,1.0);
    vec3 side=vec3(forward.z,0.0,-forward.x);
    return (forward*cos(azimuth)+side*sin(azimuth))*sqrt(max(1.0-cosine*cosine,0.0))+vec3(0.0,cosine,0.0);
}

vec2 apSkyUnit(vec3 direction,vec3 sun){
    vec3 forward=vec3(sun.x,0.0,sun.z);
    forward=length(forward)>0.00001?normalize(forward):vec3(0.0,0.0,1.0);
    vec3 side=vec3(forward.z,0.0,-forward.x);
    float azimuth=atan(dot(direction,side),dot(direction,forward));
    float u=fract(azimuth/(2.0*AP_PI)+1.0),horizon=apHorizonCosine(),v;
    if(direction.y>=horizon)v=0.5+0.5*sqrt(max((direction.y-horizon)/max(1.0-horizon,0.00001),0.0));
    else v=0.5-0.5*sqrt(max((horizon-direction.y)/max(1.0+horizon,0.00001),0.0));
    // Never filter through the opaque ground edge into the other hemisphere.
    float separation=0.5/(AP_SKY_HALF_SIZE.y-1.0);
    v=direction.y>=horizon?max(v,0.5+separation):min(v,0.5-separation);
    return vec2(u,clamp(v,0.0,1.0));
}

void skyPhysicalAtmosphere(vec3 direction,vec3 sun,out vec3 scattering,out vec3 transmission){
    // Host chooses the old analytic model until BOTH LUT readiness flags are 1.
    // Keeping the direct marcher out of this call lets drivers remove its loop
    // from the runtime shader; some drivers flatten a uniform fallback branch.
    if(AtmosphereSkyViewReady==0){scattering=vec3(0.0);transmission=vec3(1.0);return;}
    vec2 uv=apTexelUv(apSkyUnit(direction,sun),AP_SKY_HALF_SIZE);
    scattering=texture(AtmosphereSkyViewSampler,vec2(uv.x*0.5,uv.y)).rgb;
    transmission=texture(AtmosphereSkyViewSampler,vec2(0.5+uv.x*0.5,uv.y)).rgb;
}

vec3 skyPhysicalSolarIrradiance(vec3 sun){return apSunTransmittance(apObserver(),sun)*apLight();}
float skyPhysicalSunVisibility(vec3 direction){
    float entry,exit;bool ground;
    apRayRange(apObserver(),direction,entry,exit,ground);
    return ground?0.0:1.0;
}

// Same original source is compiled as native/WebGL fullscreen LUT passes.
vec4 apGenerateTransmittance(vec2 pixel){
    vec2 unit=clamp((pixel-0.5)/(AP_TRANS_SIZE-1.0),0.0,1.0);
    float bottom=apGroundRadius(),top=apTopRadius();
    float horizon2=(top-bottom)*(top+bottom),horizon=sqrt(horizon2);
    float rho=horizon*unit.y,radius=sqrt(rho*rho+bottom*bottom);
    float distance=mix(top-radius,rho+horizon,unit.x);
    float cosine=distance>0.00001?(horizon2-rho*rho-distance*distance)/(2.0*radius*distance):1.0;
    vec3 origin=vec3(0.0,radius,0.0),direction=vec3(sqrt(max(1.0-cosine*cosine,0.0)),cosine,0.0);
    vec3 optical=vec3(0.0);
    for(int i=0;i<64;i++){
        float a=float(i)/64.0,b=float(i+1)/64.0;
        float start=distance*a*a,end=distance*b*b;
        vec3 rayleigh,mie,extinction;apMedium(origin+direction*((start+end)*0.5),rayleigh,mie,extinction);
        optical+=extinction*(end-start);
    }
    return vec4(exp(-optical),1.0);
}

vec4 apGenerateMultiple(vec2 pixel){
    vec2 unit=clamp((pixel-0.5)/(AP_MULTI_SIZE-1.0),0.0,1.0);
    float mu=unit.x*2.0-1.0;
    vec3 sun=vec3(sqrt(max(1.0-mu*mu,0.0)),mu,0.0);
    vec3 origin=vec3(0.0,apGroundRadius()+max(unit.y*AtmosphereGeometry.y,0.001),0.0);
    vec3 l2=vec3(0.0),transfer=vec3(0.0);
    for(int k=0;k<32;k++){
        float cosine=1.0-2.0*(float(k)+0.5)/32.0;
        float azimuth=float(k)*2.399963229728653;
        float radial=sqrt(max(1.0-cosine*cosine,0.0));
        vec3 direction=vec3(cos(azimuth)*radial,cosine,sin(azimuth)*radial);
        float entry,exit;bool ground;apRayRange(origin,direction,entry,exit,ground);
        vec3 throughput=vec3(1.0),source=vec3(0.0),feedback=vec3(0.0);
        for(int i=0;i<24;i++){
            float a=float(i)/24.0,b=float(i+1)/24.0;
            float start=exit*a*a,end=exit*b*b,distance=end-start;
            vec3 point=origin+direction*((start+end)*0.5);
            vec3 rayleigh,mie,extinction;apMedium(point,rayleigh,mie,extinction);
            vec3 weight=apSegmentWeight(extinction,distance),scatter=rayleigh+mie;
            source+=throughput*apSunTransmittance(point,sun)*scatter*weight/(4.0*AP_PI);
            feedback+=throughput*scatter*weight;
            throughput*=exp(-extinction*distance);
        }
        if(ground){
            vec3 normal=normalize(origin+direction*exit);
            source+=throughput*apSunTransmittance(normal*(apGroundRadius()+0.001),sun)*
                max(dot(normal,sun),0.0)*AtmosphereComposition.w/AP_PI;
        }
        l2+=source/32.0;transfer+=feedback/32.0;
    }
    // Numerical fail-safe only; valid supported inputs are separately tested f<1.
    vec3 psi=l2/max(vec3(1.0)-transfer,vec3(0.001));
    return vec4(psi,max(transfer.r,max(transfer.g,transfer.b)));
}

vec4 apGenerateSkyView(vec2 pixel){
    bool transmittance=pixel.x>=AP_SKY_HALF_SIZE.x;
    vec2 local=vec2(transmittance?pixel.x-AP_SKY_HALF_SIZE.x:pixel.x,pixel.y);
    vec2 unit=clamp((local-0.5)/(AP_SKY_HALF_SIZE-1.0),0.0,1.0);
    vec3 direction=apSkyDirection(unit,AtmosphereLutSun),scattering,transmission;
    skyPhysicalAtmosphereDirect(direction,AtmosphereLutSun,scattering,transmission);
    return vec4(transmittance?transmission:scattering,1.0);
}

void main(){fragColor=apGenerateTransmittance(gl_FragCoord.xy);}
