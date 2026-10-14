#version 150
in vec2 texCoord;
out vec4 fragColor;
uniform vec3 Eye;
uniform vec2 HalfSize;
uniform mat4 SceneProjection;
uniform float SceneTime;
uniform int Interior;
uniform mat4 RayMatrix;

const vec3 paper=vec3(0.89,0.905,0.845);
const vec3 ink=vec3(0.07,0.145,0.145);
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.),f.x),f.y);}
float fbm(vec2 p){return .53*noise(p)+.28*noise(p*2.03)+.14*noise(p*4.11)+.05*noise(p*8.3);}
float peak(vec2 p,vec2 c,vec2 s,float h){vec2 q=(p-c)/s;float d=dot(q,q);return h*exp(-d*1.6);}
float terrain(vec2 p){
    vec2 w=p+vec2(sin(p.y*.22)*.7,sin(p.x*.19)*.7);
    float h=peak(w,vec2(-10,-23),vec2(5,9),11.0);
    h=max(h,peak(w,vec2(11,-32),vec2(6,11),16.5));
    h=max(h,peak(w,vec2(-18,-45),vec2(8,14),20.0));
    h=max(h,peak(w,vec2(-4,-65),vec2(7,13),22.0));
    h=max(h,peak(w,vec2(22,-65),vec2(10,18),26.0));
    h=max(h,peak(w,vec2(9,-95),vec2(12,18),29.0));
    float ridges=sin(p.x*1.2+sin(p.y*.6))*sin(p.y*.85)*.48;
    return -.28+h+min(h*.16,1.0)*ridges;
}
float traceLand(vec3 ro,vec3 rd,float start,float maximum,int steps){
    float t=start,prev=start;
    for(int i=0;i<160;i++){
        if(i>=steps||t>maximum)break;
        vec3 p=ro+rd*t;float d=p.y-terrain(p.xz);
        if(d<.035){
            float lo=prev,hi=t;
            for(int j=0;j<6;j++){float mid=(lo+hi)*.5;vec3 q=ro+rd*mid;if(q.y<terrain(q.xz))hi=mid;else lo=mid;}
            return (lo+hi)*.5;
        }
        prev=t;t+=clamp(d*.20,.12,3.5);
    }
    return 1e5;
}
vec3 sky(vec3 rd){
    float wash=fbm(rd.xz*4.0+rd.y*2.0);
    vec3 c=paper+vec3(.02,.012,-.006)*(wash-.5);
    vec3 sun=normalize(vec3(-.38,.31,-1.0));
    float disc=smoothstep(.9968,.9976,dot(rd,sun));
    c=mix(c,vec3(.70,.27,.17),disc*.82);
    return c;
}
vec3 mountainColor(vec3 p,vec3 ro){
    float e=.09,h=terrain(p.xz);
    vec3 n=normalize(vec3(h-terrain(p.xz+vec2(e,0)),e,h-terrain(p.xz+vec2(0,e))));
    float l=clamp(dot(n,normalize(vec3(-.5,.8,.3))),0.0,1.0);
    float stroke=fbm(vec2(p.x*1.9+p.z*.4,p.y*.52));
    float threads=smoothstep(.78,.96,noise(vec2(p.x*7.5,p.y*.35+p.z*.3)));
    vec3 c=mix(ink,vec3(.38,.47,.405),.28+l*.54+stroke*.12);
    c*=1.0-threads*.32;
    float distance=length(p-ro);
    float mist=1.0-exp(-distance*.016);
    mist+=.30*exp(-pow((p.y-2.0)*.42,2.0))*(.70+.3*sin(p.z*.17+SceneTime*.16));
    return mix(c,paper,clamp(mist,0.0,.93));
}
float triangle(vec3 ro,vec3 rd,vec3 a,vec3 b,vec3 c){
    vec3 e1=b-a,e2=c-a,p=cross(rd,e2);float det=dot(e1,p);
    if(abs(det)<1e-6)return 1e5;
    vec3 s=ro-a;float u=dot(s,p)/det;if(u<0.0||u>1.0)return 1e5;
    vec3 q=cross(s,e1);float v=dot(rd,q)/det;if(v<0.0||u+v>1.0)return 1e5;
    float t=dot(e2,q)/det;return t>0.0?t:1e5;
}
void boat(vec3 ro,vec3 rd,inout float closest,inout vec3 color){
    vec3 b=vec3(1.8+sin(SceneTime*.14)*.8,.12,-17.0);
    float sail=triangle(ro,rd,b+vec3(0,3.1,0),b+vec3(0,.7,0),b+vec3(1.6,.8,.12));
    if(sail<closest){closest=sail;vec3 p=ro+rd*sail;color=vec3(.50,.105,.065)*(0.90+.10*noise(p.xy*17.));}
    float hull=min(triangle(ro,rd,b+vec3(-1.2,.18,.35),b+vec3(1.7,.18,.35),b+vec3(.9,-.12,.35)),triangle(ro,rd,b+vec3(-1.2,.18,.35),b+vec3(.9,-.12,.35),b+vec3(-.8,-.12,.35)));
    hull=min(hull,triangle(ro,rd,b+vec3(-.025,.1,.05),b+vec3(.025,.1,.05),b+vec3(0,3.3,.05)));
    if(hull<closest){closest=hull;color=ink*.70;}
}
void main(){
    vec2 q=(texCoord*2.0-1.0)*HalfSize;
    vec3 ro=Eye+vec3(0,3.5,0);
    vec4 ray=RayMatrix*vec4(texCoord*2.-1.,1.,1.);
    vec3 rd=Interior==1?normalize(ray.xyz/ray.w):normalize(vec3(q,0)-Eye);
    float entry=Interior==1?.08:length(vec3(q,0)-Eye);
    float water=rd.y<-.0001?-ro.y/rd.y:1e5;
    float land=traceLand(ro,rd,entry+.02,min(water,155.0),150);
    float t=1e5;vec3 color=sky(rd);
    if(land<min(water,155.0)){t=land;color=mountainColor(ro+rd*t,ro);}
    else if(water>entry&&water<155.0){
        t=water;vec3 p=ro+rd*t;
        vec3 rr=normalize(vec3(rd.x+sin(p.z*1.5+SceneTime*.6)*.009,-rd.y,rd.z));
        float reflected=traceLand(p+vec3(0,.035,0),rr,.08,110.0,90);
        vec3 refl=reflected<110.0?mountainColor(p+rr*reflected,ro):sky(rr);
        float ripple=pow(max(0.0,sin(p.z*6.0+sin(p.x*.8)+SceneTime*1.5)),18.0);
        color=mix(vec3(.52,.65,.62),refl,.65)+ripple*.035;
        color=mix(color,paper,1.0-exp(-t*.005));
    }
    boat(ro,rd,t,color);
    float grain=noise(gl_FragCoord.xy*.65)-.5;
    color+=grain*.013;
    fragColor=vec4(clamp(color,0.0,1.0),1.0);
    if(t<155.0){vec4 clip=SceneProjection*vec4(rd*t,1);gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.,1.);}
    else gl_FragDepth=1.0;
}
