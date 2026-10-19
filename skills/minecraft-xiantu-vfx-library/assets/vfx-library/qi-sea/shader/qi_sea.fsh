#version 150
uniform sampler2D Sampler0;
uniform float Time;
uniform float ManaFill;
uniform float Progress;
uniform float FiveMode;
uniform vec4 SeaProgress;
uniform float EarthProgress;
uniform vec3 RootColor;
uniform vec4 SeaPhase;
uniform float EarthPhase;
uniform float SoloPhase;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
const float TAU=6.28318530718;
float hash21(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float noise2(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash21(i),hash21(i+vec2(1,0)),f.x),mix(hash21(i+vec2(0,1)),hash21(i+vec2(1,1)),f.x),f.y);}
float fbm(vec2 p){float s=0.0,a=.5;for(int i=0;i<4;i++){s+=a*noise2(p);p=mat2(.8,-.6,.6,.8)*p*2.03+vec2(3.1,1.7);a*=.5;}return s;}
mat2 rotate2(float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c);}
vec3 elementColor(int i){if(i==0)return vec3(1.0,.76,.25);if(i==1)return vec3(.16,.91,.39);if(i==2)return vec3(.12,.49,1.0);if(i==3)return vec3(1.0,.16,.07);return vec3(1.0,.46,.12);}
vec2 elementCenter(int i){if(i==0)return vec2(0,-.37);if(i==1)return vec2(-.35,-.03);if(i==2)return vec2(.35,-.03);if(i==3)return vec2(-.22,.31);return vec2(.22,.31);}
float elementPhase(int i){if(i==0)return SeaPhase.x;if(i==1)return SeaPhase.y;if(i==2)return SeaPhase.z;if(i==3)return SeaPhase.w;return EarthPhase;}
float level(int i){if(i==0)return SeaProgress.x;if(i==1)return SeaProgress.y;if(i==2)return SeaProgress.z;if(i==3)return SeaProgress.w;return EarthProgress;}
// Advected, twisted fBm builds moving gas filaments rather than rotating a baked sprite.
vec4 gasVortex(vec2 q,vec3 color,float phase,float strength,float clock){
 float r=length(q);float t=phase;
 vec2 adv=rotate2(t*.44+2.8*exp(-r*.9))*q;
 float density=fbm(adv*2.8+vec2(clock*.12,-clock*.09));
 float angle=atan(q.y+0.00001,q.x+0.000001);float spiral=angle*3.0+5.8*log(r+.16)-t*2.0+density*3.1;
 float arms=pow(.5+.5*sin(spiral),3.0);
 float silk=pow(.5+.5*sin(spiral*2.03+fbm(adv*7.0-clock*.14)*4.0),7.0);
 float cloud=smoothstep(.25,.76,density+arms*.25);
 float envelope=exp(-r*r*1.5)*(1.0-smoothstep(.93,1.25,r));
 float core=exp(-r*r*33.0);
 float energy=(.23+cloud*.40+arms*.62+silk*.42+core*.6)*envelope;
 float brightness=.46+1.35*strength;
 vec3 glow=color*energy*brightness;
 glow+=mix(color,vec3(1.0),.38)*silk*envelope*(.12+.30*strength);
 float opacity=clamp(envelope*(.35+cloud*.46+arms*.22),0.0,.96);
 return vec4(glow,opacity);
}
void main(){
 vec4 base=texture(Sampler0,texCoord0);vec2 p=texCoord0*2.0-1.0;float radius=length(p);
 float inner=1.0-smoothstep(.568,.615,radius);
 // The texture is retained outside the inner disk; its baked blue vortex is fully replaced here.
 float reservoir=clamp(ManaFill,0.0,1.0);
 vec3 interior=mix(vec3(.014,.035,.040),RootColor*.11,.3+reservoir*.7);
 interior+=RootColor*.035*fbm(p*9.0+Time*.03);
 if(FiveMode>.5){
  interior=vec3(.012,.023,.027);
  for(int i=0;i<5;i++){
   vec2 q=(p-elementCenter(i))/.225;
   vec4 v=gasVortex(q,elementColor(i),float(i)*1.73+elementPhase(i),clamp(level(i),0.0,1.0),Time);
   interior=mix(interior,interior+v.rgb*(.22+.78*reservoir),v.a);
  }
  float shared=exp(-dot(p,p)*30.0)*(.03+.12*Progress);interior+=vec3(.55,.72,.65)*shared;
 }else{
  vec4 v=gasVortex(p/.55,RootColor,SoloPhase,Progress,Time);interior+=v.rgb*(.20+.80*reservoir);
 }
 // Near-empty mana has only dim wisps; cultivation controls speed/brightness independently.
 float waterline=smoothstep(-.05,.05,p.y-(.64-1.28*reservoir));
 interior*=mix(.48,1.0,waterline);
 vec3 rgb=mix(base.rgb,interior,inner);
 if(FiveMode<.5){float gray=dot(rgb,vec3(.299,.587,.114));vec3 tinted=gray*mix(vec3(.7,.85,.82),RootColor*1.15+.17,.68);rgb=mix(rgb,tinted,(1.0-inner)*.68);}
 rgb=rgb/(vec3(1.0)+rgb*.24);
 fragColor=vec4(rgb,base.a)*vertexColor;
}
