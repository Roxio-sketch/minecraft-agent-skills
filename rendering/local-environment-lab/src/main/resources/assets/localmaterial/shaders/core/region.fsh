#version 150
#moj_import <localmaterial:kernel.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProjection;
uniform vec3 CenterRelative;
uniform float Radius;
uniform float Time;
uniform int Material;
uniform float FogStart;
uniform float FogEnd;
uniform int FogShape;
uniform vec4 FogColor;
in vec2 uv;
out vec4 fragColor;
vec3 reconstruct(vec2 coords,float d){vec4 q=InvViewProjection*vec4(coords*2.0-1.0,d*2.0-1.0,1.0);return q.xyz/q.w;}
void main(){
    vec4 base=texture(Sampler0,uv);float d=texture(Sampler1,uv).r;
    // Derivatives precede divergent rejection. Approximate flat normals, not vertex normals.
    vec3 relative=reconstruct(uv,d);vec3 dx=dFdx(relative),dy=dFdy(relative);
    vec3 n=cross(dx,dy);n/=max(length(n),1e-6);
    if(d>=1.0){fragColor=base;return;}
    vec3 p=relative-CenterRelative;
    float mask=leCoverage(length(p)-Radius,.7);
    if(mask<=0.0){fragColor=base;return;}
    vec3 replacement=leMaterial(p,n,Material,Time);
    float dist=FogShape==0?length(relative):max(length(relative.xz),abs(relative.y));
    float fog=FogEnd>FogStart?smoothstep(FogStart,FogEnd,dist):step(FogEnd,dist);
    // Both base and replacement have fog applied once before interpolation.
    replacement=mix(replacement,FogColor.rgb,fog*FogColor.a);
    fragColor=vec4(mix(base.rgb,replacement,mask),base.a);
}
