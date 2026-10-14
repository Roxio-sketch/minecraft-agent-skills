#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GlintAlpha;
in float vertexDistance;
in vec2 texCoord0;
out vec4 fragColor;
uniform float EffectTimeSeconds;
// Generated palette math; effectUV must be supplied by the target rendering adapter.
float cgToLinear(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float cgToSrgb(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
vec3 cgRgbToHsv(vec3 c) {
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    float d = hi - lo;
    float h = 0.0;
    if (d > 0.0000001) {
        if (hi == c.r) h = mod((c.g-c.b)/d, 6.0);
        else if (hi == c.g) h = (c.b-c.r)/d+2.0;
        else h = (c.r-c.g)/d+4.0;
        h /= 6.0;
    }
    return vec3(h, hi > 0.0 ? d/hi : 0.0, hi);
}
vec3 cgHsvToRgb(vec3 c) {
    vec3 p = clamp(abs(mod(c.x*6.0+vec3(0.0,4.0,2.0),6.0)-3.0)-1.0,0.0,1.0);
    return c.z * mix(vec3(1.0), p, c.y);
}

vec3 cgMix(vec3 a,vec3 b,float t) {
    vec3 x=cgRgbToHsv(a), y=cgRgbToHsv(b);
    if(x.y<=0.000001)x.x=y.x;
    if(y.y<=0.000001)y.x=x.x;
    float dh=mod(y.x-x.x+0.5,1.0)-0.5;
    return cgHsvToRgb(vec3(fract(x.x+dh*t),mix(x.y,y.y,t),mix(x.z,y.z,t)));
}
vec3 cgPalette(float t) {
    if(t<=0.0)return vec3(1.0, 0.18823529411764706, 0.30980392156862746);
    if(t<=0.16666666669999999)return cgMix(vec3(1.0, 0.18823529411764706, 0.30980392156862746),vec3(1.0, 0.83921568627450982, 0.25098039215686274),(t-0.0)/0.16666666669999999);
    if(t<=0.33333333329999998)return cgMix(vec3(1.0, 0.83921568627450982, 0.25098039215686274),vec3(0.30980392156862746, 1.0, 0.46274509803921571),(t-0.16666666669999999)/0.16666666659999999);
    if(t<=0.5)return cgMix(vec3(0.30980392156862746, 1.0, 0.46274509803921571),vec3(0.23137254901960785, 0.90588235294117647, 1.0),(t-0.33333333329999998)/0.16666666670000002);
    if(t<=0.66666666669999997)return cgMix(vec3(0.23137254901960785, 0.90588235294117647, 1.0),vec3(0.33333333333333331, 0.4392156862745098, 1.0),(t-0.5)/0.16666666669999997);
    if(t<=0.83333333330000003)return cgMix(vec3(0.33333333333333331, 0.4392156862745098, 1.0),vec3(0.8901960784313725, 0.30980392156862746, 1.0),(t-0.66666666669999997)/0.16666666660000007);
    if(t<=1.0)return cgMix(vec3(0.8901960784313725, 0.30980392156862746, 1.0),vec3(1.0, 0.18823529411764706, 0.30980392156862746),(t-0.83333333330000003)/0.16666666669999997);
    return vec3(1.0, 0.18823529411764706, 0.30980392156862746);
}
float cgPhase(vec2 effectUV,float seconds) {
    float q=0.0;
    q+=seconds/6.0;
    return fract(q);
}
vec4 cgColorGlint(vec4 carrier,vec2 effectUV,float seconds) {
    float intensity=max(carrier.r,max(carrier.g,carrier.b))*1.0;
    return vec4(clamp(cgPalette(cgPhase(effectUV,seconds))*intensity,0.0,1.0),carrier.a);
}
void main() {
    vec4 color=cgColorGlint(texture(Sampler0,texCoord0),texCoord0,EffectTimeSeconds)*ColorModulator;
    if(color.a<0.1)discard;
    float fade=linear_fog_fade(vertexDistance,FogStart,FogEnd)*GlintAlpha;
    fragColor=vec4(color.rgb*fade,color.a);
}
