#version 150
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec3 InverseViewTranslation;
uniform int OutputDeltaEncoding;
uniform vec3 CameraPosition;
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
in vec2 screenUV;
out vec4 fragColor;
uniform vec4 GiantBodySettings;
void skyGiantCelestial(vec3 d,inout vec3 color);
uniform vec4 AtmosphereGeometry;
uniform vec4 AtmosphereComposition;
uniform vec4 AtmospherePhysical;
uniform vec4 SnowSettings;
uniform vec4 PrecipitationSettings;
uniform vec3 PipelineSettings;
uniform vec3 SnowColor;
uniform int EnvironmentStage;
uniform vec3 WorldPrecipOrigin;
uniform vec2 WorldPrecipPresence;
uniform vec4 WorldPrecipGeometry;
uniform vec4 WorldSurfaceGrid;
uniform sampler2D WeatherVolumeSampler;
uniform sampler2D SurfaceSampler;
void skyPhysicalAtmosphere(vec3 d,vec3 sun,out vec3 scattering,out vec3 transmission);
bool skyPhysicalReady();
vec3 skyPhysicalSolarIrradiance(vec3 sun);
float skyPhysicalSunVisibility(vec3 direction);
vec3 skyWorldPrecipitation(vec3 scene,vec3 origin,vec3 direction,float distanceLimit,vec3 sun);
vec3 skyWorldRain(vec3 scene,vec3 origin,vec3 direction,float distanceLimit);
// Original direction-space sky. Shared by WebGL2 and Forge GLSL 150.
// Original generated LUTs and world data; no bitmap sky or third-party source.
uniform int Quality;
uniform float Time;
uniform float Seed;
uniform vec4 StarSettings;   // density, brightness, size, twinkle amount
uniform vec4 MotionSettings; // twinkle, nebula, aurora, meteor speeds
uniform vec4 GalaxySettings; // yaw radians, tilt radians, band width, brightness
uniform vec4 CloudSettings;  // nebula brightness, scale, dark dust, exposure
uniform vec4 AuroraSettings; // brightness, height, vertical extent, curtains
uniform vec4 MeteorSettings; // event coverage (0..8), brightness, tail radians, horizon
uniform vec4 BodySettings;   // brightness, angular radius radians, yaw, elevation
uniform vec4 BodySurface;    // surface type 1/2/3, lighting phase radians, detail, rotation speed
uniform vec3 RingSettings;   // brightness, inclination radians, outer radius multiplier
uniform vec4 BlackHoleSettings; // brightness, nominal shadow radius radians, yaw, elevation
uniform vec4 DiskSettings;   // inclination radians, outer radius/horizon, speed, deflection
uniform vec3 BackgroundColor;
uniform vec3 StarCoolColor;
uniform vec3 StarWarmColor;
uniform vec3 GalaxyColor;
uniform vec3 NebulaColorA;
uniform vec3 NebulaColorB;
uniform vec3 AuroraColorA;
uniform vec3 AuroraColorB;
uniform vec3 BodyColorA;
uniform vec3 BodyColorB;
uniform vec3 RingColor;
uniform vec3 DiskColorA;
uniform vec3 DiskColorB;
uniform vec4 AtmosphereSettings; // blend, turbidity, Rayleigh strength, Mie strength
uniform vec4 SunSettings;        // brightness, elevation radians, yaw radians, angular radius
uniform vec4 DaySettings;        // cycle speed, Mie anisotropy, night ambient, twilight strength
uniform vec4 MoonSettings;       // brightness, yaw radians, elevation radians, angular radius
uniform vec4 MoonSurface;        // phase radians (0=full), detail, halo, surface rotation speed
uniform vec4 ThinCloudSettings;  // strength, coverage, scale, plane height
uniform vec4 ThinCloudMotion;    // wind speed, wind yaw radians, evolution, silver lining
uniform vec3 SkyTint;
uniform vec3 SunColor;
uniform vec3 MoonColor;
uniform vec3 ThinCloudColor;

uniform vec4 WeatherSettings;
uniform vec4 RainSettings;
uniform vec4 WeatherMotion;
uniform vec4 FogSettings;
uniform vec4 FogMotion;
uniform vec4 VolumeCloudSettings;
uniform vec4 VolumeCloudGeometry;
uniform vec4 VolumeCloudMotion;
uniform vec4 CloudLighting;
uniform vec4 LightingDetail;
uniform vec4 RainbowSettings;
uniform vec4 HaloSettings;
uniform vec4 SpecialSettings;
uniform vec4 SpecialShape;
uniform vec4 BlackHoleAppearance;
uniform vec4 BlackHoleCinematic;
uniform vec3 FogColor;
uniform vec3 VolumeCloudColor;
uniform vec3 RainColor;
uniform vec3 LightningColor;
uniform vec3 SpecialColorA;
uniform vec3 SpecialColorB;
uniform vec2 WorldWeather; // actual/simulated rain and thunder

void skyExtraEnvironment(vec3 direction, inout vec3 color);
void skyBlackHoleCinematic(vec3 direction, inout vec3 color);
vec3 skyBlackHoleCinematicBackground(vec3 direction);
vec2 skyWeatherAmounts();

