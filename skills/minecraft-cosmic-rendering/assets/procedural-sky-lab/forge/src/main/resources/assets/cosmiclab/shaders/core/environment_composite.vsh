#version 150
in vec3 Position;out vec2 screenUV;void main(){gl_Position=vec4(Position,1.0);screenUV=Position.xy*.5+.5;}
