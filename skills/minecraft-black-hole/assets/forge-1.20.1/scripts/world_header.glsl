#version 150
uniform sampler2D Sampler0,Sampler1,Sampler2,Sampler3,Sampler4;
uniform int ForegroundCount;
uniform float EnvironmentReady;
uniform mat4 InvViewProjection,ViewProjection;
uniform vec3 CenterRelative,CameraOriginRelative;
uniform float WorldScale,LensStrength,LensRadius,uExposure;
// Only explicit development probes enable this source-coverage diagnostic.
uniform float LensProbeMode;
uniform vec3 ProbeBoundsMin,ProbeBoundsMax;
vec2 worldDiskPattern(float radius,float angle);
vec3 haloEmission(vec3 q);
in vec2 texCoord;out vec4 fragColor;
vec3 scenePosition(vec2 uv,float depth){
    vec4 p=InvViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
bool onScreen(vec2 uv){return all(greaterThan(uv,vec2(.001)))&&all(lessThan(uv,vec2(.999)));}
// One visible surface per output ray. Reprojecting every bent integration
// point into the depth buffer extrudes nearby player silhouettes across the
// horizon, then composites unrelated background into the resulting holes.
vec3 sceneRay;
float sceneLimit;
bool sceneBlocked(vec3 local){
    vec3 p=CenterRelative+local*WorldScale;
    return dot(p-CameraOriginRelative,sceneRay)>sceneLimit+.02;
}
#define BH_SCENE_BLOCKED(p) sceneBlocked(p)