const float SKY_PI = 3.14159265359;
vec3 skyHash(vec3 p) {
    // Integer lattice hash is stable within a recipe, across time and translation.
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float skyNoise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(skyHash(i).x,skyHash(i+vec3(1,0,0)).x,f.x),
                   mix(skyHash(i+vec3(0,1,0)).x,skyHash(i+vec3(1,1,0)).x,f.x),f.y),
               mix(mix(skyHash(i+vec3(0,0,1)).x,skyHash(i+vec3(1,0,1)).x,f.x),
                   mix(skyHash(i+vec3(0,1,1)).x,skyHash(i+vec3(1,1,1)).x,f.x),f.y),f.z);
}
float skyFbm(vec3 p, int octaves) {
    float result=0.0, amplitude=0.5;
    for(int i=0;i<6;i++) {
        if(i>=octaves) break;
        result+=amplitude*skyNoise(p);
        p=vec3(0.8*p.x-0.6*p.z,p.y,0.6*p.x+0.8*p.z)*2.03+vec3(7.7,19.1,13.3);
        amplitude*=0.5;
    }
    return result;
}
vec3 galaxyDirection(vec3 d) {
    float cy=cos(GalaxySettings.x),sy=sin(GalaxySettings.x);
    float ct=cos(GalaxySettings.y),st=sin(GalaxySettings.y);
    d=vec3(cy*d.x+sy*d.z,d.y,-sy*d.x+cy*d.z);
    return vec3(ct*d.x-st*d.y,st*d.x+ct*d.y,d.z);
}
vec3 skyStars(vec3 d, float frequency, float layer, float clustering) {
    vec3 p=d*frequency, cell=floor(p);
    float footprint=max(length(dFdx(p)),length(dFdy(p)));
    vec3 result=vec3(0.0);
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
        vec3 id=cell+vec3(x,y,z);
        vec3 r=skyHash(id+vec3(layer,Seed*0.011,Seed*0.017));
        float select=skyHash(id+vec3(173.1,Seed*0.021,layer)).y;
        float coverage=clamp(StarSettings.x*(0.68+0.65*clustering),0.0,1.0);
        float exists=1.0-step(coverage,select);
        vec3 center=id+0.12+r*0.76;
        float radius=(0.028+0.085*r.z*r.z)*StarSettings.z;
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
        float beat=0.5+0.5*sin(Time*MotionSettings.x*(1.1+2.0*r.x)+r.y*35.0);
        float twinkle=mix(1.0,0.18+1.82*beat*beat,StarSettings.w);
        float bright=2.0+7.0*pow(r.y,7.0);
        vec3 tint=mix(StarCoolColor,StarWarmColor,r.x);
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
    return result*StarSettings.y;
}
vec3 skyGalaxy(vec3 d, out float clustering) {
    vec3 g=galaxyDirection(d);
    float band=exp(-pow(g.y/max(GalaxySettings.z,0.04),2.0));
    vec3 offset=vec3(Seed*0.0011,Seed*0.0017,Seed*0.0023);
    float grains=skyFbm(g*5.0+offset,Quality+3);
    float knots=skyFbm(g*12.0+offset+17.0,Quality+2);
    float dense=band*smoothstep(0.20,0.72,grains);
    float core=pow(max(g.z,0.0),12.0)*band;
    float ridge=1.0-abs(2.0*knots-1.0);
    float dark=pow(ridge,3.0)*exp(-pow((g.y+0.02)/(GalaxySettings.z*0.48),2.0));
    float transmission=exp(-dark*CloudSettings.z*3.2);
    clustering=band*(0.4+0.6*grains)*transmission;
    vec3 light=GalaxyColor*(dense*0.38+core*0.24)*(0.55+0.7*knots);
    light+=mix(NebulaColorA,NebulaColorB,grains)*dense*dense*0.20;
    return light*GalaxySettings.w*transmission;
}
vec3 skyNebula(vec3 d) {
    vec3 origin=vec3(Seed*0.0013,Seed*0.0009,Seed*0.0019);
    // Both drift and evolving warp are perceptible over a few seconds at default speed.
    float flow=Time*MotionSettings.y*0.15;
    vec3 p=d*CloudSettings.y+origin;
    vec3 warp=vec3(skyFbm(p+vec3(flow,0,0),Quality+2),
                   skyFbm(p+vec3(9.1,flow*0.7,3.7),Quality+2),
                   skyFbm(p+vec3(4.3,8.1,-flow*0.6),Quality+2));
    float dust=skyFbm(p*1.65+warp*2.1+vec3(flow*0.3,-flow*0.15,0),Quality+3);
    float envelope=skyFbm(d*2.2+origin+vec3(17.2,1.8,6.3),3);
    float density=smoothstep(0.34,0.70,dust)*smoothstep(0.30,0.67,envelope);
    float filaments=pow(max(1.0-abs(dust*2.0-1.0),0.0),9.0);
    vec3 tint=mix(NebulaColorA,NebulaColorB,smoothstep(0.30,0.65,warp.y));
    return (tint*density*0.6+tint*filaments*density*0.18)*CloudSettings.x;
}
vec3 skyAurora(vec3 d) {
    // Angular decorative curtain layers, not a world-space volume integral.
    if(AuroraSettings.x<=0.0) return vec3(0);
    vec3 color=vec3(0);
    float flow=Time*MotionSettings.z*0.09;
    float altitude=clamp((d.y-AuroraSettings.y)/AuroraSettings.z*0.5+0.5,0.0,1.0);
    for(int i=0;i<8;i++) {
        if(i>=2+Quality*2) break;
        float layer=float(i);
        vec3 p=vec3(d.x*4.0,d.z*4.0,layer*0.32+Seed*0.001)*AuroraSettings.w;
        float n=skyNoise(p+vec3(flow,flow*0.6,0));
        float wave=0.08*sin(d.x*5.0+d.z*3.0+flow+layer*0.25)+0.12*(n-0.5);
        float edge=AuroraSettings.y+wave+layer*0.014;
        float h=(d.y-edge)/max(AuroraSettings.z,0.02);
        float vertical=smoothstep(-0.08,0.07,h)*exp(-max(h,0.0)*3.0);
        float curtain=pow(max(1.0-abs(sin((n*6.0+d.x*0.3+d.z*0.25)*SKY_PI)),0.0),5.0);
        float fringe=pow(skyNoise(p*vec3(4.0,4.0,0.3)+vec3(flow*0.3,0,1)),3.0);
        color+=mix(AuroraColorA,AuroraColorB,altitude)*vertical*curtain*(0.4+fringe)*0.20;
    }
    return color*AuroraSettings.x*smoothstep(0.02,0.16,d.y)*(1.0-smoothstep(0.9,1.0,d.y));
}
vec3 skyMeteors(vec3 d) {
    vec3 color=vec3(0);
    if(MeteorSettings.x<=0.0 || MeteorSettings.y<=0.0) return color;
    float clock=Time*MotionSettings.w*0.24;
    for(int i=0;i<8;i++) {
        if(i>=2+Quality*2) break;
        float channel=float(i),phase=fract(clock+channel*0.137);
        float epoch=floor(clock+channel*0.137);
        vec3 r=skyHash(vec3(channel*13.7+5.0,epoch+9.0,Seed*0.017));
        float eventWeight=1.0-step(MeteorSettings.x/8.0,r.z);
        float longitude=(r.x*2.0-1.0)*SKY_PI;
        float elevation=0.15+r.y*0.68;
        vec3 base=normalize(vec3(sin(longitude),elevation,cos(longitude)));
        vec3 tangent=normalize(cross(base,vec3(0,1,0)));
        tangent=normalize(tangent+vec3(0,-0.45,0));
        vec3 normal=normalize(cross(base,tangent));
        tangent=cross(normal,base);
        float angle=atan(dot(d,tangent),dot(d,base));
        float across=asin(clamp(dot(d,normal),-1.0,1.0));
        float head=-0.34+phase*0.68,behind=head-angle;
        float width=max(0.0008,fwidth(across)*0.8);
        float trail=exp(-across*across/(width*width))*
                    smoothstep(-0.002,0.004,behind)*(1.0-smoothstep(0.0,MeteorSettings.z,behind));
        float point=exp(-(across*across+(angle-head)*(angle-head))/(width*width*2.0));
        float life=smoothstep(0.04,0.15,phase)*(1.0-smoothstep(0.65,0.95,phase));
        color+=mix(StarCoolColor,StarWarmColor,r.y)*
               (trail*0.65+point*2.4)*life*eventWeight*smoothstep(0.0,0.2,dot(d,base));
    }
    return color*MeteorSettings.y;
}
float skyCrater(vec3 n, float scale) {
    vec3 p=n*scale,cell=floor(p);
    float profile=0.0;
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
        vec3 id=cell+vec3(x,y,z),r=skyHash(id+Seed*0.013);
        float radius=0.18+0.22*r.z;
        float distanceTo=length(p-(id+0.15+r*0.7));
        float bowl=1.0-smoothstep(radius*0.62,radius,distanceTo);
        float rim=exp(-pow((distanceTo-radius)/(radius*0.13),2.0));
        profile+=rim*0.38-bowl*0.45;
    }
    return profile;
}
vec3 skyBodyTexture(vec3 normal, vec3 side, vec3 up, vec3 front) {
    vec3 n=vec3(dot(normal,side),dot(normal,up),dot(normal,front));
    float spin=Time*BodySurface.w*0.18,c=cos(spin),s=sin(spin);
    n=vec3(c*n.x-s*n.z,n.y,s*n.x+c*n.z);
    vec3 origin=vec3(Seed*0.001,Seed*0.0017,Seed*0.0008);
    float grain=skyFbm(n*BodySurface.z+origin,Quality+3);
    if(BodySurface.x<1.5) {
        float turbulence=skyFbm(n*BodySurface.z*2.2+origin+8.0,Quality+2);
        float bands=0.5+0.5*sin(n.y*(18.0+BodySurface.z*3.0)+grain*9.0+turbulence*3.0);
        return mix(BodyColorA,BodyColorB,smoothstep(0.12,0.85,bands))*(0.65+0.55*turbulence);
    }
    if(BodySurface.x<2.5) {
        float craters=skyCrater(n,BodySurface.z*3.0);
        return mix(BodyColorA,BodyColorB,smoothstep(0.2,0.75,grain))*clamp(0.7+grain*0.45+craters,0.12,1.5);
    }
    float cells=pow(max(grain,0.0),1.6);
    return mix(BodyColorA,BodyColorB,smoothstep(0.22,0.66,grain))*(1.2+cells*2.5);
}
void skyCelestial(vec3 d, inout vec3 background) {
    if(GiantBodySettings.x>0.5) { skyGiantCelestial(d,background); return; }
    if(BodySettings.x<=0.0) return;
    vec3 front=vec3(sin(BodySettings.z)*cos(BodySettings.w),sin(BodySettings.w),cos(BodySettings.z)*cos(BodySettings.w));
    vec3 side=normalize(cross(vec3(0,1,0),front)),up=cross(front,side);
    float radius=sin(BodySettings.y),closest=dot(d,front);
    float discriminant=closest*closest-1.0+radius*radius;
    float angle=acos(clamp(closest,-1.0,1.0));
    float pixel=max(fwidth(angle),0.00015);
    float opacity=(1.0-smoothstep(BodySettings.y-pixel,BodySettings.y+pixel,angle))*step(0.0,closest);
    float bodyDistance=closest-sqrt(max(discriminant,0.0));
    vec3 normal=normalize(d*bodyDistance-front);
    vec3 lighting=normalize(-front*cos(BodySurface.y)+side*sin(BodySurface.y)+up*0.18);
    vec3 bodyColor=vec3(0);
    if(opacity>0.0) {
        vec3 surface=skyBodyTexture(normal,side,up,front);
        float lambert=max(dot(normal,lighting),0.0);
        float rim=pow(1.0-max(dot(normal,-d),0.0),3.0);
        if(BodySurface.x<2.5) bodyColor=surface*(0.025+1.25*lambert)+BodyColorA*rim*0.12*sqrt(lambert);
        else bodyColor=surface;
    }
    // A real plane intersection puts the near and far sides of the ring around the sphere.
    float ringOpacity=0.0,ringDistance=1e6;
    vec3 ringColor=vec3(0);
    if(RingSettings.x>0.0 && BodySurface.x<2.5) {
        vec3 ringNormal=front*cos(RingSettings.y)+up*sin(RingSettings.y);
        float denominator=dot(d,ringNormal);
        // Plane intersections diverge near parallel rays. Evaluate derivatives
        // on a bounded radial field before any per-pixel branch; otherwise a
        // giant AA footprint turns the infinite plane into a dotted horizon.
        float safeDenominator=(denominator>=0.0?1.0:-1.0)*max(abs(denominator),0.0001);
        ringDistance=dot(front,ringNormal)/safeDenominator;
        vec3 ringPoint=d*ringDistance-front;
        float ringRadius=length(ringPoint)/radius;
        float boundedRadius=min(ringRadius,RingSettings.z+0.16);
        float aa=clamp(fwidth(boundedRadius),0.001,0.08);
        bool finiteRing=abs(denominator)>0.0001 && ringDistance>0.0 &&
                        ringRadius>=1.14-aa && ringRadius<=RingSettings.z+aa;
        if(finiteRing) {
            float bounds=smoothstep(1.14-aa,1.14+aa,ringRadius)*(1.0-smoothstep(RingSettings.z-aa,RingSettings.z+aa,ringRadius));
            float stripes=0.55+0.45*sin(ringRadius*91.0+skyNoise(ringPoint*8.0+Seed*0.003)*4.0);
            float gap=1.0-0.8*exp(-pow((ringRadius-1.48)/0.032,2.0));
            ringOpacity=clamp(bounds*gap*(0.36+0.46*stripes)*RingSettings.x,0.0,0.96);
            // Shadow ray from the ring toward the same sphere light.
            float toward=dot(ringPoint,lighting),shadowDisc=toward*toward-dot(ringPoint,ringPoint)+radius*radius;
            float shadow=(shadowDisc>0.0 && -toward-sqrt(max(shadowDisc,0.0))>0.0)?0.15:1.0;
            ringColor=RingColor*(0.28+0.9*stripes)*BodySettings.x*shadow;
        }
    }
    if(ringDistance>bodyDistance) background=mix(background,ringColor,ringOpacity);
    background=mix(background,bodyColor*BodySettings.x,opacity);
    if(ringDistance<=bodyDistance) background=mix(background,ringColor,ringOpacity);
    // Angular atmosphere/corona glow, deliberately separate from scene lighting or bloom.
    float width=BodySurface.x>2.5?0.04:0.012;
    float halo=exp(-max(angle-BodySettings.y,0.0)/width)*smoothstep(BodySettings.y-pixel,BodySettings.y+pixel,angle);
    float lightEdge=BodySurface.x>2.5?1.0:max(dot(normal,lighting),0.0);
    background+=mix(BodyColorA,BodyColorB,0.35)*halo*lightEdge*BodySettings.x*(BodySurface.x>2.5?0.65:0.20);
}
vec3 skyEnvironment(vec3 direction) {
    float clustering;
    vec3 color=BackgroundColor+skyGalaxy(direction,clustering);
    if(CloudSettings.x>0.0) color+=skyNebula(direction);
    if(StarSettings.x>0.0 && StarSettings.y>0.0) {
        color+=skyStars(direction,55.0,11.0,clustering);
        if(Quality>=2) color+=skyStars(direction,145.0,97.0,clustering)*0.72;
        if(Quality>=3) color+=skyStars(direction,300.0,231.0,clustering)*0.48;
    }
    color+=skyAurora(direction)+skyMeteors(direction);
    color+=GalaxyColor*MeteorSettings.w*exp(-abs(direction.y)*14.0)*0.10;
    skyCelestial(direction,color);
    return color;
}
vec3 skyAccretion(vec3 point, vec3 diskX, vec3 diskY, float horizon) {
    vec2 uv=vec2(dot(point,diskX),dot(point,diskY))/horizon;
    float radial=length(uv),angle=atan(uv.y,uv.x);
    float inner=2.6,outer=DiskSettings.y;
    float edges=smoothstep(inner,inner+0.45,radial)*(1.0-smoothstep(outer-0.65,outer,radial));
    float clock=Time*DiskSettings.z;
    float rotation=clock*2.8/pow(max(radial,1.0),1.3),c=cos(rotation),s=sin(rotation);
    vec2 moving=vec2(c*uv.x-s*uv.y,s*uv.x+c*uv.y);
    // Noise is sampled in periodic Cartesian coordinates; no longitude seam.
    float gas=skyFbm(vec3(moving*1.8,Seed*0.009+clock*0.13),Quality+2);
    float spiral=0.5+0.5*sin(angle*9.0+radial*12.0+clock*1.7);
    float filaments=0.45+0.55*pow(spiral,3.0);
    float temperature=pow(clamp((outer-radial)/(outer-inner),0.0,1.0),0.7);
    float beaming=0.6+0.7*clamp(moving.x/max(radial,0.01)*0.5+0.5,0.0,1.0);
    return mix(DiskColorA,DiskColorB,temperature)*edges*(0.32+gas*1.9)*filaments*beaming*(0.8+temperature*2.0);
}
void skyBlackHole(vec3 direction, inout vec3 color) {
    if(BlackHoleAppearance.x>=1.5) {skyBlackHoleCinematic(direction,color);return;}
    if(BlackHoleSettings.x<=0.0) return;
    vec3 center=vec3(sin(BlackHoleSettings.z)*cos(BlackHoleSettings.w),sin(BlackHoleSettings.w),cos(BlackHoleSettings.z)*cos(BlackHoleSettings.w));
    float alignment=dot(direction,center);
    float horizon=sin(BlackHoleSettings.y)/2.6;
    float influence=atan(horizon*(DiskSettings.y+3.0)*1.6);
    float separation=acos(clamp(alignment,-1.0,1.0));
    if(separation>influence || alignment<=0.0) return;
    vec3 side=normalize(cross(vec3(0,1,0),center)),up=cross(center,side);
    vec3 diskNormal=center*cos(DiskSettings.x)+up*sin(DiskSettings.x);
    vec3 diskX=side,diskY=normalize(cross(diskNormal,diskX));
    vec3 position=-center,velocity=direction,emission=vec3(0);
    float transmission=1.0,closestRadius=1.0;
    bool captured=false,escaped=false;
    // Finite midpoint curvature integration is an artistic lens approximation,
    // not a Schwarzschild/Kerr geodesic or scientific event-horizon model.
    for(int i=0;i<88;i++) {
        if(i>=40+Quality*16) break;
        float radius=length(position);
        closestRadius=min(closestRadius,radius);
        if(radius<horizon) {captured=true;break;}
        if(radius>1.5 && dot(position,velocity)>0.0) {escaped=true;break;}
        float stepLength=max(horizon*0.11,min(0.13,radius*0.13));
        vec3 transverse=position-velocity*dot(position,velocity);
        vec3 force=-1.5*horizon*DiskSettings.w*transverse/max(radius*radius*radius,1e-7);
        vec3 middleVelocity=normalize(velocity+force*stepLength*0.5);
        vec3 nextPosition=position+middleVelocity*stepLength;
        float signedStart=dot(position,diskNormal),signedEnd=dot(nextPosition,diskNormal);
        if(signedStart*signedEnd<0.0) {
            vec3 hit=mix(position,nextPosition,signedStart/(signedStart-signedEnd));
            float radial=length(hit)/horizon;
            if(radial>2.6 && radial<DiskSettings.y) {
                vec3 source=skyAccretion(hit,diskX,diskY,horizon);
                emission+=source*transmission*BlackHoleSettings.x;
                transmission*=0.82;
            }
        }
        vec3 middlePosition=(position+nextPosition)*0.5;
        float middleRadius=length(middlePosition);
        vec3 middleTransverse=middlePosition-middleVelocity*dot(middlePosition,middleVelocity);
        vec3 middleForce=-1.5*horizon*DiskSettings.w*middleTransverse/max(pow(middleRadius,3.0),1e-7);
        velocity=normalize(velocity+middleForce*stepLength);
        position=nextPosition;
    }
    vec3 warped=captured?vec3(0):skyEnvironment(velocity)*transmission;
    // Unresolved tightly orbiting rays fade instead of inventing a bright background.
    if(!captured && !escaped && closestRadius<horizon*1.8) warped*=0.35;
    float angularRadius=sin(separation),critical=2.6*horizon;
    float ringWidth=max(fwidth(angularRadius)*1.1,horizon*0.07);
    float photonRing=exp(-pow((angularRadius-critical)/ringWidth,2.0))*DiskSettings.w;
    emission+=mix(DiskColorA,DiskColorB,0.85)*photonRing*BlackHoleSettings.x*0.32;
    float blend=1.0-smoothstep(influence*0.68,influence,separation);
    color=mix(color,warped+emission,blend);
}
// The following sky is an original bounded analytic approximation, rather than
// a spectral atmosphere/LUT solution. All contributions remain linear/HDR until
// renderSky's one exposure operation. It introduces no textures or scene lights.
void skyDayDirections(out vec3 sun, out vec3 moon, out vec3 sunSide, out vec3 moonSide) {
    float cycle=Time*DaySettings.x*0.035;
    float solarPhase=SunSettings.y+cycle,lunarPhase=MoonSettings.z+cycle;
    vec3 sunHorizontal=vec3(sin(SunSettings.z),0.0,cos(SunSettings.z));
    vec3 moonHorizontal=vec3(sin(MoonSettings.y),0.0,cos(MoonSettings.y));
    sun=sunHorizontal*cos(solarPhase)+vec3(0.0,sin(solarPhase),0.0);
    moon=moonHorizontal*cos(lunarPhase)+vec3(0.0,sin(lunarPhase),0.0);
    // Yaw-defined sides are perpendicular for every phase, including zenith.
    // A cross(up,front) basis would be undefined twice during a full day cycle.
    sunSide=vec3(cos(SunSettings.z),0.0,-sin(SunSettings.z));
    moonSide=vec3(cos(MoonSettings.y),0.0,-sin(MoonSettings.y));
}
float skyAirMass(float elevation) {
    // Finite horizon path length; negative elevations use the horizon value.
    float h=max(elevation,0.0);
    return 1.0/(0.12+sqrt(h*h+0.008));
}
vec3 skyAirExtinction() {
    return vec3(0.085,0.170,0.340)*AtmosphereSettings.z+
           vec3(0.040,0.038,0.034)*AtmosphereSettings.y*AtmosphereSettings.w;
}
float skySolarVisibility(vec3 sun) {
    // Continuous twilight illumination persists after the disk has set.
    return smoothstep(-0.20,0.025,sun.y);
}
vec3 skySolarIrradiance(vec3 sun) {
    if(AtmospherePhysical.x>1.5 && skyPhysicalReady()) return skyPhysicalSolarIrradiance(sun);
    float sunsetWeight=1.0-smoothstep(0.015,0.25,sun.y);
    float sunsetPath=skyAirMass(sun.y)*(0.35+0.65*sunsetWeight)+max(-sun.y,0.0)*28.0;
    return SunColor*SunSettings.x*exp(-skyAirExtinction()*sunsetPath)*skySolarVisibility(sun);
}
float skyMiePhase(float cosine) {
    float g=DaySettings.y;
    return (1.0-g*g)/pow(max(1.0+g*g-2.0*g*cosine,0.01),1.5);
}
void skyAtmosphere(vec3 d, vec3 sun, out vec3 scattering, out vec3 transmission) {
    if(AtmospherePhysical.x>1.5 && skyPhysicalReady()) {skyPhysicalAtmosphere(d,sun,scattering,transmission);return;}
    float mass=skyAirMass(d.y),mu=clamp(dot(d,sun),-1.0,1.0);
    float daylight=smoothstep(-0.06,0.16,sun.y);
    float twilight=exp(-pow((sun.y+0.065)/0.13,2.0))*DaySettings.w;
    vec3 betaRayleigh=vec3(0.085,0.170,0.340)*AtmosphereSettings.z;
    vec3 betaMie=vec3(0.040,0.038,0.034)*AtmosphereSettings.y*AtmosphereSettings.w;
    vec3 sunlight=skySolarIrradiance(sun);
    float rayleighPhase=0.65+0.35*mu*mu;
    vec3 rayleigh=(vec3(1.0)-exp(-betaRayleigh*mass))*sunlight*rayleighPhase*vec3(1.72,2.15,2.795);
    vec3 mie=(vec3(1.0)-exp(-betaMie*mass))*sunlight*(0.30+0.18*skyMiePhase(mu));
    // Broad multiple-scattering appearance is an artistic, inexpensive fill.
    vec3 diffuse=vec3(0.045,0.14,0.38)*daylight*AtmosphereSettings.z;
    float horizontalLength=length(d.xz);
    vec2 horizontal=d.xz/max(horizontalLength,0.0001);
    vec2 solarHorizontal=sun.xz/max(length(sun.xz),0.0001);
    float towards=pow(clamp(dot(horizontal,solarHorizontal)*0.5+0.5,0.0,1.0),3.0);
    float horizon=exp(-abs(d.y+0.012)*8.0);
    vec3 dusk=SunColor*vec3(0.80,0.19,0.055)*twilight*horizon*(0.23+0.77*towards)*SunSettings.x;
    dusk+=vec3(0.050,0.055,0.135)*twilight*(1.0-horizon)*AtmosphereSettings.z;
    vec3 night=vec3(0.09,0.17,0.30)*DaySettings.z*(1.0-daylight);
    vec3 upper=(rayleigh+mie+diffuse+dusk+night)*SkyTint;
    vec3 lower=(sunlight*vec3(0.075,0.065,0.055)+night*0.75)*SkyTint;
    scattering=mix(lower,upper,smoothstep(-0.23,0.025,d.y));
    // Procedural cosmic sources use artistic radiance. This smooth contrast
    // envelope makes their visibility track daylight while the spectral factor
    // supplies directional air extinction. It is not a measured optical depth.
    float spaceVisibility=1.0-smoothstep(-0.12,0.16,sun.y);
    transmission=exp(-(betaRayleigh+betaMie)*mass)*mix(0.008,1.0,spaceVisibility*spaceVisibility);
    transmission*=smoothstep(-0.24,0.015,d.y);
}
void skySunMoon(vec3 d, vec3 sun, vec3 moon, vec3 moonSide, inout vec3 color) {
    float sunAngle=acos(clamp(dot(d,sun),-1.0,1.0));
    float sunPixel=clamp(fwidth(sunAngle),0.00005,0.025);
    float sunMask=(1.0-smoothstep(SunSettings.w-sunPixel,SunSettings.w+sunPixel,sunAngle))*
                  (AtmospherePhysical.x>1.5 && skyPhysicalReady()?skyPhysicalSunVisibility(d):smoothstep(-0.018,0.010,sun.y));
    float sunLimb=sqrt(max(1.0-pow(sin(sunAngle)/max(sin(SunSettings.w),0.0001),2.0),0.0));
    vec3 solarColor=skySolarIrradiance(sun)*(16.0+9.0*sunLimb);
    color+=solarColor*sunMask;

    float moonAlignment=dot(d,moon);
    float moonAngle=acos(clamp(moonAlignment,-1.0,1.0));
    float moonPixel=clamp(fwidth(moonAngle),0.00005,0.025);
    float moonMask=(1.0-smoothstep(MoonSettings.w-moonPixel,MoonSettings.w+moonPixel,moonAngle))*
                   (AtmospherePhysical.x>1.5 && skyPhysicalReady()?skyPhysicalSunVisibility(d):smoothstep(-0.018,0.012,moon.y));
    vec3 moonUp=cross(moon,moonSide);
    vec2 uv=vec2(dot(d,moonSide),dot(d,moonUp))/max(sin(MoonSettings.w),0.0001);
    vec3 n=vec3(uv,sqrt(max(1.0-dot(uv,uv),0.0)));
    float lunarLight=max(dot(n,vec3(sin(MoonSurface.x),0.0,cos(MoonSurface.x))),0.0);
    float illuminatedFraction=0.5+0.5*cos(MoonSurface.x);
    vec3 extinction=exp(-skyAirExtinction()*skyAirMass(moon.y));
    if(AtmospherePhysical.x>1.5 && skyPhysicalReady()){vec3 unused;skyPhysicalAtmosphere(d,sun,unused,extinction);}
    if(moonMask>0.0 && MoonSettings.x>0.0) {
        float spin=Time*MoonSurface.w*0.15,c=cos(spin),s=sin(spin);
        vec3 surfaceNormal=vec3(c*n.x-s*n.z,n.y,s*n.x+c*n.z);
        vec3 origin=vec3(Seed*0.0021,Seed*0.0015,Seed*0.0033);
        float land=skyFbm(surfaceNormal*MoonSurface.y+origin,Quality+2);
        float crater=skyCrater(surfaceNormal,MoonSurface.y*2.5);
        float albedo=clamp(0.58+land*0.65+crater*0.55,0.16,1.2);
        vec3 moonColor=MoonColor*MoonSettings.x*extinction*albedo*(0.025+1.45*lunarLight);
        color=mix(color,moonColor,moonMask);
    }
    // Finite, phase-aware local glow, not a physical ice-crystal halo or bloom.
    float haloRadius=max(MoonSettings.w*5.0,0.07);
    float haloSupport=1.0-smoothstep(haloRadius*0.7,haloRadius,moonAngle);
    float halo=exp(-max(moonAngle-MoonSettings.w,0.0)/max(MoonSettings.w*1.6,0.01))*
               smoothstep(MoonSettings.w-moonPixel,MoonSettings.w+moonPixel,moonAngle)*haloSupport;
    float night=1.0-smoothstep(-0.03,0.18,sun.y);
    color+=MoonColor*extinction*halo*MoonSurface.z*MoonSettings.x*illuminatedFraction*night*0.18*
           (AtmospherePhysical.x>1.5 && skyPhysicalReady()?skyPhysicalSunVisibility(d):smoothstep(-0.018,0.012,moon.y));
}
void skyThinClouds(vec3 d, vec3 sun, vec3 moon, inout vec3 color) {
    if(ThinCloudSettings.x<=0.0 || ThinCloudSettings.y<=0.0) return;
    // A far 2D sheet: it has density/opacity, but no traversable volume or world
    // shadow. All coordinates and derivatives are bounded at parallel rays.
    float denominator=max(d.y,0.035);
    vec2 plane=d.xz/denominator;
    float distanceFade=1.0-smoothstep(14.0,26.0,length(plane));
    float domain=smoothstep(0.025,0.075,d.y)*distanceFade;
    vec2 wind=vec2(sin(ThinCloudMotion.y),cos(ThinCloudMotion.y));
    vec2 across=vec2(wind.y,-wind.x);
    vec2 p=vec2(dot(plane,wind),dot(plane,across))*ThinCloudSettings.w*ThinCloudSettings.z*0.85;
    p.x-=Time*ThinCloudMotion.x*0.075;
    float evolution=Seed*0.0037+Time*ThinCloudMotion.z*0.075;
    int octaves=Quality+2;
    float broad=skyFbm(vec3(p*vec2(0.48,1.10),evolution),octaves);
    float threads=skyFbm(vec3(p*vec2(1.15,4.0)+vec2(4.2,7.5),evolution*0.8+19.3),octaves);
    float shape=broad*0.80+threads*0.20;
    float threshold=mix(0.77,0.10,ThinCloudSettings.y);
    float density=smoothstep(threshold-0.13,threshold+0.15,shape);
    float alpha=(1.0-exp(-density*ThinCloudSettings.x*1.8))*domain;
    float daylight=smoothstep(-0.06,0.16,sun.y);
    float twilight=exp(-pow((sun.y+0.065)/0.13,2.0))*DaySettings.w;
    float lunarFraction=0.5+0.5*cos(MoonSurface.x);
    float moonlight=MoonSettings.x*lunarFraction*smoothstep(-0.03,0.18,moon.y)*(1.0-daylight);
    vec3 irradiance=skySolarIrradiance(sun);
    vec3 ambient=vec3(0.22,0.32,0.48)*daylight+MoonColor*(DaySettings.z*1.6+moonlight*0.12);
    ambient+=SunColor*vec3(0.20,0.065,0.027)*twilight;
    float shade=0.56+0.44*threads;
    vec3 cloud=ThinCloudColor*(ambient+irradiance*0.90)*shade;
    float forward=pow(max(dot(d,sun),0.0),12.0);
    float silver=forward*pow(max(1.0-density,0.0),0.55)*ThinCloudMotion.w;
    cloud+=irradiance*silver*1.8;
    color=mix(color,cloud,alpha);
}
vec3 renderSky(vec3 direction) {
    // The 2D lens remaps only the distant cosmic background. Atmospheric
    // scattering, sun/moon, thin clouds and world-depth media keep this ray.
    // Zero strength and the original curvature style retain the old function.
    vec3 color;
    if(BlackHoleAppearance.x>=1.5 && BlackHoleSettings.x>0.0 && DiskSettings.w>0.0)
        color=skyBlackHoleCinematicBackground(direction);
    else color=skyEnvironment(direction);
    skyBlackHole(direction,color);
    // This branch preserves the legacy radiance and exact tone-map expression
    // for every original recipe, including animated black holes and rings.
    if(AtmosphereSettings.x>0.0) {
        vec3 sun,moon,sunSide,moonSide;
        skyDayDirections(sun,moon,sunSide,moonSide);
        vec3 scattering,transmission;
        skyAtmosphere(direction,sun,scattering,transmission);
        vec3 atmospheric=scattering+transmission*color;
        skySunMoon(direction,sun,moon,moonSide,atmospheric);
        skyThinClouds(direction,sun,moon,atmospheric);
        color=mix(color,atmospheric,AtmosphereSettings.x);
    }
    skyExtraEnvironment(direction,color);
    return vec3(1)-exp(-max(color,vec3(0))*CloudSettings.w);
}
vec3 renderStarsOnly(vec3 direction) {
    vec3 color=BackgroundColor;
    color+=skyStars(direction,55.0,11.0,0.0);
    if(Quality>=2) color+=skyStars(direction,145.0,97.0,0.0)*0.72;
    if(Quality>=3) color+=skyStars(direction,300.0,231.0,0.0)*0.48;
    return vec3(1)-exp(-max(color,vec3(0))*CloudSettings.w);
}

