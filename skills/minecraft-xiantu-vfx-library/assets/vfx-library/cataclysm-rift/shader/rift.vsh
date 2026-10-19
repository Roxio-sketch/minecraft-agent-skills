#version 150
in vec2 Position;out vec2 uv;void main(){uv=Position*.5+.5;gl_Position=vec4(Position,0.,1.);}
