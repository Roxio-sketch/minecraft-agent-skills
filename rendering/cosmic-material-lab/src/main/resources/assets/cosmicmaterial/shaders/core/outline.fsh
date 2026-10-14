#version 150

uniform sampler2D Sampler0;
uniform float time;
uniform int mode;
uniform vec4 outlineColor;
uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;

in vec2 texCoord0;
out vec4 fragColor;

float hash21(vec2 p) {
    return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);
}

vec3 hue(float h) {
    vec3 p=abs(fract(h+vec3(0.0,2.0/3.0,1.0/3.0))*6.0-3.0)-1.0;
    return clamp(p,0.0,1.0);
}

void main() {
    if(texture(Sampler0,texCoord0).a<0.1)discard;
    vec4 result=outlineColor;
    if(mode==3) {
        float phase=fract((gl_FragCoord.x+gl_FragCoord.y)*0.006-time*0.012);
        if(phase<0.333333) result=mix(color1,color2,phase*3.0);
        else if(phase<0.666667) result=mix(color2,color3,(phase-0.333333)*3.0);
        else result=mix(color3,color1,(phase-0.666667)*3.0);
    } else if(mode==4) {
        result=vec4(hue(fract(time*0.005)),outlineColor.a);
    } else if(mode==1) {
        vec2 p=gl_FragCoord.xy*0.045;
        float cloud=sin(p.x*0.8+time*0.012)*sin(p.y*0.9-time*0.009);
        float star=step(0.992,hash21(floor(p+time*0.002)));
        result=vec4(mix(vec3(0.08,0.25,0.6),vec3(0.6,0.22,0.85),cloud*0.5+0.5)+star*0.6,outlineColor.a);
    }
    fragColor=result;
}
