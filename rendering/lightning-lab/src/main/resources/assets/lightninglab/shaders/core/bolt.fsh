#version 150
in vec4 vertexColor;
in vec2 uv;
uniform vec3 Tint;
uniform float Gain;
uniform float Profile;
out vec4 fragColor;
void main(){
 float d=abs(uv.y*2.0-1.0);
 float falloff=Profile>0.5 ? exp(-5.0*d*d)-exp(-5.0) : 1.0-smoothstep(0.30,0.95,d);
 fragColor=vec4(Tint*vertexColor.rgb*Gain,vertexColor.a*max(0.0,falloff));
}
