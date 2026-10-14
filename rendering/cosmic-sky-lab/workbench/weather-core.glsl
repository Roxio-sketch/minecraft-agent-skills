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
