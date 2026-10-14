#version 150
in vec3 Position;in vec4 Color;in vec2 UV0;
uniform mat4 ModelViewMat,ProjMat;
uniform float uTime,uDistortion,uDistortionScale,uDistortionSpeed,uPulseAmplitude,uPulseFrequency,uShape,uDepth,uMode;
out vec3 vPos,vNormal,viewPosition,viewNormal;out vec2 vUV;out float vertexAlpha;

float hash3(vec3 q){q=fract(q*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
float noise3(vec3 q){
 vec3 i=floor(q),f=fract(q);f=f*f*(3.-2.*f);
 float a=mix(hash3(i),hash3(i+vec3(1.,0.,0.)),f.x);
 float b=mix(hash3(i+vec3(0.,1.,0.)),hash3(i+vec3(1.,1.,0.)),f.x);
 float c=mix(hash3(i+vec3(0.,0.,1.)),hash3(i+vec3(1.,0.,1.)),f.x);
 float d=mix(hash3(i+vec3(0.,1.,1.)),hash3(i+vec3(1.,1.,1.)),f.x);
 return mix(mix(a,b,f.y),mix(c,d,f.y),f.z);
}

void main(){
 vec3 original,normal;
 if(uShape<1.5){float ph=UV0.y*3.14159265*(uShape>.5?.5:1.),th=UV0.x*6.2831853;original=vec3(2.*sin(ph)*cos(th),2.*cos(ph),2.*sin(ph)*sin(th)*uDepth);normal=normalize(vec3(original.xy,original.z/(uDepth*uDepth)));}
 else {float a=(UV0.x-.5)*3.14159265,b=(UV0.y-.5)*3.14159265,k=uShape<2.5?uDepth:0.;original=vec3((UV0.x-.5)*4.4,(.5-UV0.y)*2.8,k*cos(a)*cos(b));normal=normalize(vec3(k*3.14159265/4.4*sin(a)*cos(b),-k*3.14159265/2.8*cos(a)*sin(b),1.));}
 vec3 worldNormal=normalize(Color.rgb*2.-1.);float d=0.;
 if(uMode<.5){d=(noise3(original*uDistortionScale+vec3(uTime*uDistortionSpeed))-.5)*uDistortion+sin(uTime*uPulseFrequency*6.283185)*uPulseAmplitude;}
 vec4 p=ModelViewMat*vec4(Position+worldNormal*d,1.);viewPosition=p.xyz;viewNormal=normalize(mat3(ModelViewMat)*worldNormal);vPos=original+normal*d;vNormal=normal;vUV=UV0;vertexAlpha=Color.a;gl_Position=ProjMat*p;
}