#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 viewPosition;
out vec3 viewNormal;
out vec2 uv;
void main() {
    vec4 p=ModelViewMat*vec4(Position,1.0);
    viewPosition=p.xyz;
    viewNormal=mat3(ModelViewMat)*(Color.rgb*2.0-1.0);
    uv=UV0;
    gl_Position=ProjMat*p;
}
