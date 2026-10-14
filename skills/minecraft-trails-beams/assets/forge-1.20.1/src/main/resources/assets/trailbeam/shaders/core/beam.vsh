#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 tint;
out vec2 uv;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);tint=Color;uv=UV0;}
