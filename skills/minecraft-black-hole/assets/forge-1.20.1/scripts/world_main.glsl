vec3 sceneLinear(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));}
vec3 sceneDisplay(vec3 c){return mix(c*12.92,1.055*pow(max(c,vec3(0)),vec3(1.0/2.4))-.055,step(vec3(.0031308),c));}

// Artistic environment lens: a continuous angular map around the entire mass.
// Nearby terrain belongs to this environment layer. Only actual foreground
// entities are held in front; a mass-plane test on all terrain cuts off half
// the ring when the camera stands near the ground.
bool foregroundEntity(vec2 uv,float depth){
    if(depth>=.999999||ForegroundCount==0)return false;
    // The entity must have written nearer depth than the environment capture.
    // Its expanded bounds alone must not mask the grass under its feet.
    if(EnvironmentReady>.5&&depth>=texture(Sampler4,uv).r-.0000002)return false;
    vec3 point=scenePosition(uv,depth);
    // The CPU classifies the WHOLE actor, including a body crossing the mass
    // plane. Splitting the reconstructed surface here tears limbs apart and
    // leaks the rejected half into the background source image.
    for(int i=0;i<ForegroundCount;i++){
        vec3 lo=texelFetch(Sampler2,ivec2(i*2,0),0).xyz;
        vec3 hi=texelFetch(Sampler2,ivec2(i*2+1,0),0).xyz;
        if(all(greaterThanEqual(point,lo))&&all(lessThanEqual(point,hi)))return true;
    }
    return false;
}

// Color is bilinear. Check every participating texel, so a foreground player
// cannot bleed into the warped scene or become a false secondary image.
bool environmentFootprint(vec2 uv){
    if(!onScreen(uv))return false;
    ivec2 size=textureSize(Sampler0,0);
    ivec2 base=ivec2(floor(uv*vec2(size)-.5));
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        ivec2 pixel=clamp(base+ivec2(x,y),ivec2(0),size-1);
        vec2 tap=(vec2(pixel)+.5)/vec2(size);
        if(EnvironmentReady<.5&&foregroundEntity(tap,texelFetch(Sampler1,pixel,0).r))return false;
    }
    return true;
}

vec3 environmentColor(vec2 uv){
    ivec2 size=textureSize(Sampler0,0);
    vec2 grid=uv*vec2(size)-.5;ivec2 base=ivec2(floor(grid));
    vec3 colors[4];
    for(int i=0;i<4;i++){
        ivec2 pixel=clamp(base+ivec2(i%2,i/2),ivec2(0),size-1);
        vec2 tap=(vec2(pixel)+.5)/vec2(size);
        bool actor=foregroundEntity(tap,texelFetch(Sampler1,pixel,0).r);
        // Restore environment hidden by a foreground actor BEFORE filtering.
        colors[i]=EnvironmentReady>.5&&actor?texelFetch(Sampler3,pixel,0).rgb:texelFetch(Sampler0,pixel,0).rgb;
    }
    vec2 f=fract(grid);
    return mix(mix(colors[0],colors[1],f.x),mix(colors[2],colors[3],f.x),f.y);
}

vec2 lensSourceUv;
float lensAvailability;
void resolveLensSource(vec3 rd,bool foreground){
    lensSourceUv=texCoord;lensAvailability=0.0;
    if(foreground||LensStrength<=.00001||uBend<=.00001)return;
    vec3 massRelative=CenterRelative-CameraOriginRelative;
    float distance=length(massRelative);
    if(distance<=uCore*WorldScale*1.01)return;
    vec3 axis=massRelative/distance;
    float cosine=clamp(dot(rd,axis),-1.0,1.0);
    vec3 tangent=rd-axis*cosine;
    float tangentLength=length(tangent),theta=atan(tangentLength,cosine);
    if(theta>=1.50||tangentLength<.000001)return;
    float impact=distance*tangentLength/WorldScale;
    float window=1.0-smoothstep(LensRadius*.75,LensRadius,impact);
    if(window<=.00001)return;
    float coreAngle=uCore*WorldScale/distance;
    float soft=max(coreAngle*.25,.00001);
    float alpha=clamp(LensStrength,0.0,1.0)*uBend*2.0*coreAngle
        *theta/(theta*theta+soft*soft)*window;
    float beta=theta-alpha;
    // This snapshot represents one source hemisphere, not repeated 2*pi wraps.
    if(abs(beta)>=1.50)return;
    vec3 outgoing=axis*cos(beta)+(tangent/tangentLength)*sin(beta);
    vec4 clip=ViewProjection*vec4(outgoing,0.0);
    if(clip.w<=.001)return;
    vec2 uv=clip.xy/clip.w*.5+.5;
    if(!environmentFootprint(uv))return;
    float edge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
    lensSourceUv=uv;lensAvailability=smoothstep(.002,.035,edge);
}