// Original finite direction-space celestial impostors. No external textures.
// Concatenate after sky-core helpers. The host declares GiantBodySettings as
// (style 0..7, angular scale 1..4, separation 0..3, atmosphere/corona 0..2).
// skyCelestial delegates here only for style > 0. Normal style 0 stays intact.
// BodySettings.y is the base angular radius in radians; the effective radius
// is capped at 1.18 rad (67.6 degrees), before optional rings or corona.
float skyGiantWindow(float r,float outer) {
    return 1.0-smoothstep(outer*0.78,outer,r);
}
vec2 skyGiantRotate(vec2 p,float a) {
    float c=cos(a),s=sin(a);
    return vec2(c*p.x-s*p.y,s*p.x+c*p.y);
}
float skyGiantDisc(vec2 p,float radius,float aa) {
    return 1.0-smoothstep(radius-aa,radius+aa,length(p));
}
vec3 skyGiantNormal(vec2 p) {
    // Analytic apparent hemisphere: finite both inside and outside its limb.
    return normalize(vec3(p,sqrt(max(1.0-dot(p,p),0.0)))+vec3(0.0,0.0,0.00001));
}
float skyGiantLight(vec3 n) {
    vec3 light=normalize(vec3(sin(BodySurface.y),0.18,cos(BodySurface.y)));
    return max(dot(n,light),0.0);
}
vec3 skyGiantSeed() {
    return vec3(Seed*0.0017,Seed*0.0023,Seed*0.0031)+vec3(7.1,31.9,13.7);
}
vec3 skyGiantRotatingNormal(vec3 n,float clock) {
    vec2 xz=skyGiantRotate(n.xz,clock*0.16);
    return vec3(xz.x,n.y,xz.y);
}
float skyGiantCorona(vec2 p,float clock,float aa,float width) {
    float r=length(p);
    // Cartesian ring sampling is periodic on the complete circumference.
    // It introduces no atan/fract longitude discontinuity or pole axis choice.
    vec2 unit=p/max(r,0.001);
    vec2 moving=skyGiantRotate(unit,clock*0.12);
    float grain=skyFbm(vec3(moving*4.2,r*5.0-clock*0.24)+skyGiantSeed(),Quality+2);
    float edge=1.0+width*(0.13+0.85*pow(grain,2.0));
    float outside=smoothstep(1.0-aa,1.0+aa,r);
    float filament=pow(max(grain,0.0),2.0)*(0.75+0.25*sin(r*59.0-clock*2.1+grain*7.0));
    return outside*exp(-max(r-edge,0.0)/max(width*0.30,0.01))*
           skyGiantWindow(r,1.0+width*2.8)*(0.15+2.5*filament);
}
void skyGiantGas(vec2 p,float aa,float clock,inout vec3 color) {
    vec2 q=p/vec2(1.0,0.91);
    float r=length(q),mask=skyGiantDisc(q,1.0,aa);
    vec3 n=skyGiantNormal(q),rot=skyGiantRotatingNormal(n,clock);
    float warp=skyFbm(rot*3.5+skyGiantSeed(),Quality+2);
    float storm=skyFbm(rot*11.0+skyGiantSeed()+vec3(0.0,clock*0.08,0.0),Quality+2);
    float bands=0.5+0.5*sin(rot.y*(20.0+BodySurface.z*1.5)+warp*6.0+storm*1.6);
    float oval=exp(-dot((q-vec2(0.26,0.14))/vec2(0.27,0.12),
                       (q-vec2(0.26,0.14))/vec2(0.27,0.12)));
    float swirling=0.5+0.5*sin(length((q-vec2(0.26,0.14))/vec2(0.8,0.35))*45.0-warp*8.0-clock*0.25);
    vec3 surface=mix(BodyColorA,BodyColorB,clamp(bands*0.80+storm*0.20,0.0,1.0));
    surface=mix(surface,BodyColorB*(0.6+0.6*swirling),oval*0.62);
    vec3 body=surface*(0.055+1.18*skyGiantLight(n));
    float halo=exp(-max(r-1.0,0.0)/0.055)*smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.33);
    // Optional thin elliptical ring; front half overlays the visible planet.
    vec2 ringPoint=skyGiantRotate(p,-0.28);
    float inclination=max(abs(cos(RingSettings.y)),0.13);
    vec2 ringUv=ringPoint/vec2(1.0,inclination*0.45);
    float rr=length(ringUv),outer=1.50+0.18*GiantBodySettings.z;
    float ringMask=smoothstep(1.12-aa,1.12+aa,rr)*(1.0-smoothstep(outer-aa,outer+aa,rr));
    float stripes=0.58+0.42*sin(rr*140.0+storm*1.8);
    float ringAlpha=ringMask*clamp(RingSettings.x*0.72,0.0,0.92);
    vec3 ringColor=RingColor*(0.24+0.90*stripes)*BodySettings.x;
    if(ringPoint.y>=0.0) color=mix(color,ringColor,ringAlpha);
    color=mix(color,body*BodySettings.x,mask);
    if(ringPoint.y<0.0) color=mix(color,ringColor,ringAlpha);
    color+=BodyColorA*halo*BodySettings.x*GiantBodySettings.w*0.20;
}
void skyGiantFrozen(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float continents=skyFbm(rot*max(BodySurface.z*0.35,2.0)+skyGiantSeed(),Quality+3);
    float cracks=abs(skyFbm(rot*10.0+skyGiantSeed(),Quality+2)-0.43);
    float ice=smoothstep(0.35,0.53,continents+abs(rot.y)*0.13);
    float seams=1.0-smoothstep(0.012,0.04,cracks);
    vec3 surface=mix(BodyColorB*0.28,BodyColorA*(0.68+continents*0.65),ice);
    surface+=BodyColorB*seams*ice*0.15;
    float rim=pow(1.0-n.z,3.0);
    vec3 body=surface*(0.07+skyGiantLight(n)*1.10)+BodyColorA*rim*0.18;
    color=mix(color,body*BodySettings.x,mask);
    float haze=exp(-max(r-1.0,0.0)/0.07)*smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.40);
    color+=mix(BodyColorA,BodyColorB,0.25)*haze*BodySettings.x*GiantBodySettings.w*0.28;
}
void skyGiantShattered(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float rock=skyFbm(rot*max(BodySurface.z,3.0)+skyGiantSeed(),Quality+3);
    float crooked=0.065*sin(p.y*7.0+rock*2.0);
    float fracture=min(abs(p.x+0.40*p.y+crooked),
                       min(abs(p.y-0.25*p.x+0.19+crooked),abs(p.x-0.72*p.y-0.42)));
    float gap=0.013+0.018*GiantBodySettings.z;
    float intact=smoothstep(gap-aa*0.5,gap+aa,fracture);
    float hot=exp(-fracture/max(gap*1.4,0.014))*(0.70+0.30*sin(clock*0.8+rock*6.0));
    vec3 body=mix(BodyColorB*0.3,BodyColorA,rock)*(0.05+skyGiantLight(n));
    color=mix(color,body*BodySettings.x,mask*intact);
    color+=BodyColorB*hot*mask*BodySettings.x*0.8;
    // Detached fragments orbit beyond the broken silhouette, not a color swap.
    for(int i=0;i<9;i++) {
        vec3 h=skyHash(vec3(float(i),27.1,Seed*0.01));
        float angle=h.x*2.0*SKY_PI+clock*(0.025+h.z*0.025);
        float distance=1.10+h.y*(0.25+GiantBodySettings.z*0.17);
        vec2 center=vec2(cos(angle),sin(angle))*distance;
        vec2 q=skyGiantRotate(p-center,angle*1.7)/vec2(0.035+h.z*0.075,0.025+h.y*0.050);
        float fragment=1.0-smoothstep(0.70,1.0,max(abs(q.x),abs(q.y)));
        color=mix(color,BodyColorA*(0.20+0.55*h.y)*BodySettings.x,fragment*0.92);
    }
    color+=BodyColorB*exp(-pow((r-1.0)/0.045,2.0))*mask*BodySettings.x*GiantBodySettings.w*0.15;
}
void skyGiantDarkDisc(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    color*=1.0-mask*min(BodySettings.x,1.0);
    float first=skyGiantCorona(p,clock,aa,0.23+GiantBodySettings.w*0.16);
    float second=skyGiantCorona(skyGiantRotate(p,0.9),-clock*0.73,aa,0.12+GiantBodySettings.w*0.17);
    float north=0.5+0.5*p.y/max(r,0.001);
    vec3 emission=BodyColorA*first*(0.25+0.75*north)+BodyColorB*second*(1.0-0.40*north);
    float edge=exp(-pow((r-1.018)/max(aa,0.015),2.0));
    color+=(emission*0.82+mix(BodyColorA,BodyColorB,0.42)*edge*0.75)*BodySettings.x;
}
void skyGiantStar(vec2 p,float aa,float clock,vec3 tintA,vec3 tintB,float strength,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float cells=skyFbm(rot*(8.0+BodySurface.z*0.8)+skyGiantSeed()+vec3(0.0,clock*0.06,0.0),Quality+2);
    float broad=skyFbm(rot*3.5+skyGiantSeed()+vec3(clock*0.08,0.0,0.0),Quality+2);
    float limb=0.35+0.65*pow(max(n.z,0.0),0.40);
    vec3 photosphere=mix(tintB,tintA,clamp(0.28+cells*0.85,0.0,1.0))*limb*(0.50+0.85*broad);
    color=mix(color,photosphere*strength*1.6,mask);
    float corona=skyGiantCorona(p,clock,aa,0.22+GiantBodySettings.w*0.18);
    vec2 unit=p/max(r,0.001),w=skyGiantRotate(unit,clock*0.10);
    // Local closed prominence loops lift away from the limb at selected arcs.
    float lobe=pow(max(0.0,0.5+0.5*(3.0*w.y-4.0*w.y*w.y*w.y)),8.0);
    float loopRadius=1.10+(0.09+0.15*GiantBodySettings.w)*lobe;
    float loops=exp(-pow((r-loopRadius)/0.030,2.0))*lobe*
                smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.72);
    color+=(tintB*corona*0.8+mix(tintA,tintB,0.5)*loops*0.9)*strength;
}
void skyGiantBinary(vec2 p,float aa,float clock,inout vec3 color) {
    float separation=0.55+0.24*GiantBodySettings.z;
    vec2 axis=vec2(cos(clock*0.075+0.22),sin(clock*0.075+0.22));
    vec2 first=axis*separation,second=-axis*separation;
    float sizeA=0.64,sizeB=0.46;
    vec2 bridge=vec2(dot(p,axis),dot(p,vec2(-axis.y,axis.x)));
    float connection=exp(-pow(bridge.y/0.105,2.0))*skyGiantWindow(abs(bridge.x),separation)*
                     (0.6+0.4*skyNoise(vec3(bridge*12.0,clock*0.7)+skyGiantSeed()));
    color+=mix(BodyColorA,BodyColorB,0.5)*connection*BodySettings.x*GiantBodySettings.w*0.16;
    skyGiantStar((p-first)/sizeA,aa/sizeA,clock,BodyColorA,BodyColorB,BodySettings.x,color);
    skyGiantStar((p-second)/sizeB,aa/sizeB,-clock*1.3,BodyColorB,BodyColorA,BodySettings.x*0.76,color);
}
void skyGiantVortex(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    color*=1.0-mask*min(BodySettings.x,1.0);
    float twist=clock*0.24+2.8/(r*r+0.18)+r*2.5;
    vec2 warped=skyGiantRotate(p,twist),unit=warped/max(r,0.001);
    float arms=0.5+0.5*(3.0*unit.y-4.0*unit.y*unit.y*unit.y);
    float gas=skyFbm(vec3(warped*5.0,r*3.0-clock*0.25)+skyGiantSeed(),Quality+2);
    float strands=0.5+0.5*sin(r*72.0-clock*2.5+gas*8.0);
    float outside=smoothstep(1.0-aa,1.0+aa,r);
    float ring=exp(-max(r-1.035,0.0)/0.17)*outside;
    float cloud=exp(-max(r-1.0,0.0)/0.65)*pow(max(arms,0.0),3.0)*(0.25+gas);
    float support=skyGiantWindow(r,2.30+GiantBodySettings.w*0.30);
    vec3 emission=mix(BodyColorB,BodyColorA,gas)*(ring*(0.7+0.8*strands)+cloud*outside*0.70);
    color+=emission*support*BodySettings.x*(0.7+GiantBodySettings.w*0.35);
}
void skyGiantCelestial(vec3 d,inout vec3 color) {
    if(BodySettings.x<=0.0 || GiantBodySettings.x<0.5) return;
    vec3 center=vec3(sin(BodySettings.z)*cos(BodySettings.w),sin(BodySettings.w),
                     cos(BodySettings.z)*cos(BodySettings.w));
    // A yaw-defined side stays finite at the exact north and south poles.
    vec3 side=vec3(cos(BodySettings.z),0.0,-sin(BodySettings.z));
    vec3 up=cross(center,side);
    float alignment=dot(d,center);
    float angularRadius=clamp(BodySettings.y*GiantBodySettings.y,0.003,1.18);
    vec2 p=vec2(dot(d,side),dot(d,up))/(max(alignment,0.02)*tan(angularRadius));
    // Differentiate a bounded field before support rejection. No tangent-plane
    // derivative can create the horizontal far-field lines seen in old effects.
    float support=3.65;
    vec2 bounded=clamp(p,vec2(-support-1.0),vec2(support+1.0));
    float aa=clamp(max(fwidth(bounded.x),fwidth(bounded.y)),0.0006,0.045);
    if(alignment<=0.0 || length(p)>=support) return;
    float clock=Time*BodySurface.w;
    int style=int(floor(GiantBodySettings.x+0.5));
    if(style==1) skyGiantGas(p,aa,clock,color);
    else if(style==2) skyGiantFrozen(p,aa,clock,color);
    else if(style==3) skyGiantShattered(p,aa,clock,color);
    else if(style==4) skyGiantDarkDisc(p,aa,clock,color);
    else if(style==5) skyGiantBinary(p,aa,clock,color);
    else if(style==6) skyGiantStar(p,aa,clock,BodyColorA,BodyColorB,BodySettings.x,color);
    else if(style==7) skyGiantVortex(p,aa,clock,color);
}

