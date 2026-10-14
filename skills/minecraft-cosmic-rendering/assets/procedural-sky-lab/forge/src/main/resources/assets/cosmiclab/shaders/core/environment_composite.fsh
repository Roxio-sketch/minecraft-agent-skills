#version 150
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EnvironmentSampler;
uniform mat4 InverseProjection;
uniform vec2 LowSize;
uniform float DepthTolerance;
uniform int OutputDeltaEncoding;
in vec2 screenUV;out vec4 fragColor;
float sceneDistance(vec2 uv,float depth){vec4 p=InverseProjection*vec4(uv*2.-1.,depth*2.-1.,1.);vec4 eye=InverseProjection*vec4(0.,0.,1.,0.);return length(p.xyz/max(abs(p.w),1e-7)-eye.xyz/eye.w);}
void main(){
 vec3 original=texture(SceneSampler,screenUV).rgb;
 float depth=texture(DepthSampler,screenUV).r;bool sky=depth>=.999999;
 float distanceToScene=sceneDistance(screenUV,depth);
 vec2 pixel=screenUV*LowSize-.5,lower=floor(pixel),f=fract(pixel);
 vec3 difference=vec3(0.);float weights=0.;
 for(int y=0;y<2;y++)for(int x=0;x<2;x++){
   vec2 uv=(lower+vec2(x,y)+.5)/LowSize;
   float lowDepth=texture(DepthSampler,uv).r;bool lowSky=lowDepth>=.999999;
   float weight=(x==0?1.-f.x:f.x)*(y==0?1.-f.y:f.y);
   if(sky!=lowSky)weight=0.;
   else if(!sky){float lowDistance=sceneDistance(uv,lowDepth);float relative=abs(lowDistance-distanceToScene)/max(min(lowDistance,distanceToScene),1.);if(relative>4.*max(DepthTolerance,.001))weight=0.;else weight*=exp(-relative/max(DepthTolerance,.001));}
   vec3 delta=texture(EnvironmentSampler,uv).rgb;
   if(OutputDeltaEncoding==2)delta=(delta-128./255.)*(255./127.);
   difference+=delta*weight;weights+=weight;
 }
 fragColor=vec4(clamp(original+(weights>1e-5?difference/weights:vec3(0.)),0.,1.),1.);
}
