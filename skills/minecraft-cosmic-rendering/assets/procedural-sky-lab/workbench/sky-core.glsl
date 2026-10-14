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