// Original direction-space extensions. Concatenate after sky-core.glsl, before
// environment-core.glsl in both hosts. No sampled images or third-party code.
// Recipe angular widths are converted to radians by the host exactly once.
vec2 skyWeatherAmounts() {
    return mix(clamp(WeatherSettings.xy,vec2(0.0),vec2(1.0)),
               clamp(WorldWeather,vec2(0.0),vec2(1.0)),clamp(WeatherMotion.y,0.0,1.0));
}
float skyCompactGaussian(float coordinate,float width,float support) {
    float q=abs(coordinate);
    return exp(-pow(q/max(width,0.00001),2.0))*(1.0-smoothstep(support*0.78,support,q));
}
vec3 skyOpticalSpectrum(float coordinate) {
    // Violet -> blue -> cyan -> green -> yellow -> orange -> red.
    float t=clamp(coordinate,0.0,1.0)*6.0;
    if(t<1.0) return mix(vec3(0.36,0.035,0.8),vec3(0.04,0.22,1.0),t);
    if(t<2.0) return mix(vec3(0.04,0.22,1.0),vec3(0.06,0.8,0.8),t-1.0);
    if(t<3.0) return mix(vec3(0.06,0.8,0.8),vec3(0.10,0.9,0.16),t-2.0);
    if(t<4.0) return mix(vec3(0.10,0.9,0.16),vec3(1.0,0.86,0.06),t-3.0);
    if(t<5.0) return mix(vec3(1.0,0.86,0.06),vec3(1.0,0.29,0.025),t-4.0);
    return mix(vec3(1.0,0.29,0.025),vec3(0.95,0.055,0.025),t-5.0);
}
void skyRainbow(vec3 d,vec3 sun,vec2 weather,inout vec3 color) {
    if(RainbowSettings.x<=0.0 || AtmosphereSettings.x<=0.0) return;
    // Droplets can persist after rain: wetness is an independent explicit input.
    float wet=clamp(max(weather.x,RainbowSettings.w),0.0,1.0);
    float daylight=smoothstep(0.0,0.08,sun.y)*min(SunSettings.x,1.6);
    float cloudCover=clamp(ThinCloudSettings.x*ThinCloudSettings.y+
                          VolumeCloudSettings.x*VolumeCloudSettings.y,0.0,1.0);
    float visibility=wet*daylight*(1.0-0.75*cloudCover)*(1.0-0.85*weather.y)*
                     smoothstep(-0.018,0.025,d.y)*AtmosphereSettings.x;
    // Primary rainbow is centered on the antisolar point, red outside; the
    // secondary is near 51 degrees with the color order reversed. These are
    // bounded artistic band approximations, not a spectral droplet integrator.
    float angle=acos(clamp(dot(d,-sun),-1.0,1.0));
    float aa=clamp(fwidth(angle),0.00004,0.012);
    float halfWidth=max(RainbowSettings.y,0.00436);
    float primaryCenter=41.6*SKY_PI/180.0;
    float secondaryCenter=51.6*SKY_PI/180.0;
    float primaryDistance=angle-primaryCenter;
    float secondaryDistance=angle-secondaryCenter;
    float primary=1.0-smoothstep(halfWidth-aa,halfWidth+aa,abs(primaryDistance));
    float secondary=1.0-smoothstep(halfWidth*1.4-aa,halfWidth*1.4+aa,abs(secondaryDistance));
    float tPrimary=0.5+primaryDistance/(2.0*halfWidth);
    float tSecondary=0.5-secondaryDistance/(2.8*halfWidth);
    float innerWindow=smoothstep(42.8*SKY_PI/180.0,44.0*SKY_PI/180.0,angle);
    float outerWindow=1.0-smoothstep(49.2*SKY_PI/180.0,50.4*SKY_PI/180.0,angle);
    color*=1.0-0.12*innerWindow*outerWindow*visibility*min(RainbowSettings.x,1.0);
    color+=skyOpticalSpectrum(tPrimary)*primary*RainbowSettings.x*visibility*0.30;
    color+=skyOpticalSpectrum(tSecondary)*secondary*RainbowSettings.z*visibility*0.16;
}
void skyIceHalo(vec3 d,vec3 sun,vec3 moon,vec2 weather,inout vec3 color) {
    if(HaloSettings.x<=0.0 || HaloSettings.z<=0.0 || AtmosphereSettings.x<=0.0) return;
    bool lunar=HaloSettings.w>0.5;
    vec3 source=lunar?moon:sun;
    float sourceBrightness=lunar?MoonSettings.x*(0.5+0.5*cos(MoonSurface.x)):SunSettings.x;
    float visibility=smoothstep(-0.018,0.02,source.y)*sourceBrightness*
                     HaloSettings.z*AtmosphereSettings.x*(1.0-0.83*weather.y)*
                     smoothstep(-0.025,0.015,d.y);
    // Randomly oriented hexagonal ice prisms have a roughly 22-degree minimum
    // deviation. The reddish inner edge is near 22 deg; short wavelengths lie
    // outside it. Moon halos are mostly white. This is a geometric optical ring,
    // deliberately unrelated to the small local moon glow or a bloom pass.
    float angle=acos(clamp(dot(d,source),-1.0,1.0));
    float aa=clamp(fwidth(angle),0.00004,0.012);
    float width=max(HaloSettings.y,0.00349);
    float inner=21.8*SKY_PI/180.0;
    float edge=smoothstep(inner-aa,inner+aa,angle);
    float halo=edge*skyCompactGaussian(angle-(inner+width*0.50),width*0.85,width*3.0);
    float spectrum=1.0-clamp((angle-inner)/max(width,0.0001),0.0,1.0);
    vec3 tint=mix(vec3(1.0),skyOpticalSpectrum(spectrum),lunar?0.12:0.50);
    float cirrus=0.60+0.40*clamp(ThinCloudSettings.y+VolumeCloudSettings.y*VolumeCloudSettings.x,0.0,1.0);
    color+=tint*halo*HaloSettings.x*visibility*cirrus*(lunar?0.12:0.24);
}
void skyStorm(vec3 d,vec2 weather,inout vec3 color) {
    float storm=clamp(weather.x*0.55+weather.y*0.80,0.0,1.0);
    if(storm<=0.0) return;
    float clock=Time*WeatherSettings.z;
    float rolling=skyFbm(d*3.4+vec3(clock*0.015,Seed*0.008,clock*0.009),Quality+2);
    float darkness=storm*WeatherSettings.w*(0.48+0.32*rolling)*smoothstep(-0.20,0.1,d.y);
    color*=1.0-darkness;
    color+=vec3(0.014,0.020,0.029)*storm*(0.20+0.65*rolling);
    if(weather.y<=0.0 || WeatherMotion.z<=0.0) return;
    float period=7.2/max(WeatherMotion.z,0.001);
    float event=floor(clock/period),within=clock-event*period;
    vec3 pick=skyHash(vec3(event+Seed*0.031,17.21,38.16));
    float start=(0.12+0.48*pick.x)*period;
    float age=within-start,duration=WeatherMotion.w;
    float leading=smoothstep(0.0,min(0.035,duration*0.15),age);
    float envelope=leading*(1.0-smoothstep(duration*0.5,duration,age));
    float pulses=0.72+0.28*pow(0.5+0.5*cos(age*83.0),2.0);
    vec3 center=normalize(vec3(sin(pick.y*2.0*SKY_PI),0.28+0.30*pick.z,cos(pick.y*2.0*SKY_PI)));
    float illumination=0.30+0.70*pow(max(dot(d,center),0.0),3.0);
    color+=LightningColor*weather.y*envelope*pulses*illumination*1.6;
}
void skySpecialWave(vec3 d,inout vec3 color) {
    if(SpecialSettings.x<=0.0 || SpecialSettings.y>2.5) return;
    float clock=Time*SpecialSettings.w;
    vec3 p=d*SpecialSettings.z*4.0+vec3(Seed*0.0019,Seed*0.0031,Seed*0.0013);
    vec3 warp=vec3(skyFbm(p+vec3(clock*0.09,0.0,0.0),Quality+2),
                   skyFbm(p+vec3(10.8,clock*0.07,19.1),Quality+2),
                   skyFbm(p+vec3(17.1,7.2,-clock*0.06),Quality+2));
    p+=(warp-0.5)*SpecialShape.x*2.0;
    float above=smoothstep(-0.18,0.04,d.y);
    if(SpecialSettings.y<1.5) {
        float field=(sin(p.x*1.7+p.z*0.8-clock*0.11)+
                     0.42*sin(p.y*2.7-p.z*1.2+clock*0.17))*0.70;
        float width=SpecialShape.y;
        float aa=clamp(fwidth(field),0.001,0.075);
        float rim=1.0-smoothstep(width-aa,width+aa,abs(field));
        float core=skyCompactGaussian(field,width*0.11+aa,width*0.55+aa);
        float veins=0.45+0.55*pow(skyNoise(p*5.3+clock*0.13),2.0);
        float alpha=(1.0-exp(-rim*SpecialSettings.x*0.34))*above;
        color=mix(color,SpecialColorA*(0.10+0.20*warp.y),alpha);
        color+=(SpecialColorA*rim*0.09+SpecialColorB*core*veins*0.80)*SpecialSettings.x*above;
    } else {
        float turbulence=skyFbm(p*1.7+vec3(clock*0.07,-clock*0.16,0.0),Quality+2);
        float density=smoothstep(0.20,0.74,warp.x*0.40+turbulence*0.60);
        float thread=1.0-abs(2.0*skyNoise(p*5.8+vec3(0.0,clock*0.17,clock*0.04))-1.0);
        float veins=pow(thread,9.0)*smoothstep(0.29,0.59,turbulence);
        float alpha=(1.0-exp(-density*SpecialSettings.x*0.70))*above;
        color=mix(color,SpecialColorA*(0.07+0.20*turbulence),alpha);
        color+=(SpecialColorA*density*0.19+SpecialColorB*veins*(0.60+warp.z))*SpecialSettings.x*above;
    }
}
void skyBirds(vec3 d,inout vec3 color) {
    if(SpecialShape.z<=0.0) return;
    float opacity=0.0;
    for(int i=0;i<24;i++) {
        vec3 random=skyHash(vec3(float(i)+Seed*0.021,55.32,91.71));
        float selected=1.0-step(SpecialShape.z,random.z);
        float clock=Time*SpecialShape.w;
        float yaw=(random.x-0.5)*2.0*SKY_PI+clock*(0.035+random.y*0.025);
        float elevation=0.12+random.y*0.50+sin(clock*0.12+random.x*17.0)*0.025;
        vec3 front=vec3(sin(yaw)*cos(elevation),sin(elevation),cos(yaw)*cos(elevation));
        vec3 side=vec3(cos(yaw),0.0,-sin(yaw)),up=cross(front,side);
        float alignment=dot(d,front),size=0.004+0.007*random.y;
        vec2 uv=vec2(dot(d,side),dot(d,up))/max(alignment,0.2)/size;
        // Bound the differentiated field and cap footprint before rejecting
        // pixels; no diverging plane footprint can illuminate a whole row.
        float aa=clamp(max(fwidth(clamp(uv.x,-2.0,2.0)),fwidth(clamp(uv.y,-2.0,2.0))),0.02,0.25);
        if(alignment<=0.0 || abs(uv.x)>1.3 || abs(uv.y)>1.3) continue;
        float flap=sin(clock*(6.0+random.x*2.0)+random.y*29.0)*0.44;
        float wingHeight=flap*abs(uv.x)+0.13*sin(min(abs(uv.x),1.0)*SKY_PI);
        float wing=(1.0-smoothstep(0.07,0.07+aa,abs(uv.y-wingHeight)))*
                   (1.0-smoothstep(0.80,1.0,abs(uv.x)));
        float body=1.0-smoothstep(1.0,1.0+aa*2.0,length((uv-vec2(0.0,-0.04))/vec2(0.09,0.25)));
        opacity+=selected*max(wing,body)*(SpecialSettings.y>2.5 && SpecialSettings.x>0.0?min(SpecialSettings.x,1.0):0.80);
    }
    color=mix(color,vec3(0.004,0.006,0.012),clamp(opacity,0.0,0.96));
}
void skyExtraEnvironment(vec3 direction,inout vec3 radiance) {
    vec2 weather=skyWeatherAmounts();
    skyStorm(direction,weather,radiance);
    vec3 sun,moon,sunSide,moonSide;
    skyDayDirections(sun,moon,sunSide,moonSide);
    skyRainbow(direction,sun,weather,radiance);
    skyIceHalo(direction,sun,moon,weather,radiance);
    skySpecialWave(direction,radiance);
    skyBirds(direction,radiance);
}
// Original bounded inverse thin-lens-style mapping for the direction-plane
// cinematic black hole. This is an artistic background distortion, not a
// Schwarzschild/Kerr geodesic or a physically calibrated lens mass.
vec3 skyBlackHoleCinematicLensDirection(vec3 d) {
    if(BlackHoleAppearance.x<1.5 || BlackHoleSettings.x<=0.0 || DiskSettings.w<=0.0) return d;
    vec3 center=vec3(sin(BlackHoleSettings.z)*cos(BlackHoleSettings.w),sin(BlackHoleSettings.w),
                     cos(BlackHoleSettings.z)*cos(BlackHoleSettings.w));
    vec3 side=vec3(cos(BlackHoleSettings.z),0.0,-sin(BlackHoleSettings.z)),up=cross(center,side);
    float alignment=dot(d,center);
    if(alignment<=0.0) return d;
    float radius=max(tan(BlackHoleSettings.y),0.001);
    vec2 p=vec2(dot(d,side),dot(d,up))/max(alignment,0.08)/radius;
    float r=length(p),support=3.25+clamp(DiskSettings.y-3.5,0.0,5.5)*0.15;
    if(r>=support) return d;
    float window=1.0-smoothstep(support*0.70,support,r);
    float strength=clamp(DiskSettings.w,0.0,1.5)*min(BlackHoleSettings.x,1.0);
    // beta = theta - a/theta, softened and bounded behind the opaque core.
    // The near-zero source radius outside the core stretches stars and nebula
    // into an Einstein-like arc; negative parity supplies the inner image.
    float deflection=min(2.2*strength*window/(r*r+0.08),4.0);
    vec2 source=p*(1.0-deflection);
    return normalize(center+radius*(side*source.x+up*source.y));
}
vec3 skyBlackHoleCinematicBackground(vec3 direction) {
    vec3 source=skyBlackHoleCinematicLensDirection(direction);
    // Retain the original unwarped derivative footprint outside the lens.
    if(all(equal(source,direction))) return skyEnvironment(direction);
    float clustering;
    vec3 color=BackgroundColor+skyGalaxy(source,clustering);
    if(CloudSettings.x>0.0) color+=skyNebula(source);
    if(StarSettings.x>0.0 && StarSettings.y>0.0) {
        color+=skyStars(source,55.0,11.0,clustering);
        if(Quality>=2) color+=skyStars(source,145.0,97.0,clustering)*0.72;
        if(Quality>=3) color+=skyStars(source,300.0,231.0,clustering)*0.48;
    }
    // Preserve the original direction for decorative sky layers and bodies.
    color+=skyAurora(direction)+skyMeteors(direction);
    color+=GalaxyColor*MeteorSettings.w*exp(-abs(direction.y)*14.0)*0.10;
    skyCelestial(direction,color);
    return color;
}
void skyBlackHoleCinematic(vec3 d,inout vec3 color) {
    if(BlackHoleSettings.x<=0.0) return;
    vec3 center=vec3(sin(BlackHoleSettings.z)*cos(BlackHoleSettings.w),sin(BlackHoleSettings.w),
                     cos(BlackHoleSettings.z)*cos(BlackHoleSettings.w));
    // Yaw basis remains defined at +/-90-degree elevation.
    vec3 side=vec3(cos(BlackHoleSettings.z),0.0,-sin(BlackHoleSettings.z)),up=cross(center,side);
    float alignment=dot(d,center),radius=max(tan(BlackHoleSettings.y),0.001);
    vec2 p=vec2(dot(d,side),dot(d,up))/max(alignment,0.08)/radius;
    float extent=clamp(DiskSettings.y*0.54,2.1,4.8),support=extent+0.8;
    float aa=clamp(max(fwidth(clamp(p.x,-support-1.0,support+1.0)),
                       fwidth(clamp(p.y,-support-1.0,support+1.0))),0.0007,0.035);
    float r=length(p);
    if(alignment<=0.0 || r>=support || abs(p.y)>2.3) return;
    float clock=Time*DiskSettings.z,angle=atan(p.y,p.x);
    // The animated pattern is periodic Cartesian noise, so no longitude seam.
    float spin=clock*0.18,c=cos(spin),s=sin(spin);
    vec2 moving=vec2(c*p.x-s*p.y,s*p.x+c*p.y);
    float gas=skyFbm(vec3(moving*vec2(7.0,4.0),Seed*0.003+clock*0.10-r*0.4),Quality+2);
    float textureAmount=BlackHoleCinematic.w;
    float filament=(0.5+0.5*sin(r*135.0+clock*3.1+gas*textureAmount*5.0));
    filament=0.70+0.30*filament*filament;
    float coreMask=1.0-smoothstep(0.97-aa,0.97+aa,r);
    float absorption=clamp(BlackHoleCinematic.z*0.92,0.0,1.0)*min(BlackHoleSettings.x,1.0);
    color*=1.0-coreMask*absorption;
    float ringWidth=max(BlackHoleAppearance.z,aa*0.70);
    float photon=skyCompactGaussian(r-1.01,max(ringWidth*0.20,aa*0.70),ringWidth*0.80+aa);
    float ring=skyCompactGaussian(r-(1.055+ringWidth*0.75),ringWidth*1.45,ringWidth*4.5+0.05);
    float arcEnvelope=pow(abs(p.y)/max(r,0.001),0.65);
    float bentArc=skyCompactGaussian(r-(1.115+ringWidth*0.85),ringWidth*1.8,ringWidth*5.5+0.09)*arcEnvelope;
    float upperBeaming=0.75+0.25*clamp(p.y/max(r,0.01),-1.0,1.0);
    float ringPattern=mix(1.0,(0.7+gas*0.75)*filament,min(textureAmount,1.0));
    float glow=skyCompactGaussian(r-1.10,0.29+ringWidth,0.90)*(1.0-coreMask);
    float inclination=abs(sin(DiskSettings.x));
    float centralHeight=mix(0.34,0.11,inclination)+0.125*exp(-p.x*p.x*1.2);
    float taper=pow(max(1.0-pow(abs(p.x)/max(extent,0.01),2.0),0.0),0.75);
    float height=max(centralHeight*taper,aa*0.65);
    float disk=skyCompactGaussian(p.y,height,height*3.5+aa)*
               (1.0-smoothstep(extent-0.24,extent,abs(p.x)));
    float diskCore=skyCompactGaussian(p.y,max(height*0.30,aa*0.7),height*1.4+aa)*
                   (1.0-smoothstep(extent*0.70,extent,abs(p.x)));
    float diskGas=skyFbm(vec3(p.x*4.0-clock*0.7,p.y*19.0,Seed*0.013+clock*0.12),Quality+2);
    float diskThreads=0.94+0.06*sin(p.x*24.0-clock*2.2+diskGas*6.0);
    float diskPattern=mix(1.0,(0.45+diskGas*1.1)*diskThreads,min(textureAmount,1.0));
    float diskTaper=0.30+0.70*exp(-pow(p.x/max(extent*0.48,0.1),2.0));
    float beaming=0.87+0.13*sin(angle+clock*0.09);
    vec3 hot=mix(DiskColorB,vec3(1.0),0.68),warm=mix(DiskColorA,DiskColorB,0.35);
    vec3 emission=hot*photon*BlackHoleAppearance.y*3.8;
    emission+=mix(warm,hot,0.50)*ring*BlackHoleAppearance.y*ringPattern*1.4;
    emission+=mix(warm,hot,0.30)*bentArc*BlackHoleCinematic.x*ringPattern*upperBeaming*1.4;
    emission+=warm*glow*BlackHoleAppearance.w*0.35;
    emission+=(warm*disk*1.45+hot*diskCore*2.0)*BlackHoleCinematic.y*diskPattern*beaming*diskTaper;
    // A separate orange gas veil curls outside the sharply focused photon
    // image. Integer angular harmonics are periodic at atan's branch cut.
    float spiral=pow(0.5+0.5*sin(r*24.0+angle*5.0-clock*2.5+gas*6.0),3.0);
    float veilSupport=smoothstep(1.10,1.23,r)*(1.0-smoothstep(1.40,2.07,r));
    float veil=mix(0.22,(0.18+gas*0.85)*spiral,min(textureAmount,1.0))*veilSupport;
    emission+=DiskColorA*veil*BlackHoleAppearance.w*0.36;
    // A finite soft disk glow contributes inside the dark core like the user's
    // reference, while the remainder of the opaque shadow keeps stars hidden.
    float diskGlow=skyCompactGaussian(p.y,0.40,0.90)*(1.0-smoothstep(0.85,extent,abs(p.x)));
    emission+=warm*diskGlow*BlackHoleAppearance.w*BlackHoleCinematic.y*0.14;
    emission+=DiskColorA*coreMask*BlackHoleAppearance.w*0.014;
    float domain=1.0-smoothstep(support-0.28,support,r);
    color+=emission*BlackHoleSettings.x*domain;
}

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

