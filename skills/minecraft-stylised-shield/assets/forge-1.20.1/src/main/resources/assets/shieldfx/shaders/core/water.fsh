#version 150
in vec3 vPos,vNormal;
in vec2 vUV;
uniform vec3 uColor,uEdgeColor;
uniform float uTime,uOpacity,uCenterOpacity,uEdgeOpacity,uFresnelPower,uFresnelStrength,uLife;
uniform float uHexScale,uHexOpacity,uLineWidth,uFlashSpeed,uFlashIntensity,uShowHex;
uniform float uFlowScale,uFlowSpeed,uFlowIntensity,uShowFlow;
uniform float uReveal,uNoiseScale,uNoiseEdgeWidth,uNoiseEdgeIntensity,uShowDissolve;
uniform float uScanSpeed,uScanStrength,uScanWidth,uShowScan;
uniform float uHitSpeed,uHitWidth,uHitDuration,uHitIntensity,uHitRadius;
uniform float uPattern,uShape;
uniform vec3 uHit0;uniform float uHitAge0;
uniform vec3 uHit1;uniform float uHitAge1;
uniform vec3 uHit2;uniform float uHitAge2;
uniform vec3 uHit3;uniform float uHitAge3;
uniform vec3 uHit4;uniform float uHitAge4;
uniform vec3 uHit5;uniform float uHitAge5;

