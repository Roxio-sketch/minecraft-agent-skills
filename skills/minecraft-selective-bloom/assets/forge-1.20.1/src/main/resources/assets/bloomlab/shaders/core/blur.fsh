#version 150
uniform sampler2D Sampler0;uniform vec2 Direction;in vec2 uv;out vec4 fragColor;
void main(){vec3 c=texture(Sampler0,uv).rgb*.227027;for(int i=1;i<=4;i++){float w=i==1?.194595:i==2?.121622:i==3?.054054:.016216;vec2 d=Direction*float(i);c+=(texture(Sampler0,clamp(uv+d,vec2(0),vec2(1))).rgb+texture(Sampler0,clamp(uv-d,vec2(0),vec2(1))).rgb)*w;}fragColor=vec4(c,1);}