// Original local 3D precipitation. All uniforms are declared in the shared core.
// Climate is sampled from the real world's 3D biome atlas, while roof heights
// retain one-block horizontal resolution. Explicit forced modes override climate.
// Scene inputs are exposed framebuffer colors; this pass alpha-composes particles.

vec2 skyPrecipitationType(vec3 world) {
    if(PrecipitationSettings.x<1.5) {
        if(WorldPrecipGeometry.w<0.5) return vec2(0.0);
        float cellSize=max(WorldPrecipGeometry.x,0.001);
        int dimension=max(int(WorldPrecipGeometry.y+0.5),1);
        int tiles=max(int(WorldPrecipGeometry.z+0.5),1);
        ivec3 cell=ivec3(floor((world-WorldPrecipOrigin)/cellSize));
        if(any(lessThan(cell,ivec3(0))) || any(greaterThanEqual(cell,ivec3(dimension)))) return vec2(0.0);
        ivec2 pixel=ivec2(cell.x+(cell.y%tiles)*dimension,cell.z+(cell.y/tiles)*dimension);
        vec4 data=texelFetch(WeatherVolumeSampler,pixel,0);
        return clamp(data.rg,0.0,1.0)*step(0.5,data.a);
    }
    return PrecipitationSettings.x<2.5?vec2(1.0,0.0):vec2(0.0,1.0);
}
bool skyPrecipitationRoof(vec3 world, out float height) {
    height=-1000000.0;
    if(PrecipitationSettings.y<0.5) return true;
    if(WorldPrecipGeometry.w<0.5 || WorldSurfaceGrid.w<1.0) return false;
    int dimension=max(int(WorldSurfaceGrid.w+0.5),1);
    ivec2 cell=ivec2(floor((world.xz-WorldSurfaceGrid.xy)/max(WorldSurfaceGrid.z,0.001)));
    if(any(lessThan(cell,ivec2(0))) || any(greaterThanEqual(cell,ivec2(dimension)))) return false;
    vec4 data=texelFetch(SurfaceSampler,cell,0);
    if(data.g<0.5) return false;
    // MOTION_BLOCKING returns the first open block above the top blocker.
    height=data.r+0.025;
    return true;
}
float skySnowSegment(vec2 point, vec2 a, vec2 b) {
    vec2 segment=b-a;
    float along=clamp(dot(point-a,segment)/max(dot(segment,segment),0.00001),0.0,1.0);
    return length(point-a-segment*along);
}
float skySnowFlake(vec2 coordinates, float angle, float pixelWidth, float resolved) {
    float radius=length(coordinates);
    if(radius>=1.12) return 0.0;
    float direction=atan(coordinates.y,coordinates.x)+angle;
    float sector=abs(mod(direction+SKY_PI/6.0,SKY_PI/3.0)-SKY_PI/6.0);
    vec2 folded=vec2(cos(sector),sin(sector))*radius;
    float stem=skySnowSegment(folded,vec2(0.0),vec2(0.92,0.0));
    float lower=skySnowSegment(folded,vec2(0.34,0.0),vec2(0.54,0.21));
    float upper=skySnowSegment(folded,vec2(0.60,0.0),vec2(0.78,0.18));
    float shape=1.0-smoothstep(max(0.026-pixelWidth,0.0),0.038+pixelWidth,min(stem,min(lower,upper)));
    float support=1.0-smoothstep(0.90,1.12,radius);
    float unresolved=exp(-radius*radius*3.0)*support;
    return mix(unresolved,shape*support,resolved);
}
mat3 skyWeatherLatticeRotation(float field) {
    vec3 angle=vec3(0.37,0.71,0.23)+field*vec3(1.13,0.83,1.47);
    vec3 c=cos(angle),s=sin(angle);
    mat3 x=mat3(1,0,0, 0,c.x,s.x, 0,-s.x,c.x);
    mat3 y=mat3(c.y,0,-s.y, 0,1,0, s.y,0,c.y);
    mat3 z=mat3(c.z,s.z,0, -s.z,c.z,0, 0,0,1);
    return z*y*x;
}
vec3 skyWorldWeatherParticles(vec3 scene, vec3 origin, vec3 direction, float distanceLimit, vec3 sun, bool snow) {
    float rayFootprint=max(length(dFdx(direction)),length(dFdy(direction)));
    float amount=skyWeatherAmounts().x;
    float brightness=snow?SnowSettings.w:RainSettings.w;
    if(amount<=0.0 || brightness<=0.0) return scene;
    float limit=min(distanceLimit,48.0);
    if(limit<=0.0) return scene;
    vec2 wind=vec2(sin(WeatherMotion.x),cos(WeatherMotion.x));
    vec2 across=vec2(wind.y,-wind.x);
    float windTilt=snow?PrecipitationSettings.w*0.55:0.10;
    vec3 fallAxis=normalize(vec3(-wind.x*windTilt,1.0,-wind.y*windTilt));
    float clock=Time*WeatherSettings.z*(snow?SnowSettings.z*3.0:RainSettings.y*13.0);
    float cellSize=3.4;
    float light=0.0;
    int budget=Quality<=1?24:(Quality==2?36:48);
    float density=snow?SnowSettings.x:RainSettings.x;
    // Independent fixed world lattices keep the same total three candidates,
    // while preventing coincident empty lattice planes from forming sky stripes.
    // Rotation is fixed, never tied to the camera or to the animation clock.
    for(int field=0;field<3;field++) {
    mat3 rotation=skyWeatherLatticeRotation(float(field)),inverseRotation=transpose(rotation);
    vec3 fieldDirection=rotation*direction,fieldFallAxis=rotation*fallAxis;
    vec3 fieldOrigin=(rotation*(origin+fallAxis*clock)+vec3(13.71,33.37,7.19)*float(field))/cellSize;
    vec3 initialCell=floor(fieldOrigin),localOrigin=fract(fieldOrigin)*cellSize;
    vec3 cell=initialCell,relativeCell=vec3(0.0),stepDirection=sign(fieldDirection);
    vec3 inverseDirection=1.0/max(abs(fieldDirection),vec3(0.00001));
    vec3 nextBoundary=mix(localOrigin,vec3(cellSize)-localOrigin,step(vec3(0.0),fieldDirection))*inverseDirection;
    vec3 boundaryStride=cellSize*inverseDirection;
    if(abs(fieldDirection.x)<0.00001) nextBoundary.x=1000000.0;
    if(abs(fieldDirection.y)<0.00001) nextBoundary.y=1000000.0;
    if(abs(fieldDirection.z)<0.00001) nextBoundary.z=1000000.0;
    float entry=0.0;
    for(int i=0;i<48;i++) {
        if(i>=budget || entry>=limit) break;
        float exitDistance=min(limit,min(nextBoundary.x,min(nextBoundary.y,nextBoundary.z)));
        for(int candidate=0;candidate<1;candidate++) {
            vec3 id=cell+vec3(float(field)*137.7,Seed*0.017+(snow?43.1:0.0),float(field)*19.3);
            vec3 random=skyHash(id);
            float selection=skyHash(id+vec3(17.1,13.7,9.2)).z;
            if(selection>=clamp(amount*density/3.0,0.0,1.0)) continue;
            // Margin contains the complete rain segment or fluttered flake plus
            // its finite AA support. Candidates cannot disappear on DDA faces.
            vec3 center=relativeCell*cellSize+(0.35+random*0.30)*cellSize;
            float flutterPhase=Time*WeatherSettings.z*SnowSettings.z*(1.2+random.x)+random.y*31.0;
            if(snow) {
                float amplitude=PrecipitationSettings.z*0.28;
                vec2 flutter=(across*sin(flutterPhase)+wind*cos(flutterPhase*0.73+random.z*9.0)*0.5)*amplitude;
                center+=rotation*vec3(flutter.x,cos(flutterPhase*0.82)*amplitude*0.30,flutter.y);
            }
            vec3 worldCenter=origin+inverseRotation*(center-localOrigin);
            vec2 type=skyPrecipitationType(worldCenter);
            if((snow?type.y:type.x)<0.5) continue;
            float roof;
            if(!skyPrecipitationRoof(worldCenter,roof)) continue;
            float alongRay=0.0,particleLight=0.0;
            if(snow) {
                float rotation=flutterPhase*0.53+random.z*16.0;
                vec3 normal=normalize(vec3(sin(rotation)*0.55,0.28+random.y*0.62,cos(rotation)*0.55));
                float denominator=dot(direction,normal);
                float facing=smoothstep(0.025,0.14,abs(denominator));
                float safe=denominator<0.0?-max(abs(denominator),0.025):max(abs(denominator),0.025);
                alongRay=dot(inverseRotation*(center-localOrigin),normal)/safe;
                if(alongRay<entry || alongRay>exitDistance || alongRay<=0.0 || alongRay>=limit) continue;
                vec3 point=origin+direction*alongRay;
                if(point.y<roof || skyPrecipitationType(point).y<0.5) continue;
                float pointRoof;
                if(!skyPrecipitationRoof(point,pointRoof) || point.y<pointRoof) continue;
                vec3 axis=abs(normal.y)<0.95?vec3(0.0,1.0,0.0):vec3(1.0,0.0,0.0);
                vec3 side=normalize(cross(normal,axis)),up=cross(side,normal);
                vec3 delta=inverseRotation*(localOrigin+fieldDirection*alongRay-center);
                float pixel=clamp(rayFootprint*alongRay*0.70/max(abs(denominator),0.15),0.001,0.20);
                float radius=max(SnowSettings.y,pixel);
                vec2 uv=vec2(dot(delta,side),dot(delta,up))/radius;
                float energy=SnowSettings.y*SnowSettings.y/(radius*radius);
                float resolved=smoothstep(0.28,0.85,SnowSettings.y/max(pixel,0.001));
                particleLight=skySnowFlake(uv,rotation,pixel/radius*0.55,resolved)*energy*facing;
            } else {
                float alignment=dot(direction,fallAxis);
                vec3 toOrigin=inverseRotation*(localOrigin-center);
                float alongOrigin=dot(direction,toOrigin),alongDrop=dot(fallAxis,toOrigin);
                float denominator=max(1.0-alignment*alignment,0.00001);
                float halfLength=0.09+RainSettings.z*0.64;
                float lower=max(-halfLength,(roof-worldCenter.y)/max(fallAxis.y,0.01));
                if(lower>=halfLength) continue;
                float segment=clamp((alongDrop-alignment*alongOrigin)/denominator,lower,halfLength);
                float projected=dot(center+fieldFallAxis*segment-localOrigin,fieldDirection);
                if(projected<=0.0 || projected>=limit) continue;
                alongRay=clamp(projected,entry,exitDistance);
                vec3 closest=worldCenter+fallAxis*segment;
                if(skyPrecipitationType(closest).x<0.5) continue;
                float pointRoof;
                if(!skyPrecipitationRoof(closest,pointRoof) || closest.y<pointRoof) continue;
                vec3 delta=localOrigin+fieldDirection*alongRay-center-fieldFallAxis*segment;
                float radius=0.012+0.007*random.y;
                float pixel=clamp(rayFootprint*alongRay*0.70,0.003,0.08);
                float width=max(radius,pixel);
                float support=1.0-smoothstep(width*2.3,width*3.0,length(delta));
                particleLight=exp(-dot(delta,delta)/(width*width))*radius/width*support;
            }
            float nearFade=smoothstep(0.20,1.50,alongRay),farFade=1.0-smoothstep(35.0,48.0,alongRay);
            light+=particleLight*nearFade*farFade*(0.70+random.z*0.60);
        }
        if(nextBoundary.x<=nextBoundary.y && nextBoundary.x<=nextBoundary.z) {
            entry=nextBoundary.x;nextBoundary.x+=boundaryStride.x;cell.x+=stepDirection.x;relativeCell.x+=stepDirection.x;
        } else if(nextBoundary.y<=nextBoundary.z) {
            entry=nextBoundary.y;nextBoundary.y+=boundaryStride.y;cell.y+=stepDirection.y;relativeCell.y+=stepDirection.y;
        } else {
            entry=nextBoundary.z;nextBoundary.z+=boundaryStride.z;cell.z+=stepDirection.z;relativeCell.z+=stepDirection.z;
        }
    }
    }
    float alpha=1.0-exp(-light*brightness*0.82);
    float daylight=smoothstep(-0.07,0.18,sun.y);
    vec3 color=snow?SnowColor*(0.30+0.70*daylight):RainColor;
    return mix(scene,clamp(color,0.0,1.0),alpha);
}
vec3 skyWorldPrecipitation(vec3 scene, vec3 origin, vec3 direction, float distanceLimit, vec3 sun) {
    // Original recipes retain the exact established rain draw, including its
    // candidate positions. No climate or roof texture is read in compatibility.
    if(PrecipitationSettings.x<0.5) return skyWorldRain(scene,origin,direction,distanceLimit);
    float mode=PrecipitationSettings.x;
    vec3 result=scene;
    if(mode<1.5 && WorldPrecipGeometry.w<0.5) return scene;
    if(mode>1.5 && mode<2.5 || mode<1.5 && WorldPrecipPresence.x>0.5) result=skyWorldWeatherParticles(result,origin,direction,distanceLimit,sun,false);
    if(mode>2.5 || mode<1.5 && WorldPrecipPresence.y>0.5) result=skyWorldWeatherParticles(result,origin,direction,distanceLimit,sun,true);
    return result;
}

