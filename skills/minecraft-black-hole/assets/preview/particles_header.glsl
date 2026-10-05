#version 150
uniform vec3 CenterRelative,ParticleCold,ParticleMid,ParticleHot;
uniform float WorldScale,ParticleBrightness,ParticleOuter,ParticleGradientPosition;
in vec2 texCoord;in vec3 RayPosition;
flat in vec3 ParticleLocal;
flat in float ParticleLife,ParticleAlpha,ParticleRadius;
flat in float ParticleSeed;
out vec4 fragColor;
vec2 worldDiskPattern(float radius,float angle);
#define BH_PATTERN worldDiskPattern
bool dustEnd(vec3 p){
    return length(p+CenterRelative/WorldScale)>=length(ParticleLocal+CenterRelative/WorldScale);
}
#define BH_SCENE_BLOCKED(p) dustEnd(p)
