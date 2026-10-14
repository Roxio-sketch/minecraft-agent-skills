#version 150
in vec4 color;in vec2 uv;out vec4 fragColor;
void main(){float r=length(uv*2.0-1.0);float a=(1.0-smoothstep(.1,1.0,r))*color.a;if(a<.003)discard;fragColor=vec4(color.rgb,a);}
