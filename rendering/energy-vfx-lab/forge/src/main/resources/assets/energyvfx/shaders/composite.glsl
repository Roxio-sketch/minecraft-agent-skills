uniform sampler2D image,glow;
uniform float bloom,distortion,clockTime;
out vec4 result;
void main(){vec2 uv=gl_FragCoord.xy/viewport;vec4 energy=texture(image,uv);
 vec2 offset=vec2(sin(uv.y*33.+clockTime),cos(uv.x*29.-clockTime))*distortion*(1.-energy.a);
 vec3 c=scene(clamp(uv+offset,vec2(0),vec2(1))).rgb*energy.a+energy.rgb;
 vec3 halo=vec3(0);if(bloom>0.)halo=pow(1.-exp(-texture(glow,uv).rgb*bloom),vec3(1./2.2));
 result=vec4(clamp(c+halo*.4,0.,1.),1);}
