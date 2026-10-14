#version 150
#moj_import <fog.glsl>
#moj_import <localmaterial:kernel.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 CenterRelative;
uniform float Radius;
uniform float Time;
uniform int Material;
uniform int SurfaceScope;
uniform vec4 WaterRect0;
uniform vec4 WaterRect1;
uniform vec4 WaterRect2;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 surfacePosition;
in vec3 surfaceLight;
out vec4 fragColor;
bool inRect(vec2 uv,vec4 r){return all(greaterThanEqual(uv,r.xy))&&all(lessThanEqual(uv,r.zw));}
void main(){
    vec4 texel=texture(Sampler0,texCoord0);
    vec4 color=texel*vertexColor*ColorModulator;
    // Compute derivatives before divergent selection. Vanilla liquid vertex Normal is always up,
    // so actual geometric derivatives correctly recover sloped tops AND vertical flowing sides.
    vec3 crossNormal=cross(dFdx(surfacePosition),dFdy(surfacePosition));
    vec3 n=crossNormal*inversesqrt(max(dot(crossNormal,crossNormal),1e-12));
    vec3 p=surfacePosition-CenterRelative;
    bool selected=SurfaceScope==1||inRect(texCoord0,WaterRect0)||inRect(texCoord0,WaterRect1)||inRect(texCoord0,WaterRect2);
    float coverage=selected?leCoverage(length(p)-Radius,.7):0.0;
    if(coverage>0.0){
        // Original animated atlas sampling remains a modulation of the new surface material.
        float flowDetail=mix(.65,1.0,clamp(dot(texel.rgb,vec3(.2126,.7152,.0722)),0.0,1.0));
        vec3 replacement=leMaterial(p,n,Material,Time)*surfaceLight*ColorModulator.rgb*flowDetail;
        color.rgb=mix(color.rgb,replacement,coverage);
    }
    // Keep original alpha exactly; no discard, gl_FragDepth, displacement, extra surface or scene sampling.
    fragColor=linear_fog(color,vertexDistance,FogStart,FogEnd,FogColor);
}
