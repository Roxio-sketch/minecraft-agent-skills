// Original teaching kernel. No Minecraft resources or third-party shader code.
// Shared subset of GLSL 150 / GLSL ES 300. The host supplies uniforms.
// A finite virtual slab behind an aperture, NOT the reference Avaritia algorithm.
uniform float uTime, uSeed, uDensity, uStarSize, uCloud, uDust;
uniform float uDepth, uParallax, uScale, uSpeed, uTwinkle, uExposure;
uniform float uStarSpeed, uTwinkleRate;
uniform float uStarsOn, uCloudsOn, uDustOn;
uniform int uSteps;
uniform vec3 uVoid, uCloudA, uCloudB, uStarTint;

uint cmHash(uint x) {
    x ^= x >> 16u; x *= 0x7feb352du;
    x ^= x >> 15u; x *= 0x846ca68bu;
    return x ^ (x >> 16u);
}
float cmRandom(ivec3 p) {
    uint h = uint(p.x) * 1597334677u ^ uint(p.y) * 3812015801u;
    h ^= uint(p.z) * 2798796415u ^ uint(uSeed);
    return float(cmHash(h) >> 8u) / 16777216.0;
}
float cmNoise(vec3 p) {
    ivec3 i = ivec3(floor(p));
    vec3 f = fract(p); f = f*f*(3.0-2.0*f);
    float a = mix(cmRandom(i), cmRandom(i+ivec3(1,0,0)), f.x);
    float b = mix(cmRandom(i+ivec3(0,1,0)), cmRandom(i+ivec3(1,1,0)), f.x);
    float c = mix(cmRandom(i+ivec3(0,0,1)), cmRandom(i+ivec3(1,0,1)), f.x);
    float d = mix(cmRandom(i+ivec3(0,1,1)), cmRandom(i+ivec3(1,1,1)), f.x);
    return mix(mix(a,b,f.y), mix(c,d,f.y), f.z);
}
float cmFbm(vec3 p) {
    float v = 0.0, a = 0.533333;
    for (int n=0; n<4; n++) {
        v += a * cmNoise(p);
        p = p.yzx * 2.03 + vec3(3.7, 7.1, 1.9); a *= 0.5;
    }
    return v;
}

vec3 cmStars(vec2 p, float level, float footprint) {
    // Move a persistent field at each depth, never the random seed or cell IDs.
    // Differential drift and rotation keep the strata from behaving as one sheet.
    float travel = uStarSpeed > 0.0 ? uTime*uStarSpeed : 0.0;
    float depthRate = mix(1.4,0.32,level);
    float angle = travel*0.035*depthRate;
    p = mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*p;
    p -= travel*depthRate*vec2(0.15,0.055+level*0.035);
    float grid = mix(13.0, 31.0, level);
    vec2 q = p * grid;
    ivec2 cell = ivec2(floor(q));
    vec3 sum = vec3(0.0);
    for (int y=-1; y<=1; y++) for (int x=-1; x<=1; x++) {
        ivec3 id = ivec3(cell + ivec2(x,y), int(level*100.0)+19);
        float choose = cmRandom(id);
        if (choose > uDensity * mix(0.055,0.09,level)) continue;
        vec2 center = vec2(cmRandom(id+ivec3(0,0,13)), cmRandom(id+ivec3(0,0,27)));
        float phase = cmRandom(id+ivec3(0,0,83))*6.2831853;
        // Bounded sub-cell orbits stay within the 3x3 neighbor search.
        center += 0.055*(vec2(sin(travel*0.7+phase),cos(travel*0.5+phase))
                       - vec2(sin(phase),cos(phase)));
        vec2 d = q - vec2(id.xy) - center;
        float variety = cmRandom(id+ivec3(0,0,51));
        float r = uStarSize * mix(0.022,0.065,variety) * mix(1.15,0.65,level);
        // A finite footprint prevents narrow peaks from appearing/disappearing.
        float aa = max(r, min(0.32, footprint * grid * 0.60));
        float core = exp(-dot(d,d)/(aa*aa)) * (r*r)/(aa*aa);
        float halo = exp(-length(d)/(r*3.1)) * 0.025;
        float rays = 0.0;
        if (variety > 0.975 && level < 0.5) {
            float narrow = max(r*0.23, footprint*grid*0.45);
            rays = exp(-min(abs(d.x),abs(d.y))/narrow - length(d)/(r*5.0)) * 0.23;
        }
        float tw = 1.0;
        if (uTwinkle > 0.0) {
            float pulse = 0.5+0.5*sin(uTime*uTwinkleRate*(1.4+variety*1.8)+phase);
            tw = mix(1.0,0.12+2.1*pulse*pulse,uTwinkle);
        }
        vec3 tint = mix(uStarTint, vec3(1.0,0.91,0.79), variety*0.48);
        sum += tint * (core+halo+rays) * tw * mix(7.0,2.7,level);
    }
    return sum * uStarsOn;
}

// entry.xy and depth are measured in virtual material units, z=0 at aperture.
// ray points INTO the material; derivatives must be computed before masking.
vec3 cosmicMaterial(vec3 entry, vec3 ray, float footprint) {
    vec3 C = vec3(0.0);
    float T = 1.0;
    float dz = uDepth / float(uSteps);
    vec2 slope = ray.xy / max(ray.z,0.2) * uParallax;
    float ds = dz * sqrt(1.0 + dot(slope,slope));
    float nextStar = 0.125;
    int starLayer = 0;
    for (int i=0; i<24; i++) {
        if (i >= uSteps) break;
        float fraction = (float(i)+0.5) / float(uSteps);
        float z = fraction * uDepth;
        vec3 p = vec3(entry.xy + slope*z, z) * uScale;
        float flow = uSpeed > 0.0 ? uTime*uSpeed : 0.0;
        p += vec3(flow*0.16, flow*0.06, 0.0);
        vec3 w = vec3(cmNoise(p*0.42+7.0), cmNoise(p*0.42+19.0), cmNoise(p*0.42+37.0))-0.5;
        float n = cmFbm(p*1.45 + w*1.6);
        // A broad, bent band supplies composition; noise supplies its detail.
        // GLSL pow is undefined for a negative base, even for exponent 2.
        float bandCoordinate = (p.y - p.x*0.5 + sin(p.x*0.75)*0.50 + w.x*0.65)*1.15;
        float band = exp(-(bandCoordinate*bandCoordinate));
        float gas = pow(smoothstep(0.40,0.76,n),2.0) * band * uCloud * uCloudsOn;
        float dust = pow(smoothstep(0.40,0.76,cmNoise(p*2.1+w*1.9+41.0)),2.0);
        dust *= band * uDust * uDustOn;
        float sigma = gas*0.37 + dust*1.8;
        float a = 1.0-exp(-sigma*ds);
        vec3 tint = mix(uCloudA,uCloudB,smoothstep(0.48,0.76,n+w.y*0.23));
        vec3 emission = tint * gas * (0.50 + n*n*2.7);
        C += T * emission * (sigma > 0.00001 ? a/sigma : ds);
        T *= 1.0-a;
        // Four explicit star strata: near stars are not attenuated by rear fog.
        if (starLayer < 4 && fraction >= nextStar) {
            float starZ = nextStar*uDepth;
            vec2 sp = (entry.xy + slope*starZ) * uScale;
            // cmStars transports each depth stratum independently.
            C += T * cmStars(sp, nextStar, footprint*uScale*(1.0+starZ/3.0));
            starLayer++; nextStar += 0.25;
        }
    }
    return C + T*uVoid;
}
