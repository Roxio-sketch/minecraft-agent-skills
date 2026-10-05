#version 150
in vec3 viewPosition;
in vec3 viewNormal;
in vec2 uv;
out vec4 fragColor;
uniform vec3 Tint;
uniform float Opacity;
uniform float Shininess;
uniform float Reflection;
uniform mat4 ModelViewMat;
void main() {
    vec3 n=normalize(viewNormal),v=normalize(-viewPosition);
    if(dot(n,v)<0.0)n=-n;
    float fresnel=0.0204+0.9796*pow(1.0-max(dot(n,v),0.0),5.0);
    vec3 light=normalize(mat3(ModelViewMat)*vec3(-0.3,0.8,0.5));
    float shine=pow(max(dot(reflect(-light,n),v),0.0),Shininess);
    vec3 reflected=transpose(mat3(ModelViewMat))*reflect(-v,n);
    vec3 sky=mix(vec3(0.12,0.28,0.40),vec3(0.55,0.85,0.98),clamp(reflected.y*0.5+0.5,0.0,1.0));
    vec3 col=mix(Tint,sky,clamp(fresnel*Reflection,0.0,1.0))+shine*vec3(0.8,0.95,1.0);
    float edge=smoothstep(0.0,0.06,min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y)));
    fragColor=vec4(col,edge*clamp(Opacity+fresnel*0.6*Reflection+shine*0.25,0.0,0.95));
}
