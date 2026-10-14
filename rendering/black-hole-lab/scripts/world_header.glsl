#version 150
uniform sampler2D Sampler0,Sampler1;
uniform mat4 InvViewProjection,ViewProjection;
uniform vec3 CenterRelative;
uniform float WorldScale,LensStrength,uExposure;
uniform float HaloStrength,HaloRing,HaloDisk,HaloAura;
vec2 worldDiskPattern(float radius,float angle);
vec3 haloEmission(vec3 q);
#define BH_PATTERN worldDiskPattern
#define BH_VOLUME_SAMPLE(q,ds,result) result.light+=result.transmission*haloEmission(q)*ds
in vec2 texCoord;out vec4 fragColor;
vec3 scenePosition(vec2 uv,float depth){
    vec4 p=InvViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
bool onScreen(vec2 uv){return all(greaterThan(uv,vec2(.001)))&&all(lessThan(uv,vec2(.999)));}
bool sceneBlocked(vec3 local){
    vec3 p=CenterRelative+local*WorldScale;
    vec4 clip=ViewProjection*vec4(p,1.0);
    if(clip.w<=0.0)return false;
    vec2 uv=clip.xy/clip.w*.5+.5;
    if(!onScreen(uv))return false;
    float depth=texture(Sampler1,uv).r;
    if(depth>=.999999)return false;
    vec3 surface=scenePosition(uv,depth);
    // Compare camera-to-point Euclidean distances for the same projected ray.
    return length(p)>length(surface)+.08;
}
#define BH_SCENE_BLOCKED(p) sceneBlocked(p)
// Emissivity follows the transported field as well as density, retaining visible
// spiral filaments instead of saturating into a uniform glowing band.
#define BH_DISK_EMISSION(pattern) ((.25+.75*pow(pattern.x,1.3))*(.65+.35*pattern.y))
