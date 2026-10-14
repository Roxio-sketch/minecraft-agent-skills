#version 150
uniform sampler2D Sampler0;
uniform int Profile;
uniform float Gain;
in vec4 tint;
in vec2 uv;
out vec4 fragColor;
float trailHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    float v=abs(uv.y*2.0-1.0);
    float coverage=1.0-smoothstep(0.0,1.0,v);
    if(Profile==1)coverage=1.0;
    if(Profile==2)coverage=exp(-pow((v-0.65)*9.0,2.0));
    if(Profile==3)coverage*=1.0-smoothstep(0.3,0.48,abs(fract(uv.x)-0.5));
    if(Profile==4)coverage*=0.14+0.86*exp(-pow((fract(uv.x)-0.5)*6.0,2.0));
    if(Profile==5){
        float n=trailHash(floor(vec2(uv.x*9.0,uv.y*7.0)));
        float wave=sin(uv.x*21.0+uv.y*13.0+n*6.28318);
        coverage*=smoothstep(0.12,0.76,0.56+0.30*wave+0.32*n-0.32*v);
    }
    if(Profile==6){
        vec2 cell=fract(vec2(uv.x*3.0,uv.y));
        float bar=1.0-smoothstep(0.035,0.105,abs(cell.x-0.50));
        float arm=1.0-smoothstep(0.035,0.100,abs(cell.y-0.48-0.20*sin(floor(uv.x*3.0)*2.1)));
        float gate=step(0.18,cell.x)*step(cell.x,0.82);
        coverage*=0.12+0.88*max(bar,arm*gate);
    }
    vec4 tex=texture(Sampler0,vec2(fract(uv.x),uv.y));
    float a=tint.a*tex.a*coverage;
    if(a<0.001)discard;
    fragColor=vec4(tint.rgb*tex.rgb*Gain,a);
}