float hash3(vec3 q){q=fract(q*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
float noise3(vec3 q){
 vec3 i=floor(q),f=fract(q);f=f*f*(3.-2.*f);
 float a=mix(hash3(i),hash3(i+vec3(1.,0.,0.)),f.x);
 float b=mix(hash3(i+vec3(0.,1.,0.)),hash3(i+vec3(1.,1.,0.)),f.x);
 float c=mix(hash3(i+vec3(0.,0.,1.)),hash3(i+vec3(1.,0.,1.)),f.x);
 float d=mix(hash3(i+vec3(0.,1.,1.)),hash3(i+vec3(1.,1.,1.)),f.x);
 return mix(mix(a,b,f.y),mix(c,d,f.y),f.z);
}

float hexLine(vec2 q){
 q*=uHexScale;vec2 s=vec2(1.,1.7320508);
 vec2 a=mod(q,s)-s*.5,b=mod(q-s*.5,s)-s*.5;
 vec2 cell=dot(a,a)<dot(b,b)?a:b;cell=abs(cell);
 float edge=max(dot(cell,vec2(.5,.8660254)),cell.x);
 return smoothstep(.5-uLineWidth,.5,edge);
}

float planePattern(vec2 q){
 if(uPattern==0.)return hexLine(q);
 if(uPattern==1.){vec2 g=q*uHexScale;float d=min(abs(fract(g.x+g.y)-.5),abs(fract(g.x-g.y)-.5));return 1.-smoothstep(0.,uLineWidth,d);}
 if(uPattern==2.){vec2 g=fract(q*uHexScale)-.5;return 1.-smoothstep(.10,.16,length(g));}
 if(uPattern==3.){vec2 g=abs(fract(q*uHexScale)-.5);return 1.-smoothstep(0.,uLineWidth,min(g.x,g.y));}
 return 0.;
}out vec4 fragColor;
in vec3 viewPosition,viewNormal;
in float vertexAlpha;
uniform float uMode,uAlphaScale,uShatterAge,uShatterFlash;
uniform vec3 uSparkColor;
void main(){
 if(uMode>2.5){fragColor=vec4(1.-exp(-uSparkColor*2.5),vertexAlpha);return;}
 vec3 uHits[6]=vec3[6](uHit0,uHit1,uHit2,uHit3,uHit4,uHit5);
 float uHitAge[6]=float[6](uHitAge0,uHitAge1,uHitAge2,uHitAge3,uHitAge4,uHitAge5);
 vec3 n=normalize(viewNormal),view=normalize(-viewPosition);
 float face=pow(clamp(abs(dot(n,view)),0.,1.),.45);float rim=pow(clamp(1.-abs(dot(n,view)),0.,1.),uFresnelPower)*uFresnelStrength;
 vec3 an=abs(normalize(vNormal));vec2 faceUV=vPos.xy;float pattern=0.;
 if(uShape<2.){
  // 单一投影：交界处留白，绝不叠加多套图案。
  float dominance=max(an.x,max(an.y,an.z));
  if(an.x>=an.y&&an.x>=an.z)faceUV=vPos.yz;
  else if(an.y>=an.z)faceUV=vPos.xz;
  else faceUV=vPos.xy;
  float seamGap=smoothstep(.74,.88,dominance);
  pattern=planePattern(faceUV)*seamGap;
 }else pattern=planePattern(faceUV);
 pattern*=uShowHex;
 float t=uTime*uFlowSpeed;
 float flow=noise3(vPos*uFlowScale+vec3(t,t*.6,t*.4))*.6+noise3(vPos*uFlowScale*2.1+vec3(-t*.5,t*.9,t*.3))*.4;
 flow=pow(flow,2.)*uFlowIntensity*uShowFlow;
 float scan=(1.-smoothstep(0.,uScanWidth,abs(fract(vPos.y*.18-uTime*uScanSpeed)-.5)))*uScanStrength*uShowScan;
 float mask=1.,dissolveEdge=0.;
 if(uShowDissolve>.5){
  float noise=noise3(vPos*uNoiseScale);
  mask=1.-smoothstep(uReveal-uNoiseEdgeWidth,uReveal+uNoiseEdgeWidth,noise);
  dissolveEdge=(1.-smoothstep(0.,uNoiseEdgeWidth,abs(noise-uReveal)))*uNoiseEdgeIntensity;
  if(uReveal>=.999){mask=1.;dissolveEdge=0.;}if(uReveal<=.001){mask=0.;dissolveEdge=0.;}
 }
 if(mask<.001&&dissolveEdge<.001)discard;
 float border=0.;if(uShape>=2.)border=1.-smoothstep(.002,.015,min(min(vUV.x,1.-vUV.x),min(vUV.y,1.-vUV.y)));
 if(uShape==1.)border=1.-smoothstep(.002,.02,1.-vUV.y);
 float rings=0.,flash=0.;
 for(int i=0;i<6;i++){
  float age=uHitAge[i];float hitActive=step(0.,age)*(1.-step(uHitDuration,age));
  float dist=length(vPos-uHits[i]);
  if(uShape<2.)dist=acos(clamp(dot(normalize(vPos),normalize(uHits[i])),-1.,1.))*2.;
  float fade=1.-smoothstep(uHitDuration*.35,uHitDuration,age);
  float radius=min(age*uHitSpeed,uHitRadius);
  float wave=1.-smoothstep(0.,uHitWidth,abs(dist-radius+(noise3(vPos*6.+vec3(age))- .5)*.06));
  rings+=wave*hitActive*fade*(1.-smoothstep(uHitRadius*.75,uHitRadius,radius));
  flash+=(1.-smoothstep(0.,.65,dist))*hitActive*exp(-age*9.);
 }
 rings=1.-exp(-rings);flash=1.-exp(-flash);
 float cellPhase=hash3(floor(vec3(faceUV*uHexScale,1.)))*6.283185;
 float cellFlash=pow(max(0.,sin(uTime*uFlashSpeed+cellPhase)),12.)*uFlashIntensity*uShowHex;
 vec3 tint=mix(vec3(1.,.075,.025),uColor,uLife);
 vec3 energy=tint*(uCenterOpacity*(.85+face*1.35)+pattern*uHexOpacity*(.55+flow)+flow*(.28+rim*.8)+scan+cellFlash);
 energy+=uEdgeColor*(rim*1.45+border*1.15+dissolveEdge)+mix(tint,uEdgeColor,.6)*(rings+flash*1.5)*uHitIntensity*.58;
 float alpha=clamp(uOpacity*(uCenterOpacity*(1.05+face*1.25)+rim*uEdgeOpacity*1.15+border*.7+pattern*uHexOpacity*.22+flow*.16+scan*.2)+(rings*.28+flash*.45)*uHitIntensity,0.,.97)*max(mask,dissolveEdge*.3);
 vec3 mapped=1.-exp(-energy*1.4);
 
 float breakFlash=uMode>.5?exp(-uShatterAge*7.)*uShatterFlash:0.;
 if(uMode>.5){energy+=vec3(1.,.22,.06)*(.28+breakFlash);mapped=1.-exp(-energy*1.4);alpha=clamp(max(alpha,uOpacity*.46)+breakFlash*.18*uOpacity,0.,.92);}
 if(uMode>1.5){mapped=1.-exp(-vec3(2.4,1.15,.32)*(1.+breakFlash)*1.4);alpha=.9*uOpacity;}
 fragColor=vec4(mapped,alpha*vertexAlpha*uAlphaScale);
}