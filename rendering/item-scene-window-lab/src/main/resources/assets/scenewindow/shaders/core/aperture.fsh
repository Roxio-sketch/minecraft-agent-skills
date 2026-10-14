#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform int Shape;
uniform vec3 FrameColor;
in vec2 uv;
out vec4 fragColor;
void main(){
 vec2 p=(uv-.5)*2.;
 float d=Shape==1?length(p):Shape==2?(abs(p.x)+abs(p.y)):max(abs(p.x),abs(p.y));
 if(Shape==3){ if(texture(Sampler1,vec2(uv.x,1.-uv.y)).r<.5)discard; d=0.; }
 if(d>.98)discard;
 vec3 col=texture(Sampler0,uv).rgb;
 if(d>.88)col=FrameColor*(.7+.3*uv.y);
 fragColor=vec4(col,1.0);
}
