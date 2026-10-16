#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ViewProjection;
out vec2 LocalPlane;
void main(){LocalPlane=UV0;gl_Position=ViewProjection*vec4(Position,1.);}