void main(){
    ivec2 size=textureSize(Sampler0,0);
    ivec2 pixel=clamp(ivec2(texCoord*vec2(size)),ivec2(0),size-1);
    vec3 original=texelFetch(Sampler0,pixel,0).rgb;
    vec3 rd=normalize(scenePosition(texCoord,1.0)-CameraOriginRelative);
    float depth=texelFetch(Sampler1,pixel,0).r;
    bool foreground=foregroundEntity(texCoord,depth);
    resolveLensSource(rd,foreground);
    // Calculate the resolved map's footprint before any per-pixel return.
    vec2 dx=dFdx(lensSourceUv),dy=dFdy(lensSourceUv);
    dx*=min(1.0,8.0/max(length(dx*vec2(size)),.0001));
    dy*=min(1.0,8.0/max(length(dy*vec2(size)),.0001));
    vec3 ro=(CameraOriginRelative-CenterRelative)/WorldScale;
    vec2 support=bhSphere(ro,rd,max(BH_TRACE_BOUND,LensRadius));
    BHResult hit;hit.light=vec3(0);hit.transmission=1.0;
    if(!foreground&&support.y>0.0){
        // Environment geometry has been remapped by the lens. Its ORIGINAL
        // depth cannot clip the mass/disk into a horizontal half-silhouette.
        // Actual foreground entities were already separated above.
        sceneRay=rd;sceneLimit=1e8;
        hit=bhTrace(ro,rd);
    }
    if(LensProbeMode>.5){
        if(LensProbeMode>1.5){fragColor=vec4(lensAvailability,0,0,1);return;}
        float sourceDepth=texture(Sampler1,lensSourceUv).r;
        vec3 source=scenePosition(lensSourceUv,sourceDepth);
        bool entityDepth=EnvironmentReady<.5||sourceDepth<texture(Sampler4,lensSourceUv).r-.0000002;
        bool playerSource=entityDepth&&sourceDepth<.999999&&all(greaterThanEqual(source,ProbeBoundsMin))&&all(lessThanEqual(source,ProbeBoundsMax));
        bool replaced=foregroundEntity(lensSourceUv,sourceDepth);
        float coverage=playerSource&&!replaced?lensAvailability*hit.transmission:0.0;
        bool moved=length((lensSourceUv-texCoord)*vec2(size))>2.0;
        fragColor=vec4(coverage,moved?coverage:0.0,0,1);return;
    }
    if(foreground){fragColor=vec4(original,1.0);return;}
    vec3 background=original;
    if(lensAvailability>.00001){
        // Four subpixel taps reduce aliasing in compressed terrain. Each tap
        // retains the foreground exclusion instead of blurring actor colors.
        vec3 sum=vec3(0);float weight=0.0;
        for(int y=0;y<2;y++)for(int x=0;x<2;x++){
            vec2 uv=lensSourceUv+(float(x)-.5)*.5*dx+(float(y)-.5)*.5*dy;
            if(environmentFootprint(uv)){sum+=environmentColor(uv);weight+=1.0;}
        }
        if(weight>0.0)background=mix(original,sum/weight,lensAvailability);
    }
    if(all(equal(hit.light,vec3(0.0)))&&hit.transmission==1.0){fragColor=vec4(background,1.0);return;}
    vec3 emitted=1.0-exp(-max(hit.light,vec3(0))*uExposure);
    fragColor=vec4(sceneDisplay(emitted+hit.transmission*sceneLinear(background)),1.0);
}
