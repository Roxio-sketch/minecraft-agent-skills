#version 150
uniform vec2 Viewport;
uniform mat4 InverseViewProjection;
uniform sampler2D SceneDepth;
uniform vec3 Camera, BoundsMin, BoundsMax;
uniform float Yaw;
uniform int Steps;
out vec4 result;

// SHARED_VOLUME_CORE_BEGIN
__SHARED_ALCHEMY_VOLUME_CORE__
// SHARED_VOLUME_CORE_END

vec3 toLocalDirection(vec3 v){float c=cos(Yaw),s=sin(Yaw);return vec3(c*v.x-s*v.z,v.y,s*v.x+c*v.z);}
vec2 boxHit(vec3 ro,vec3 rd){
 vec3 safe=sign(rd)*max(abs(rd),vec3(.00001));safe+=vec3(equal(safe,vec3(0)))*.00001;
 vec3 a=(BoundsMin-ro)/safe,b=(BoundsMax-ro)/safe,mn=min(a,b),mx=max(a,b);
 return vec2(max(mn.x,max(mn.y,mn.z)),min(mx.x,min(mx.y,mx.z)));
}
void main(){
 vec2 uv=gl_FragCoord.xy/Viewport;
 vec4 farPoint=InverseViewProjection*vec4(uv*2.-1.,1.,1.);
 vec3 rd=toLocalDirection(normalize(farPoint.xyz/farPoint.w));
 vec2 hit=boxHit(Camera,rd);
 float depth=texture(SceneDepth,uv).r;
 vec4 surface=InverseViewProjection*vec4(uv*2.-1.,depth*2.-1.,1.);
 float sceneDistance=depth>=.999999?10000.:length(surface.xyz/surface.w);
 float first=max(0.,hit.x),last=min(hit.y,sceneDistance);
 if(last<=first){result=vec4(0);return;}
 float ds=(last-first)/float(Steps),transmittance=1.;vec3 light=vec3(0);
 // Fixed user-selected precision and stable midpoint integration; no automatic LOD.
 for(int i=0;i<512;i++){
  if(i>=Steps)break;
  vec3 p=Camera+rd*(first+(float(i)+.5)*ds);
  vec4 field=sampleAlchemy(p,Time);
  float alpha=1.-exp(-max(0.,field.a)*ds*8.);
  light+=transmittance*field.rgb*alpha;transmittance*=1.-alpha;
 }
 float alpha=1.-transmittance;result=vec4(light/max(.00001,alpha),alpha);
}
