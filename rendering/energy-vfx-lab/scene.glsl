// Camera and depth reconstruction shared by volume integration and full-resolution composition.
uniform vec2 viewport;
uniform vec3 camera,rightVec,upVec,forwardVec;
uniform float tanHalfFov;
uniform int gameScene,environment;
uniform sampler2D sceneColor,sceneDepth;
uniform mat4 inverseViewProjection;
uniform vec3 avatarOffset;
vec3 ray(vec2 uv){vec2 p=(uv*2.-1.)*vec2(viewport.x/viewport.y,1);return normalize(forwardVec+(rightVec*p.x+upVec*p.y)*tanHalfFov);}
vec2 boxHit(vec3 ro,vec3 rd,vec3 lo,vec3 hi){vec3 safe=sign(rd)*max(abs(rd),vec3(.00001));safe+=vec3(equal(safe,vec3(0)))*.00001;vec3 a=(lo-ro)/safe,b=(hi-ro)/safe;vec3 mn=min(a,b),mx=max(a,b);return vec2(max(mn.x,max(mn.y,mn.z)),min(mx.x,min(mx.y,mx.z)));}
vec4 scene(vec2 uv){
 vec3 rd=ray(uv);
 if(gameScene==1){float depth=texture(sceneDepth,uv).r;vec4 world=inverseViewProjection*vec4(uv*2.-1.,depth*2.-1.,1);float dist=depth>=.999999?10000.:length(world.xyz/world.w);return vec4(texture(sceneColor,uv).rgb,dist);}
 vec3 bg=mix(vec3(.007,.012,.022),vec3(.024,.037,.053),max(rd.y,0.));float dist=10000.;
 if(environment==0)return vec4(bg,dist);
 if(rd.y<-.0001){float hit=(-1.7-camera.y)/rd.y;if(hit>0.){vec3 at=camera+hit*rd;float grid=mod(floor(at.x)+floor(at.z),2.);float fade=exp(-hit*.028);bg=mix(bg,mix(vec3(.025,.038,.05),vec3(.09,.12,.14),grid)*fade,.9);dist=hit;}}
 if(environment==2){vec2 b=boxHit(camera,rd,vec3(-.75,-1.7,1.4),vec3(.35,.15,2.05));if(b.x>0.&&b.y>=b.x&&b.x<dist){dist=b.x;bg=vec3(.26,.33,.37);}}
 if(environment==3){bg=mix(vec3(.28,.35,.42),vec3(.7,.78,.85),max(rd.y,0.));}
 if(environment==4){
  vec3 lo[7]=vec3[7](vec3(-.24,-.45,-.15),vec3(-.22,.35,-.22),vec3(-.47,-.58,-.12),vec3(.26,-.58,-.12),vec3(-.23,-1.15,-.12),vec3(.02,-1.15,-.12),vec3(.54,-.35,-.05));
  vec3 hi[7]=vec3[7](vec3(.24,.36,.15),vec3(.22,.8,.22),vec3(-.26,.32,.12),vec3(.47,.32,.12),vec3(-.02,-.45,.12),vec3(.23,-.45,.12),vec3(.66,.72,.05));
  for(int i=0;i<7;i++){vec2 hit=boxHit(camera,rd,lo[i]+avatarOffset,hi[i]+avatarOffset);if(hit.x>0.&&hit.y>=hit.x&&hit.x<dist){dist=hit.x;vec3 at=camera+rd*dist;float panel=.8+.2*step(.5,fract(at.y*8.));bg=(i==1?vec3(.38,.33,.27):i==6?vec3(.35,.57,.72):vec3(.14,.22,.29))*panel;}}
 }
 return vec4(bg,dist);
}
