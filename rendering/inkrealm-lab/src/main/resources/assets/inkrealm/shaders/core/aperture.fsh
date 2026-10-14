#version 150
uniform sampler2D Sampler0;
uniform float SceneTime;
uniform int Interior;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    if(Interior==1){fragColor=vec4(texture(Sampler0,texCoord).rgb,1.);gl_FragDepth=0.;return;}
    vec2 q=texCoord*2.0-1.0;float a=atan(q.y,q.x),r=length(q);
    float edge=1.0+.007*sin(a*31.)+.004*sin(a*73.+1.3);
    if(r>edge)discard;
    vec3 scene=texture(Sampler0,texCoord).rgb;
    float rim=smoothstep(edge-.033,edge-.007,r);
    float brush=.72+.28*sin(a*43.0+sin(a*19.0));
    vec3 border=mix(vec3(.075,.14,.14),vec3(.64,.47,.23),brush*.70);
    float hair=1.-smoothstep(.0015,.004,abs(r-(edge-.039)));
    scene=mix(scene,border,rim);
    scene=mix(scene,vec3(.77,.67,.40),hair*.5);
    fragColor=vec4(scene,1.0);
    gl_FragDepth=gl_FragCoord.z;
}
