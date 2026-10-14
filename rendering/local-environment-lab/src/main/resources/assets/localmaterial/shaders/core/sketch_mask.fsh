#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform vec2 TexelSize;
uniform float DetailSuppression;
uniform float LineWeight;
in vec2 uv;
out vec4 fragColor;

float visibleCutout(vec2 p) {
    p=clamp(p,vec2(0.0),vec2(1.0));
    float solid=texture(Sampler0,p).r;
    float cutout=texture(Sampler1,p).r;
    float world=texture(Sampler2,p).r;
    return float(cutout+0.000001<solid && abs(world-cutout)<0.0000001);
}
vec3 sceneColor(vec2 p) {
    return texture(Sampler3,clamp(p,vec2(0.0),vec2(1.0))).rgb;
}
vec3 blur9Color(vec2 p,vec2 stepSize) {
    vec3 v=4.0*sceneColor(p);
    v+=2.0*(sceneColor(p+vec2(stepSize.x,0.0))+sceneColor(p-vec2(stepSize.x,0.0)));
    v+=2.0*(sceneColor(p+vec2(0.0,stepSize.y))+sceneColor(p-vec2(0.0,stepSize.y)));
    v+=sceneColor(p+stepSize)+sceneColor(p-stepSize);
    v+=sceneColor(p+vec2(stepSize.x,-stepSize.y))+sceneColor(p+vec2(-stepSize.x,stepSize.y));
    return v/16.0;
}
void main() {
    // Half-resolution, wide-footprint coverage: aggregate a canopy rather than
    // preserving every alpha-tested leaf fragment as a final contour.
    // Dense tent coverage avoids the comb-like aliasing of nine distant taps.
    float density=0.0;
    for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++){
        float weight=float((4-abs(x))*(4-abs(y)));
        density+=weight*visibleCutout(uv+vec2(x,y)*TexelSize*3.0*LineWeight);
    }
    vec2 pixel=TexelSize*LineWeight;
    vec3 fine=blur9Color(uv,pixel*mix(1.15,3.8,DetailSuppression));
    vec3 coarse=blur9Color(uv,pixel*mix(3.0,9.0,DetailSuppression));
    vec3 difference=coarse-fine;
    float chroma=max(abs(difference.r-difference.g),abs(difference.b-difference.g));
    // Encode low-amplitude chroma changes with more RGBA8 precision.
    float d=texture(Sampler2,uv).r;
    vec2 e=TexelSize*LineWeight*2.0;
    float l=texture(Sampler2,uv-vec2(e.x,0)).r;
    float r=texture(Sampler2,uv+vec2(e.x,0)).r;
    float b=texture(Sampler2,uv-vec2(0,e.y)).r;
    float t=texture(Sampler2,uv+vec2(0,e.y)).r;
    float scale=max(1.0-d,0.002);
    float edge=max(max(abs(l-d),abs(r-d)),max(abs(t-d),abs(b-d)))/scale;
    float crease=max(abs(l+r-2.0*d),abs(b+t-2.0*d))/scale;
    float structure=smoothstep(0.0002,0.004,crease);
    fragColor=vec4(density/256.0,clamp(chroma*16.0,0.0,1.0),structure,1.0);
}
