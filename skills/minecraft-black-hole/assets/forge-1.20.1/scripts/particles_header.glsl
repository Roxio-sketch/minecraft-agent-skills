#version 150
uniform vec3 CenterRelative,CameraOriginRelative,ParticleCold,ParticleHot;
uniform float WorldScale,ParticleBrightness,ParticleOuter;
in vec2 texCoord;in vec3 RayPosition;
flat in vec3 ParticleLocal;
flat in float ParticleLife,ParticleAlpha,ParticleRadius;
out vec4 fragColor;
vec2 worldDiskPattern(float radius,float angle);
bool dustEnd(vec3 p){
    vec3 cameraLocal=(CameraOriginRelative-CenterRelative)/WorldScale;
    return length(p-cameraLocal)>=length(ParticleLocal-cameraLocal);
}
#define BH_SCENE_BLOCKED(p) dustEnd(p)
