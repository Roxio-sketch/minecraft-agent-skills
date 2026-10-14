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
