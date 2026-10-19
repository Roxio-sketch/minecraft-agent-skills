// Original video-inspired implementation. Textures contain periodic scalar fields, not video frames.
// Local units, shader time in seconds. Host supplies genuine scene color in SceneColor.
uniform sampler2D SceneColor,FlowNoise,Filaments;
uniform vec2 Resolution,Pan;
uniform float Time,Wave,Breath,BreathPeriod,Height,Speed,Glow,Distortion,Open,Zoom,Yaw,Slice,Bloom;
uniform int Background;
const float PI=3.14159265359;
float hash21(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float noise(vec2 p){return texture(FlowNoise,p*.071).r;}
float ribbon(vec2 p){return texture(Filaments,p).r;}
vec3 heat(float v){return mix(vec3(1.,.085,.007),vec3(1.,.62,.12),clamp(v,0.,1.));}
// Negative within the aperture. Concave polygon keeps long, angular tears rather than oval noise.
float aperture(vec2 p,out vec4 boundary){
 const int N=21;
 vec2 v[N]=vec2[N](vec2(-6.,-1.9),vec2(-3.2,-.15),vec2(-2.45,.70),vec2(-4.25,1.55),vec2(-2.25,2.27),vec2(-.85,3.02),vec2(-1.72,3.19),vec2(1.20,4.15),vec2(-.10,2.60),vec2(3.72,2.20),vec2(1.58,1.02),vec2(5.40,.48),vec2(2.62,-.28),vec2(3.34,-.38),vec2(1.38,-1.22),vec2(.50,-1.66),vec2(.10,-2.70),vec2(-.70,-1.55),vec2(-1.70,-1.20),vec2(-3.08,-1.52),vec2(-4.20,-1.40));
 float d=1e4,s=1.,arc=0.;boundary=vec4(0.);
 for(int i=0,j=N-1;i<N;j=i,i++) {vec2 e=v[j]-v[i],w=p-v[i];float u=clamp(dot(w,e)/dot(e,e),0.,1.),len=length(e);vec2 b=w-e*u;float dd=dot(b,b);if(dd<d){d=dd;boundary=vec4(arc+(1.-u)*len,v[i]+e*u,pow(max(0.,sin(u*PI)),.75));}arc+=len;bvec3 c=bvec3(p.y>=v[i].y,p.y<v[j].y,e.x*w.y>e.y*w.x);if(all(c)||all(not(c)))s=-s;}
 return s*sqrt(d);
}
vec4 riftShade(vec2 uv,out vec3 emission){
 float t=Time*Speed;vec2 p=(uv-.5)*vec2(Resolution.x/Resolution.y,1.)*9.8/Zoom-Pan;p.y-=.35;
 p.x/=max(.40,cos(Yaw));
 float breathPhase=Time*6.2831853/max(1.,BreathPeriod);

 float reveal=max(.001,Open);p.x/=reveal;p.y/=mix(.18,1.,reveal);
 vec2 ringP=p;p.y/=Height;
 // Stable polygon anchors; only a narrow band along its boundary is displaced.
 vec2 w=p;
 float burst=pow(max(0.,sin(t*2.15+.7)),20.);
 float bands=floor((p.y+t*.3)*13.);float stripe=step(.69,hash21(vec2(bands,floor(t*6.))));
 w.x+=Slice*burst*stripe*(hash21(vec2(bands,4.))-.5)*.38;
 vec4 boundary;float baseD=aperture(w,boundary);float along=boundary.x;
 float travel=along*2.05-breathPhase;
 float crest=sin(travel)+.38*sin(along*4.6-breathPhase*1.4+.8);
 float localFlow=texture(FlowNoise,vec2(along*.035-Time*.012,baseD*.4+Time*.008)).g;
 float edgeShift=Breath*(.12*crest+.075*(localFlow-.5))*boundary.w;
 // No object scaling or whole-screen sine warp: displacement decays into/out of the edge.
 float d=baseD-edgeShift*exp(-abs(baseD)*3.5);
 d-=Wave*.009*sin(along*17.-t*3.)*boundary.w*exp(-abs(baseD)*7.);
 float aa=max(fwidth(d),.002),inside=1.-smoothstep(-aa,aa,d),outside=max(d,0.);
 float localPulse=.5+.5*sin(along*1.65-breathPhase-.6);
 float flow=texture(FlowNoise,vec2(along*.062-t*.05,d*.55+t*.018)).g;
 float thread=ribbon(vec2(along*.047-t*.035,d*.30+t*.013));
 float edgeW=(.010+flow*.009)*(1.+Breath*(localPulse-.5)*.5);
 float edge=exp(-abs(d)/(edgeW+aa));
 float rim=exp(-abs(d+.042+Breath*.028*sin(travel-.7))/(.023+aa));
 float ripple=pow(.5+.5*sin(d*42.-breathPhase*1.5+Breath*sin(travel)*1.8+flow*1.5),7.)*exp(-abs(d)*12.);
 float sheet=pow(.5+.5*sin(d*88.-breathPhase*2.+thread*2.+Breath*sin(travel-.8)*1.4),11.)*exp(-abs(d)*22.);
 float turbulence=.85+.15*sin(along*5.-t*2.+flow*3.);
 vec3 border=(heat(flow)*edge*3.3+vec3(1.,.38,.065)*rim*1.3+heat(.4)*ripple*.42+vec3(1.,.16,.015)*sheet*.65)*turbulence;
 // Burnt-orange layered corona, narrow core preserved under HDR tonemapping.
 border+=vec3(.62,.035,.003)*exp(-abs(d)/( .10+Breath*.035*localPulse))*(.6+flow*.45);
 border*=1.+Breath*(localPulse-.5)*.48;
 float veil=pow(texture(Filaments,vec2(along*.037-Time*.02,d*.17-Time*.006)).g,2.);
 border+=vec3(.85,.10,.008)*veil*exp(-abs(d+.035*sin(travel-.5))*17.)*Breath*(.35+.65*localPulse);
 // Blue/violet moving space texture inside the torn cavity.
 vec2 nebUV=w*vec2(.11,.21)+vec2(t*.018,-t*.011);vec3 tex=texture(FlowNoise,nebUV).rgb;
 float neb=pow(clamp(tex.r*.7+tex.g*.4,0.,1.),2.2);
 vec3 space=mix(vec3(.009,.004,.025),vec3(.035,.09,.22),neb);
 space+=vec3(.20,.025,.09)*pow(tex.b,4.)*.55;
 space+=vec3(.025,.12,.26)*pow(thread,2.)*.25;
 vec2 cell=floor((w+vec2(t*.008,0.))*37.);vec2 cellUV=fract((w+vec2(t*.008,0.))*37.)-.5;
 float star=step(.983,hash21(cell))*exp(-dot(cellUV,cellUV)*230.)*(.6+.4*sin(hash21(cell+5.)*18.+t*1.3));
 space+=vec3(.60,.67,1.)*star*.85;
 vec2 ringWobble=ringP+vec2((w.x-p.x)*.15,0.);float angle=atan(ringWobble.y,ringWobble.x),rad=length(ringWobble*vec2(1.,.98));
 float radius=1.13+Wave*(.012*sin(angle*9.+t*2.2)+.007*sin(angle*21.-t*3.5));
 float rd=abs(rad-radius);float ringW=.026+.008*sin(angle*7.-t*2.);
 float ring=exp(-rd/max(.004,ringW+aa));
 float bandFlow=.75+.25*ribbon(vec2(angle/6.283-t*.09,rd*.4+t*.025));
 vec3 sun=(vec3(1.,.78,.23)*ring*6.0+vec3(1.,.16,.008)*exp(-rd*13.)*.85)*bandFlow;
 sun+=vec3(1.,.37,.025)*exp(-abs(rad-radius-.065)*70.)*.65;
 float hole=1.-smoothstep(radius-.07,radius+.01,rad);
 vec3 dark=vec3(.012,.0009,.001)+vec3(.13,.006,.001)*pow(rad/max(radius,.01),4.);
 space=mix(space,dark,hole);
 // Refraction samples the actual background FBO, offset only around the aperture boundary.
 float lens=exp(-outside*5.)*(1.-inside*.80);
 vec2 offset=normalize(p+vec2(.001))*(sin(d*35.-t*4.)+flow-.5)*Distortion*.004*lens*Open;
 vec3 bg=texture(SceneColor,clamp(uv+offset,vec2(.001),vec2(.999))).rgb;
 vec3 result=mix(bg,space,inside*Open);
 float live=smoothstep(0.,.16,Open);
 emission=(border+sun)*live*Glow;
 // Occasional small peripheral streaks remain subordinate to the torn edge.
 float sparks=0.;for(int i=0;i<14;i++){float h=hash21(vec2(float(i),7.)),phase=fract(t*(.13+h*.12)+h);vec2 q=vec2((h-.5)*9.,phase*5.-2.3);q.x+=.13*sin(phase*8.+h*20.);vec2 ds=(w-q)*vec2(1.,.45);sparks+=exp(-dot(ds,ds)*2400.)*sin(phase*PI);}
 emission+=vec3(1.,.28,.025)*sparks*.8*live*Glow;
 result+=emission;
 return vec4(result,1.);
}
