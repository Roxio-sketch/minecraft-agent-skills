#version 150

#define M_PI 3.1415926535897932384626433832795

#moj_import <fog.glsl>
#moj_import <cosmicmaterial:fantasy_material.glsl>

const int cosmiccount = 10;
const int cosmicoutof = 101;
const float lightmix = 0.2f;

uniform sampler2D Sampler0;
uniform sampler2D CosmicAtlas;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;

uniform float yaw;
uniform float pitch;
uniform float externalScale;

uniform float opacity;
uniform vec4 baseRect;
uniform vec4 maskRect;
uniform int maskMode;
uniform int shieldRimMode;
uniform int paletteMode;
uniform int fmGui;
uniform vec4 fmSpriteRect;
uniform float fmExposure;
uniform vec2 screenSize;

uniform mat2 cosmicuvs[cosmiccount];

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;
in vec4 overlayColor;

out vec4 fragColor;

vec3 fantasyDisplayColor(vec3 linearColor) {
    vec3 mapped=vec3(1.0)-exp(-max(linearColor,vec3(0.0))*fmExposure);
    vec3 lo=12.92*mapped;
    vec3 hi=1.055*pow(mapped,vec3(1.0/2.4))-0.055;
    return mix(lo,hi,step(vec3(0.0031308),mapped));
}