// Original world-space environment composition, shared by WebGL2 and GLSL 150.
// scene is an already exposed framebuffer color. Cloud/fog sources are exposed
// once before alpha composition; this is an artistic display-space approximation,
// not a physical multiple-scattering or color-management replacement.
// Uniforms, hash/noise, solar functions and skyWeatherAmounts live in the bundle.

int skyVolumeSteps() { return Quality<=1?16:(Quality==2?24:32); }
int skyLightSteps() { return Quality<=1?2:(Quality==2?3:4); }
int skyAirSteps() { return Quality<=1?12:(Quality==2?20:28); }
vec3 skyDisplaySource(vec3 radiance) {
    return vec3(1.0)-exp(-max(radiance,vec3(0.0))*CloudSettings.w);
}
float skyCloudPhase(float cosine, float anisotropy) {
    float g=clamp(anisotropy,0.0,0.9);
    return min((1.0-g*g)/pow(max(1.0+g*g-2.0*g*cosine,0.025),1.5),18.0);
}
bool skyWorldCloudInterval(vec3 origin, vec3 direction, float limit, out vec2 interval) {
    float lower=VolumeCloudGeometry.x,upper=lower+VolumeCloudGeometry.y;
    interval=vec2(0.0);
    if(limit<=0.0) return false;
    if(abs(direction.y)<0.00001) {
        if(origin.y<=lower || origin.y>=upper) return false;
        interval=vec2(0.0,limit); return true;
    }
    vec2 planes=(vec2(lower,upper)-origin.y)/direction.y;
    interval=vec2(max(min(planes.x,planes.y),0.0),min(max(planes.x,planes.y),limit));
    return interval.y>interval.x;
}
float skyWorldCloudDensity(vec3 world, bool details) {
    if(VolumeCloudSettings.x<=0.0 || VolumeCloudSettings.y<=0.0 || VolumeCloudSettings.z<=0.0) return 0.0;
    float height=(world.y-VolumeCloudGeometry.x)/max(VolumeCloudGeometry.y,8.0);
    if(height<=0.0 || height>=1.0) return 0.0;
    float profile=smoothstep(0.0,0.14,height)*(1.0-smoothstep(0.66,1.0,height));
    vec2 weather=skyWeatherAmounts();
    float coverage=clamp(VolumeCloudSettings.y+weather.x*0.16+weather.y*0.10,0.0,1.0);
    vec2 wind=vec2(sin(VolumeCloudMotion.y),cos(VolumeCloudMotion.y));
    // Coordinates remain world-anchored: moving the camera changes the sampled
    // slice, rather than moving an angular texture together with the camera.
    vec3 p=world*VolumeCloudSettings.w+vec3(Seed*0.0031,Seed*0.0019,Seed*0.0023);
    p.xz-=wind*Time*VolumeCloudMotion.x*0.026;
    p.y+=Time*VolumeCloudMotion.z*0.021;
    float broad=skyFbm(p*vec3(1.0,1.75,1.0),details?3:2)*(details?1.0:1.1666667);
    float mound=1.0-abs(skyNoise(p*1.9+vec3(5.8,12.2,8.9))*2.0-1.0);
    float macro=skyNoise(p*0.63+vec3(8.2,13.1,2.9));
    float shape=broad*0.66+mound*0.12+macro*0.22;
    // Tall dense regions survive higher into the slab; weaker regions form
    // lower rounded billows instead of a uniformly flat opaque ceiling.
    float threshold=mix(0.70,0.18,coverage)+height*height*0.12;
    float body=smoothstep(threshold-0.075,threshold+0.16,shape);
    if(details && VolumeCloudGeometry.w>0.0) {
        float holes=skyNoise(p*5.1+vec3(11.7,4.2,17.3)+Time*VolumeCloudMotion.z*0.013);
        body=max(body-VolumeCloudGeometry.w*(1.0-holes)*(1.0-body)*0.80,0.0);
    }
    return body*profile*VolumeCloudSettings.x*VolumeCloudSettings.z*(1.0+weather.y*0.35);
}
float skyWorldSunTransmission(vec3 world, vec3 sun) {
    if(sun.y<=0.0005 || SunSettings.x<=0.0 || VolumeCloudSettings.x<=0.0) return 1.0;
    vec2 span;
    if(!skyWorldCloudInterval(world,sun,VolumeCloudGeometry.z,span)) return 1.0;
    // Shadow softness changes sampling width through the same world density;
    // it does not move a separate shadow texture independently of the clouds.
    int samples=skyLightSteps();
    float stride=(span.y-span.x)/float(samples),opticalDepth=0.0;
    float spread=max(LightingDetail.z,0.1);
    vec3 tangent=vec3(sun.z,0.0,-sun.x);
    tangent=length(tangent)>0.001?normalize(tangent):vec3(1.0,0.0,0.0);
    for(int i=0;i<4;i++) {
        if(i>=samples) break;
        vec3 p=world+sun*(span.x+(float(i)+0.5)*stride);
        p+=tangent*((float(i)+0.5)/float(samples)-0.5)*spread*12.0;
        float density=skyWorldCloudDensity(p,false);
        // The cheap light density omits fine erosion and is deliberately softer.
        opticalDepth+=density*stride*0.035;
    }
    return exp(-min(opticalDepth,24.0));
}
vec3 skyWorldClouds(vec3 scene, vec3 origin, vec3 direction, float distanceLimit, vec3 sun, vec3 moon) {
    if(VolumeCloudSettings.x<=0.0 || VolumeCloudSettings.y<=0.0 || VolumeCloudSettings.z<=0.0) return scene;
    float cloudLimit=min(distanceLimit,VolumeCloudGeometry.z);
    vec2 span;
    if(!skyWorldCloudInterval(origin,direction,cloudLimit,span)) return scene;
    int steps=skyVolumeSteps();
    float stride=(span.y-span.x)/float(steps),transmission=1.0;
    vec3 accumulated=vec3(0.0);
    float daytime=smoothstep(-0.07,0.18,sun.y);
    float moonlight=MoonSettings.x*(0.5+0.5*cos(MoonSurface.x))*smoothstep(-0.03,0.16,moon.y)*(1.0-daytime);
    vec3 direct=skySolarIrradiance(sun)*CloudLighting.y;
    vec3 ambient=(vec3(0.20,0.30,0.46)*daytime+MoonColor*(DaySettings.z*1.8+moonlight*0.13))*CloudLighting.x;
    float phase=0.52+0.14*skyCloudPhase(dot(direction,sun),VolumeCloudMotion.w);
    vec2 weather=skyWeatherAmounts();
    for(int i=0;i<32;i++) {
        if(i>=steps || transmission<0.008) break;
        float distanceAlong=span.x+(float(i)+0.5)*stride;
        vec3 point=origin+direction*distanceAlong;
        float density=skyWorldCloudDensity(point,true);
        density*=1.0-smoothstep(VolumeCloudGeometry.z*0.78,VolumeCloudGeometry.z,distanceAlong);
        if(density<=0.001) continue;
        float alpha=1.0-exp(-density*stride*0.035);
        float sunlight=skyWorldSunTransmission(point,sun);
        float normalizedHeight=clamp((point.y-VolumeCloudGeometry.x)/VolumeCloudGeometry.y,0.0,1.0);
        vec3 radiance=VolumeCloudColor*(ambient*(0.58+0.42*normalizedHeight)+direct*phase*(0.12+0.88*sunlight));
        radiance*=1.0-WeatherSettings.w*weather.y*0.68;
        accumulated+=transmission*alpha*skyDisplaySource(radiance);
        transmission*=1.0-alpha;
    }
    return scene*transmission+accumulated;
}
float skyWorldFogDensity(vec3 point) {
    float heightExponent=clamp(-(point.y-FogSettings.w)*FogSettings.z,-12.0,6.0);
    float density=FogSettings.x*FogSettings.y*exp(heightExponent);
    if(FogMotion.x>0.0) {
        vec3 p=point*FogMotion.y+vec3(Seed*0.0027,Seed*0.0033,Seed*0.0017);
        p.xz+=vec2(0.040,-0.026)*Time*FogMotion.z;
        density*=mix(1.0,0.35+1.30*skyNoise(p),FogMotion.x);
    }
    return min(density,0.30);
}
vec3 skyWorldAir(vec3 scene, vec3 origin, vec3 direction, float distanceLimit, vec3 sun) {
    bool fogOn=FogSettings.x>0.0 && FogSettings.y>0.0;
    bool shaftsOn=CloudLighting.w>0.0 && LightingDetail.x>0.0 && VolumeCloudSettings.x>0.0 && VolumeCloudSettings.y>0.0 && VolumeCloudSettings.z>0.0 && sun.y>0.0005 && SunSettings.x>0.0;
    if(!fogOn && !shaftsOn) return scene;
    float limit=min(distanceLimit,max(fogOn?FogMotion.w:0.0,shaftsOn?LightingDetail.y:0.0));
    if(limit<=0.0) return scene;
    int steps=skyAirSteps();
    float stride=limit/float(steps),transmission=1.0;
    vec3 accumulated=vec3(0.0);
    float daylight=smoothstep(-0.07,0.18,sun.y);
    vec3 fogSource=FogColor*mix(0.075,0.78,daylight)+skySolarIrradiance(sun)*0.10;
    vec3 exposedFogSource=skyDisplaySource(fogSource);
    // A separate bounded air phase avoids a giant saturated disk when players
    // combine maximum shaft strength, medium density and forward anisotropy.
    float phase=0.10+0.035*min(skyCloudPhase(dot(direction,sun),LightingDetail.w),5.0);
    vec3 sunSource=skyDisplaySource(skySolarIrradiance(sun)*CloudLighting.w*phase*3.2);
    for(int i=0;i<28;i++) {
        if(i>=steps || transmission<0.005) break;
        float t=(float(i)+0.5)*stride;
        vec3 point=origin+direction*t;
        float density=fogOn && t<FogMotion.w?skyWorldFogDensity(point):0.0;
        float air=0.0;
        vec3 source=exposedFogSource*density;
        if(shaftsOn && t<LightingDetail.y) {
            float sunlight=skyWorldSunTransmission(point,sun);
            float haze=exp(clamp(-(point.y-FogSettings.w)*0.004,-6.0,2.0));
            air=LightingDetail.x*0.004*haze;
            // View-path air integrates the actual animated cloud density's solar
            // transmission; it is neither radial-screen blur nor a sky decal.
            source+=sunSource*sunlight*air;
        }
        float totalDensity=density+air;
        float alpha=1.0-exp(-totalDensity*stride);
        accumulated+=transmission*alpha*source/max(totalDensity,0.000001);
        transmission*=1.0-alpha;
    }
    return scene*transmission+accumulated;
}
vec3 skyWorldRain(vec3 scene, vec3 origin, vec3 direction, float distanceLimit) {
    float rayFootprint=max(length(dFdx(direction)),length(dFdy(direction)));
    vec2 weather=skyWeatherAmounts();
    float amount=weather.x;
    if(amount<=0.0 || RainSettings.w<=0.0) return scene;
    float limit=min(distanceLimit,48.0);
    if(limit<=0.0) return scene;
    vec2 wind=vec2(sin(WeatherMotion.x),cos(WeatherMotion.x));
    vec3 fallAxis=normalize(vec3(-wind.x*0.10,1.0,-wind.y*0.10));
    float cellSize=3.4,clock=Time*WeatherSettings.z*RainSettings.y*13.0;
    vec3 fieldOrigin=(origin+fallAxis*clock)/cellSize;
    vec3 initialCell=floor(fieldOrigin),localOrigin=fract(fieldOrigin)*cellSize;
    vec3 cell=initialCell,relativeCell=vec3(0.0),stepDirection=sign(direction);
    vec3 inverseDirection=1.0/max(abs(direction),vec3(0.00001));
    vec3 nextBoundary=mix(localOrigin,vec3(cellSize)-localOrigin,step(vec3(0.0),direction))*inverseDirection;
    vec3 boundaryStride=cellSize*inverseDirection;
    if(abs(direction.x)<0.00001) nextBoundary.x=1000000.0;
    if(abs(direction.y)<0.00001) nextBoundary.y=1000000.0;
    if(abs(direction.z)<0.00001) nextBoundary.z=1000000.0;
    float entry=0.0,light=0.0;
    int budget=Quality<=1?24:(Quality==2?36:48);
    // Exact finite ray/segment proximity in each traversed 3D cell. This avoids
    // subpixel streaks being missed by a coarse fixed-step fog march. Rain shares
    // the reconstructed world ray and stops before the first terrain surface.
    for(int i=0;i<48;i++) {
        if(i>=budget || entry>=limit) break;
        float exitDistance=min(limit,min(nextBoundary.x,min(nextBoundary.y,nextBoundary.z)));
        for(int drop=0;drop<3;drop++) {
            vec3 id=cell+vec3(float(drop)*137.7,Seed*0.017,float(drop)*19.3);
            vec3 random=skyHash(id);
            float selection=skyHash(id+vec3(17.1,13.7,9.2)).z;
            float exists=1.0-step(clamp(amount*RainSettings.x/3.0,0.0,1.0),selection);
            if(exists<=0.0) continue;
            vec3 center=relativeCell*cellSize+(0.27+random*0.46)*cellSize;
            vec3 toOrigin=localOrigin-center;
            float alignment=dot(direction,fallAxis);
            float alongOrigin=dot(direction,toOrigin),alongDrop=dot(fallAxis,toOrigin);
            float denominator=max(1.0-alignment*alignment,0.00001);
            float halfLength=0.09+RainSettings.z*0.64;
            float segment=clamp((alongDrop-alignment*alongOrigin)/denominator,-halfLength,halfLength);
            float alongRay=clamp(dot(center+fallAxis*segment-localOrigin,direction),entry,exitDistance);
            vec3 delta=localOrigin+direction*alongRay-center-fallAxis*segment;
            float radius=0.012+0.007*random.y;
            float footprint=clamp(rayFootprint*alongRay*0.7,0.003,0.08);
            float width=max(radius,footprint);
            float energy=radius/max(width,0.0001);
            float streak=exp(-dot(delta,delta)/(width*width))*energy;
            float nearFade=smoothstep(0.20,1.50,alongRay),farFade=1.0-smoothstep(35.0,48.0,alongRay);
            light+=streak*nearFade*farFade*(0.70+random.z*0.60);
        }
        // Move along exactly one earliest face. Ties visit the intervening
        // zero-width cells, with bounded iterations and no infinite loop.
        if(nextBoundary.x<=nextBoundary.y && nextBoundary.x<=nextBoundary.z) {
            entry=nextBoundary.x;nextBoundary.x+=boundaryStride.x;cell.x+=stepDirection.x;relativeCell.x+=stepDirection.x;
        } else if(nextBoundary.y<=nextBoundary.z) {
            entry=nextBoundary.y;nextBoundary.y+=boundaryStride.y;cell.y+=stepDirection.y;relativeCell.y+=stepDirection.y;
        } else {
            entry=nextBoundary.z;nextBoundary.z+=boundaryStride.z;cell.z+=stepDirection.z;relativeCell.z+=stepDirection.z;
        }
    }
    float alpha=1.0-exp(-light*RainSettings.w*0.82);
    return mix(scene,clamp(RainColor,0.0,1.0),alpha);
}
vec3 skyComposeEnvironment(vec3 scene, vec3 origin, vec3 direction, float sceneDistance, bool hitGeometry) {
    vec2 weather=skyWeatherAmounts();
    bool mediumOn=EnvironmentStage!=2;
    bool cloudOn=mediumOn && VolumeCloudSettings.x>0.0 && VolumeCloudSettings.y>0.0 && VolumeCloudSettings.z>0.0;
    bool fogOn=mediumOn && FogSettings.x>0.0 && FogSettings.y>0.0;
    bool shaftOn=CloudLighting.w>0.0 && LightingDetail.x>0.0 && cloudOn;
    bool shadowOn=CloudLighting.z>0.0 && cloudOn && hitGeometry;
    float mode=PrecipitationSettings.x;
    bool particlesOn=mode<0.5?RainSettings.w>0.0:(mode<1.5?(RainSettings.w>0.0 || SnowSettings.w>0.0):(mode<2.5?RainSettings.w>0.0:SnowSettings.w>0.0));
    bool precipitationOn=EnvironmentStage!=1 && weather.x>0.0 && particlesOn;
    if(!cloudOn && !fogOn && !shaftOn && !shadowOn && !precipitationOn) return scene;
    float longest=max(max(VolumeCloudGeometry.z,FogMotion.w),max(LightingDetail.y,48.0));
    float limit=hitGeometry?max(sceneDistance-PipelineSettings.z,0.0):longest;
    vec3 sun,moon,sunSide,moonSide;
    skyDayDirections(sun,moon,sunSide,moonSide);
    vec3 result=scene;
    if(shadowOn && sun.y>0.0005 && SunSettings.x>0.0) {
        vec3 surface=origin+direction*max(sceneDistance,0.0);
        float direct=skyWorldSunTransmission(surface+sun*0.05,sun);
        float sunlight=smoothstep(0.005,0.16,sun.y);
        result*=1.0-CloudLighting.z*(1.0-direct)*sunlight*0.70;
    }
    if(mediumOn) {
        result=skyWorldClouds(result,origin,direction,limit,sun,moon);
        result=skyWorldAir(result,origin,direction,limit,sun);
    }
    if(precipitationOn) result=skyWorldPrecipitation(result,origin,direction,limit,sun);
    return max(result,vec3(0.0));
}

