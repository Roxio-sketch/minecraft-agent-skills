uniform vec2 Resolution;
uniform int Background;
in vec2 texCoord;
out vec4 fragColor;
float hash21(vec2 p) {return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec2 p=(texCoord-0.5)*vec2(Resolution.x/Resolution.y,1.0)*2.0;
    vec3 c;
    if(Background==0){
        vec2 q=texCoord*12.0;
        float checker=mod(floor(q.x)+floor(q.y),2.0);
        c=mix(vec3(0.07,0.12,0.19),vec3(0.72,0.79,0.87),checker);
        float line=1.0-smoothstep(0.02,0.04,min(abs(fract(q.x)-0.5),abs(fract(q.y)-0.5)));
        c=mix(c,vec3(1.0,0.40,0.20),line*0.65);
    }else{
        float band=exp(-8.0*pow(p.y-0.28*sin(2.1*p.x)-0.08*cos(7.0*p.x),2.0));
        float folds=0.5+0.25*sin(9.0*p.x+5.0*p.y)+0.25*sin(17.0*p.y-3.0*p.x);
        c=vec3(0.007,0.016,0.05)+band*mix(vec3(0.18,0.05,0.40),vec3(0.02,0.47,0.63),folds)*0.70;
        vec2 grid=p*24.0, cell=floor(grid);
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
            vec2 id=cell+vec2(float(x),float(y));float h=hash21(id);
            vec2 center=id+vec2(h,hash21(id+17.0));
            float radius=length(grid-center);
            float star=exp(-radius*radius*220.0)*step(0.84,h);
            c+=star*mix(vec3(0.55,0.80,1.0),vec3(1.0,0.72,0.42),hash21(id+4.0));
        }
    }
    fragColor=vec4(c,1.0);
}