mat4 rotationMatrix(vec3 axis, float angle)
{

    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

void main (void)
{
    vec4 mask = texture(Sampler0, texCoord0.xy);
    vec2 aperture = ((texCoord0 - fmSpriteRect.xy) / max(fmSpriteRect.zw - fmSpriteRect.xy,vec2(0.000001)) - 0.5) * vec2(4.0,-4.0);
    float footprint = max(length(dFdx(aperture)),length(dFdy(aperture)));
    vec2 screenUv=(gl_FragCoord.xy/screenSize-0.5)*2.0;
    screenUv.x*=screenSize.x/max(screenSize.y,1.0);
    float windowFootprint=max(length(dFdx(screenUv*2.0)),length(dFdy(screenUv*2.0)));
    if (mask.a < 0.1) discard;
    if (fmMode >= 0) {
        vec2 slope = fmGui == 1 ? vec2(0.0) : clamp(-fPos.xy / max(abs(fPos.z),0.25),vec2(-0.8),vec2(0.8));
        vec3 linearColor;
        if(fmMode==6)linearColor=fmFantasyWindow(screenUv,vec2(yaw,pitch),windowFootprint);
        else if(fmMode>=7)linearColor=fmNewInteriorWindow(screenUv,vec2(yaw,pitch),windowFootprint);
        else linearColor=fantasyMaterial(vec3(aperture,0.0),normalize(vec3(slope,1.0)),footprint);
        vec3 mapped = fantasyDisplayColor(linearColor);
        float coverage = 1.0;
        if (maskMode == 1) {
            vec2 localUV = clamp((texCoord0-baseRect.xy)/(baseRect.zw-baseRect.xy),0.0,1.0);
            coverage = texture(CosmicAtlas,mix(maskRect.xy,maskRect.zw,localUV)).r;
        }
        // Full replacement must not retain rounding from face-dependent base
        // colors in mix(base, mapped, 1). Keep identity overlays exact as well.
        float blend=coverage*opacity;
        vec3 surface=mapped;
        if(blend<1.0)surface=mix(mask.rgb*vertexColor.rgb,mapped,blend);
        if(overlayColor.a<1.0)surface=mix(overlayColor.rgb,surface,overlayColor.a);
        float materialAlpha=mask.a*vertexColor.a*(fmMode==12?clamp(fmWaterOpacity,0.0,1.0):1.0);
        fragColor = linear_fog(vec4(surface,materialAlpha)*ColorModulator,
            vertexDistance,FogStart,FogEnd,FogColor);
        return;
    }

    float oneOverExternalScale = 1.0/externalScale;

    int uvtiles = 16;

    // background color
    vec4 col = vec4(0.1,0.0,0.0,1.0);

    float pulse = mod(time,400)/400.0;

    col.g = sin(pulse*M_PI*2) * 0.075 + 0.225;
    col.b = cos(pulse*M_PI*2) * 0.05 + 0.3;

    // get ray from camera to fragment
    vec4 dir = normalize(vec4(-fPos, 0));

    // rotate the ray to show the right bit of the sphere for the angle
    float sb = sin(pitch);
    float cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0));

    float sa = sin(-yaw);
    float ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0));

    vec4 ray;

    // draw the layers
    for (int i=0; i<16; i++) {
        int mult = 16-i;

        // get semi-random stuff
        int j = i + 7;
        float rand1 = (j * j * 4321 + j * 8) * 2.0F;
        int k = j + 1;
        float rand2 = (k * k * k * 239 + k * 37) * 3.6F;
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        // random rotation matrix by random rotation around random axis
        vec3 axis = normalize(vec3(sin(rand1), sin(rand2) , cos(rand3)));

        // apply
        ray = dir * rotationMatrix(axis, mod(rand3, 2*M_PI));

        // calcuate the UVs from the final ray
        float rawu = 0.5 + (atan(ray.z,ray.x)/(2*M_PI));
        float rawv = 0.5 + (asin(ray.y)/M_PI);

        // get UV scaled for layers and offset by time;
        float scale = mult*0.5 + 2.75;
        float u = rawu * scale * externalScale;
        //float v = (rawv + time * 0.00006) * scale * 0.6;
        float v = (rawv + time * 0.0002 * oneOverExternalScale) * scale * 0.6 * externalScale;

        vec2 tex = vec2( u, v );

        // tile position of the current uv
        int tu = int(mod(floor(u*uvtiles),uvtiles));
        int tv = int(mod(floor(v*uvtiles),uvtiles));

        // get pseudorandom variants
        //int position = ((1777541 * tu) + (7649689 * tv) + (3612703 * (i+31)) + 1723609 ) ^ 50943779;
        int position = ((171 * tu) + (489 * tv) + (303 * (i+31)) + 17209 ) ^ 10;
        int symbol = int(mod(position, cosmicoutof));
        int rotation = int(mod(pow(tu,float(tv)) + tu + 3 + tv*i, 8));
        bool flip = false;
        if (rotation >= 4) {
            rotation -= 4;
            flip = true;
        }

        // if it's an icon, then add the color!
        if (symbol >= 0 && symbol < cosmiccount) {

            vec2 cosmictex = vec2(1.0,1.0);
            vec4 tcol = vec4(1.0,0.0,0.0,1.0);

            // get uv within the tile
            float ru = clamp(mod(u,1.0)*uvtiles - tu, 0.0, 1.0);
            float rv = clamp(mod(v,1.0)*uvtiles - tv, 0.0, 1.0);

            if (flip) {
                ru = 1.0 - ru;
            }

            float oru = ru;
            float orv = rv;

            // rotate uvs if necessary
            if (rotation == 1) {
                oru = 1.0-rv;
                orv = ru;
            } else if (rotation == 2) {
                oru = 1.0-ru;
                orv = 1.0-rv;
            } else if (rotation == 3) {
                oru = rv;
                orv = 1.0-ru;
            }

            // get the iicon uvs for the tile
            float umin = cosmicuvs[symbol][0][0];
            float umax = cosmicuvs[symbol][1][0];
            float vmin = cosmicuvs[symbol][0][1];
            float vmax = cosmicuvs[symbol][1][1];

            // interpolate based on tile uvs
            cosmictex.x = umin * (1.0-oru) + umax * oru;
            cosmictex.y = vmin * (1.0-orv) + vmax * orv;

            tcol = texture(CosmicAtlas, cosmictex);

            // set the alpha, blending out at the bunched ends
            float a = tcol.r * (0.5 + (1.0/mult) * 1.0) * (1.0-smoothstep(0.15, 0.48, abs(rawv-0.5)));

            // get fancy colours
            float r = (mod(rand1, 29.0)/29.0) * 0.3 + 0.4;
            float g = (mod(rand2, 35.0)/35.0) * 0.4 + 0.6;
            float b = (mod(rand1, 17.0)/17.0) * 0.3 + 0.7;

            // mix the colours
            //col = col*(1-a) + vec4(r,g,b,1)*a;
            col = col + vec4(r,g,b,1)*a;
        }
    }

    // apply lighting
    vec3 shade = vertexColor.rgb * (lightmix) + vec3(1.0-lightmix,1.0-lightmix,1.0-lightmix);
    col.rgb *= shade;

    // Lab composition: original geometry/alpha, optional red-channel blade mask.
    float coverage = 1.0;
    if (maskMode == 1) {
        vec2 localUV = clamp((texCoord0 - baseRect.xy) / (baseRect.zw - baseRect.xy), 0.0, 1.0);
        coverage = texture(CosmicAtlas, mix(maskRect.xy, maskRect.zw, localUV)).r;
    }
    // Vanilla shield atlases store the metal frame as mid-dark neutral texels.
    // Patterned shields use a near-white paint base, so it stays Cosmic.
    if (shieldRimMode == 1) {
        float spread = max(max(mask.r, mask.g), mask.b) - min(min(mask.r, mask.g), mask.b);
        float peak = max(max(mask.r, mask.g), mask.b);
        if (spread < 0.085 && peak < 0.80) coverage = 0.0;
    }
    vec3 cosmicColor = clamp(col.rgb, 0.0, 1.0);
    if (paletteMode == 1) {
        float brightness = max(max(cosmicColor.r, cosmicColor.g), cosmicColor.b);
        cosmicColor = vec3(brightness, brightness * 0.67, brightness * 0.16);
    } else if (paletteMode == 2) {
        float brightness = max(max(cosmicColor.r, cosmicColor.g), cosmicColor.b);
        cosmicColor = vec3(brightness * 0.15, brightness * 0.9, brightness);
    }
    vec3 surface = mix(mask.rgb * vertexColor.rgb, cosmicColor, coverage * opacity);
    surface = mix(overlayColor.rgb, surface, overlayColor.a);
    fragColor = linear_fog(vec4(surface, mask.a * vertexColor.a) * ColorModulator,
                          vertexDistance, FogStart, FogEnd, FogColor);
}
