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
    vec3 x=vec3(cgToLinear(a.r),cgToLinear(a.g),cgToLinear(a.b));
    vec3 y=vec3(cgToLinear(b.r),cgToLinear(b.g),cgToLinear(b.b));
    vec3 c=mix(x,y,t);
    return vec3(cgToSrgb(c.r),cgToSrgb(c.g),cgToSrgb(c.b));
}
vec3 cgPalette(float t) {
    if(t<=0.0)return vec3(0.63921568627450975, 0.85098039215686272, 1.0);
    return vec3(0.63921568627450975, 0.85098039215686272, 1.0);
}
float cgPhase(vec2 effectUV,float seconds) {
    return 0.0;
}
vec4 cgColorGlint(vec4 carrier,vec2 effectUV,float seconds) {
    float intensity=max(carrier.r,max(carrier.g,carrier.b))*1.0;
    return vec4(clamp(cgPalette(cgPhase(effectUV,seconds))*intensity,0.0,1.0),carrier.a);
}
void main() {
    vec4 color=cgColorGlint(texture(Sampler0,texCoord0),texCoord0,0.0)*ColorModulator;
    if(color.a<0.1)discard;
    float fade=linear_fog_fade(vertexDistance,FogStart,FogEnd)*GlintAlpha;
    fragColor=vec4(color.rgb*fade,color.a);
}
