#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ViewProjection;
uniform int Interior;
out vec2 texCoord;
void main(){texCoord=UV0;gl_Position=Interior==1?vec4(Position,1.0):ViewProjection*vec4(Position,1.0);}
