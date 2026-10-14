// Original procedural material. Shared verbatim by WebGL2 and Forge 1.20.1.
uniform float Time;
uniform float RiseSpeed;
uniform float Turbulence;
uniform float TongueLength;
uniform float Erosion;
uniform float Intensity;
uniform float Opacity;
uniform vec3 CoreColor;
uniform vec3 MidColor;
uniform vec3 EdgeColor;
float fcHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}
float fcNoise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(fcHash(i),fcHash(i+vec2(1,0)),f.x),mix(fcHash(i+vec2(0,1)),fcHash(i+vec2(1,1)),f.x),f.y);}
float fcFbm(vec2 p){return .57*fcNoise(p)+.29*fcNoise(p*2.03+vec2(17.1,5.7))+.14*fcNoise(p*4.11+vec2(9.4,23.2));}
vec4 shadeFire(vec2 uv,float kind,float age,float seed,float strength){
 if(kind>1.5){
   vec2 q=(uv-.5)*2.0;float d=length(q);float alpha=(1.0-smoothstep(.1,1.0,d))*pow(max(0.0,1.0-age),1.25)*strength;
   vec3 color=mix(MidColor,CoreColor,exp(-5.0*d*d));
   return vec4(color*Intensity,alpha*Opacity);
 }
 float localTime=Time*RiseSpeed;
 float u=uv.x;
 float v=uv.y;
 if(kind>.5){
   // Detached flame silhouette, tapered upwards and alive internally.
   float sway=(sin(v*7.0+seed*18.0-Time*2.1)*.09+(fcNoise(vec2(v*3.1-localTime*1.8,seed*79.0))-.5)*.25)*Turbulence;
   float halfWidth=(.12+.28*sin(clamp(v,0.0,1.0)*3.14159))*(1.0-.7*v);
   float lateral=abs(uv.x-.5+sway*v);
   float contour=1.0-smoothstep(halfWidth-.055,halfWidth+.03,lateral);
   float n=fcFbm(vec2((uv.x+sway)*5.0+seed*31.0,v*3.2/max(.15,TongueLength)-localTime*2.0));
   float holes=smoothstep(.12+Erosion*.35,.34+Erosion*.35,n+.14*(1.0-v));
   float alpha=contour*holes*smoothstep(0.0,.17,v)*(1.0-smoothstep(.69,1.0,v))*pow(max(0.0,1.0-age),1.4)*strength*Opacity;
   float heat=clamp((1.0-v)*.52+n*.30,0.0,1.0);
   vec3 color=mix(EdgeColor,MidColor,smoothstep(.12,.69,heat));color=mix(color,CoreColor,smoothstep(.73,.94,heat)*.45);
   return vec4(color*Intensity,alpha);
 }
 // Scrolling noise defines separate fire tongues along arc-length, not a glowing tube.
 vec2 p=vec2(u*5.5+seed*61.0-localTime*1.7,v*2.2-localTime*.52);
 float warp=(fcNoise(p*.57+vec2(2.4,-Time*.41))-.5)*Turbulence;
 float broad=fcFbm(p+vec2(warp*1.5,warp*.35));
 float detail=fcFbm(vec2(u*13.0+warp*2.0+seed*109.0-localTime*2.3,v*4.7-localTime*1.3));
 float crest=clamp(.12+pow(broad,1.8)*(1.7*TongueLength)+.13*warp,.09,.98);
 float distance=crest-v-Erosion*(detail-.36)*(.14+.52*v);
 float edgeAA=max(.013,fwidth(distance)*1.3);
 float silhouette=smoothstep(-edgeAA,edgeAA*2.0,distance)*smoothstep(.015,.16,v)*(1.0-smoothstep(.92,1.0,v));
 float holes=smoothstep(Erosion*.36-.06,Erosion*.36+.25,detail+.13*(1.0-v));
 float flow=fcFbm(vec2(u*.92-localTime*.68,seed*47.0+Time*.13));
 float breaks=smoothstep(.22+Erosion*.17,.54+Erosion*.17,flow);
 float alpha=silhouette*holes*breaks*strength*Opacity;
 float heat=clamp((crest-v)/max(.12,crest)*.57+detail*.29,0.0,1.0);
 vec3 color=mix(EdgeColor,MidColor,smoothstep(.08,.70,heat));
 float core=smoothstep(.70,.91,heat)*smoothstep(.48,.81,detail)*(1.0-smoothstep(.15,.42,v));
 color=mix(color,CoreColor,core*.65);
 return vec4(color*Intensity,alpha);
}
