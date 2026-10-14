#version 150
uniform vec2 Viewport;
uniform mat4 InverseViewProjection;
uniform sampler2D SceneDepth;
uniform vec3 Camera, BoundsMin, BoundsMax;
uniform float Yaw, Intensity, Opacity;
uniform int Steps;
uniform int ItemSpace;
out vec4 result;

// SHARED_VOLUME_CORE_BEGIN
__SHARED_FIRE_VOLUME_CORE__
// SHARED_VOLUME_CORE_END

vec3 bodyDirection(vec3 v){float c=cos(Yaw),s=sin(Yaw);return vec3(c*v.x+s*v.z,v.y,-s*v.x+c*v.z);}
vec2 volumeBoxHit(vec3 ro,vec3 rd){
 vec3 safe=sign(rd)*max(abs(rd),vec3(.00001));safe+=vec3(equal(safe,vec3(0)))*.00001;
 vec3 a=(BoundsMin-ro)/safe,b=(BoundsMax-ro)/safe,mn=min(a,b),mx=max(a,b);
 return vec2(max(mn.x,max(mn.y,mn.z)),min(mx.x,min(mx.y,mx.z)));
}
void main(){
 vec2 uv=gl_FragCoord.xy/Viewport;
 vec4 farPoint=InverseViewProjection*vec4(uv*2.0-1.0,1.0,1.0);
 vec3 rd=ItemSpace!=0?normalize(farPoint.xyz/farPoint.w-Camera):bodyDirection(normalize(farPoint.xyz/farPoint.w));
 vec2 hit=volumeBoxHit(Camera,rd);
 float depth=texture(SceneDepth,uv).r;
 vec4 surface=InverseViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
 float sceneDistance=depth>=.999999?10000.0:(ItemSpace!=0?length(surface.xyz/surface.w-Camera):length(surface.xyz/surface.w));
 float first=max(0.0,hit.x),last=min(hit.y,sceneDistance);
 if(last<=first){result=vec4(0);return;}
 float ds=(last-first)/float(Steps),transmittance=1.0;
 vec3 light=vec3(0);DensityStep=rd*ds;
 // Stable midpoint integration; temporal random dithering would shimmer in motion.
 for(int i=0;i<512;i++){
  if(i>=Steps||transmittance<.001)break;
  vec3 p=Camera+rd*(first+(float(i)+.5)*ds);
  vec2 field=sampleFireVolume(p,Time);
  float alpha=1.0-exp(-max(0.0,field.x)*ds*Opacity*8.0);
  light+=transmittance*fireVolumeColor(field.y)*Intensity*alpha;
  transmittance*=1.0-alpha;
 }
 float alpha=1.0-transmittance;
 result=vec4(light/max(.00001,alpha),alpha);
}