void main(){
    vec3 scene=texture(SceneSampler,screenUV).rgb;
    float depth=texture(DepthSampler,screenUV).r;
    vec4 viewFar=InverseProjection*vec4(screenUV*2.0-1.0,-1.0,1.0);
    vec4 eyeH=InverseProjection*vec4(0.0,0.0,1.0,0.0);
    vec3 eyeView=abs(eyeH.w)>1e-7?eyeH.xyz/eyeH.w:vec3(0.0);
    vec3 eyeWorld=InverseViewRotation*eyeView+InverseViewTranslation;
    vec3 direction=normalize(InverseViewRotation*(viewFar.xyz/viewFar.w-eyeView));
    vec4 viewPoint=InverseProjection*vec4(screenUV*2.0-1.0,depth*2.0-1.0,1.0);
    bool hit=depth<0.999999;
    vec3 relative=InverseViewRotation*(viewPoint.xyz/viewPoint.w)+InverseViewTranslation-eyeWorld;
    float distance=hit?length(relative):max(VolumeCloudGeometry.z,max(FogMotion.w,LightingDetail.y));
    if(hit)direction=normalize(relative);
    vec3 color=skyComposeEnvironment(scene,CameraPosition+eyeWorld,direction,distance,hit);
    if(OutputDeltaEncoding==1)color-=scene;
    else if(OutputDeltaEncoding==2)color=(color-scene)*(127./255.)+128./255.;
    fragColor=vec4(color,1.0);
}
