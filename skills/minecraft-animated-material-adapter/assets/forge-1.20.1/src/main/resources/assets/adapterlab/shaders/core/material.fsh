#version 150
uniform float Time;uniform vec3 Tint;in vec3 local;in vec4 color;in vec4 overlay;in vec3 normal;out vec4 fragColor;
void main(){float wave=.5+.5*sin(local.y*25.0+sin(local.x*12.0+Time)*2.0-Time*2.0);float motif=smoothstep(.84,.98,wave);vec3 c=mix(Tint*.16,Tint,motif);c*=.6+.4*abs(normal.z);c=mix(overlay.rgb,c,overlay.a);fragColor=vec4(c,1.0)*color;}
