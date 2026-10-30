/* Original V4 black/crimson smoke look development.
 * One screen-facing proxy per bounded 3D field; no solid object or smoke mesh.
 * The field is prescribed, advected procedural density, not a fluid solver.
 */
(function (root) {
  'use strict';
  const add = (a,b) => a.map((v,i)=>v+b[i]);
  const sub = (a,b) => a.map((v,i)=>v-b[i]);
  const mul = (a,s) => a.map(v=>v*s);
  const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
  const unit = a => {const n=Math.hypot(...a);return n>1e-8?mul(a,1/n):[0,0,1];};
  const clamp = (x,a,b) => Math.max(a,Math.min(b,x));
  const rand = (a,b) => {const x=Math.sin(a*127.17+b*311.73)*43758.5453123;return x-Math.floor(x);};
  const basisFor = d => {const x=unit(d),y=unit(cross(x,Math.abs(x[1])>.97?[0,0,1]:[0,1,0]));return [x,y,unit(cross(y,x))];};
  const offset = (p,b,x,y,z) => add(p,add(mul(b[0],x),add(mul(b[1],y),mul(b[2],z))));

  // The quad is placed in front of the field so ordinary depth testing does
  // not discard smoke that lies in front of an occluder. The shader clips its
  // ray against the copied scene depth. A single quad prevents back-face
  // double integration, including when the camera enters a field.
  function proxy(ctx, center, radii) {
    const eye=ctx.eye||add(center,[4,3,5]),delta=sub(center,eye),distance=Math.hypot(...delta);
    const forward=unit(delta),normal=mul(forward,-1);
    const right=unit(cross(Math.abs(normal[1])>.98?[0,0,1]:[0,1,0],normal));
    const up=unit(cross(normal,right)),radius=Math.hypot(...radii)*1.035;
    const plane=add(eye,mul(forward,Math.max(.12,distance-radius)));
    const x=mul(right,radius),y=mul(up,radius),data=[];
    const points=[sub(sub(plane,x),y),add(sub(plane,y),x),add(add(plane,x),y),add(sub(plane,x),y)];
    const uv=[[-1,-1],[1,-1],[1,1],[-1,1]];
    for(const i of [0,1,2,0,2,3])ctx.vertex(data,points[i],[1,1,1,1],uv[i],normal);
    return data;
  }

  function anchor(ctx) {
    const state=ctx.state||(ctx.state={});let s=state.smokeV4Anchor;
    if(!s||ctx.time<s.lastTime||ctx.moving){
      s=state.smokeV4Anchor={pos:ctx.pos.slice(),basis:ctx.basis.map(v=>v.slice()),lastTime:ctx.time,stopTime:ctx.time,moving:ctx.moving};
    }else if(s.moving){s.moving=false;s.stopTime=ctx.time;}
    s.lastTime=ctx.time;
    return {pos:ctx.moving?ctx.pos:s.pos,basis:ctx.moving?ctx.basis:s.basis,age:ctx.moving?0:Math.max(0,ctx.time-s.stopTime)};
  }

  function smoke(ctx) {
    const out={opaque:[],alpha:[],additive:[],diagnostics:{}},p=ctx.params,a=anchor(ctx);
    const fade=ctx.moving?1:Math.pow(clamp(ctx.motionFade,0,1),1.16);
    const axial=.88+p.shellLength*.43,width=.35+p.spread*.48+p.width*.38;
    const height=.25+p.width*.72+p.spread*.15;
    if(fade>.001){
      const expansion=1+a.age*.12;
      const center=offset(a.pos,a.basis,-axial*.56,0,.02);
      const radii=[axial*expansion,width*expansion,height*expansion];
      out.alpha.push({data:proxy(ctx,center,radii),mode:10,center,
        volume:{center,axes:a.basis,radii,fade,seed:ctx.seed*.83,glow:p.glow*1.65}});
    }

    // Detached remnants are selected from persistent fixed-distance births.
    // Birth identity, world position and direction never follow the camera or
    // current emitter, and no segment is joined to another segment.
    const remnantLife=Math.min(p.life,1.55),firstAge=Math.max(.40,axial/Math.max(1,p.speed)*1.30);
    const stride=Math.max(8,Math.round(18-clamp(p.strands,2,12)*.65));
    const births=[];
    for(const h of ctx.history||[]){
      const age=ctx.time-h.t,serial=Math.round((h.arc||0)/.14);
      if(age>firstAge&&age<remnantLife&&serial%stride===0)births.push(h);
    }
    for(const h of births.slice(-5)){
      const age=ctx.time-h.t,t=age/remnantLife,r=rand(ctx.seed,h.t),b=basisFor(h.dir||ctx.dir);
      const growth=1+age*.32,drift=(r-.5)*age*.16;
      const center=offset(h.p,b,-age*.10,drift,age*.07);
      const radii=[(.42+r*.26)*growth,width*(.46+r*.20)*growth,height*(.54+r*.18)*growth];
      const opacity=Math.pow(1-t,1.8)*.65;
      out.alpha.push({data:proxy(ctx,center,radii),mode:10,center,
        volume:{center,axes:b,radii,fade:opacity,seed:ctx.seed+h.t*4.7,glow:p.glow*.22*(1-t)}});
    }
    out.diagnostics={topology:'short-broad-crimson-black-cloak-with-rolled-density-and-detached-world-remnants',
      raymarchSteps:88,volumePackets:out.alpha.length,proxyTriangles:out.alpha.length*2,
      density:'bounded 3D domain-warped multi-octave value noise with axial profile and rolled lobes',
      lighting:'two density probes toward an artistic directional light, Beer-Lambert absorption and localized colored emission',
      positionSpace:'local frozen-on-stop cloak plus historical world-space births; no connections across segments',
      stopping:'main center and basis freeze; density fades, field expands; old births expire independently',
      shaderOnly:true,modelsIncluded:false,carrierRemainsAfterStop:false,
      depth:'single front proxy plus reconstructed opaque scene-depth ray clipping',
      limitations:['prescribed procedural density, not fluid simulation','light is an artistic approximation without multiple scattering','overlapping bounded fields use packet-center alpha sorting','transparent objects are not represented in the opaque scene depth']};
    return out;
  }

  const shaderFunctions=String.raw`
uniform vec3 uVolumeCenter;
uniform mat3 uVolumeBasis;
uniform vec3 uVolumeRadii;
uniform float uVolumeFade;
uniform float uVolumeSeed;
uniform float uVolumeGlow;
uniform vec3 uVolumeCore;
uniform vec3 uVolumeEdge;
uniform sampler2D uSceneDepth;
uniform vec2 uViewport;
uniform mat4 uInverseVP;
uniform int uMonochrome;

float smokeHash3(vec3 p){
  p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);
}
float smokeNoise3(vec3 p){
  vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
  return mix(mix(mix(smokeHash3(i),smokeHash3(i+vec3(1,0,0)),f.x),
                 mix(smokeHash3(i+vec3(0,1,0)),smokeHash3(i+vec3(1,1,0)),f.x),f.y),
             mix(mix(smokeHash3(i+vec3(0,0,1)),smokeHash3(i+vec3(1,0,1)),f.x),
                 mix(smokeHash3(i+vec3(0,1,1)),smokeHash3(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float smokeFbm3(vec3 p){
  float n=0.,amplitude=.55;
  const mat3 spin=mat3(.00,.80,.60,-.80,.36,-.48,-.60,-.48,.64);
  for(int octave=0;octave<4;octave++){n+=smokeNoise3(p)*amplitude;p=spin*p*2.04+vec3(13.1,7.7,3.4);amplitude*=.48;}
  return n;
}

// Returns density, local heat, texture modulation and radial surface distance.
vec4 smokeField(vec3 q){
  float tail=clamp((1.-q.x)*.5,0.,1.);
  float profile=.34+.66*pow(max(0.,sin(tail*3.14159265)),.52);
  float roll=q.x*2.3-uTime*.52+uVolumeSeed*.011;
  mat2 turn=mat2(cos(roll),-sin(roll),sin(roll),cos(roll));
  vec3 npos=vec3(q.x*2.4,turn*q.yz*3.2);
  npos+=vec3(uTime*.68,uTime*.09,-uTime*.12)+uVolumeSeed*vec3(.071,.019,.037);
  vec3 warp=vec3(smokeNoise3(npos*.67),smokeNoise3(npos*.67+12.8),smokeNoise3(npos*.67+26.1))-.5;
  float n=smokeFbm3(npos+warp*1.36);
  vec2 yz=q.yz/profile+warp.yz*.52;
  // Broad lobes and broken trailing edges make a cloak, not a smooth capsule.
  float angle=atan(yz.y,yz.x);
  float billow=smokeNoise3(npos*.74+vec3(7.3,2.1,4.8));
  float radial=length(yz)+.16*sin(angle*3.+q.x*4.2-uTime*.64)
                +.075*sin(angle*5.-q.x*6.1+n*3.4)+(billow-.5)*.32;
  // The rear break varies across the section. Both outer lobes and the rear
  // silhouette therefore break into substantial folds, not a soft capsule.
  float rear=-.86+.12*sin(angle*3.1+q.y*2.7)+.10*(billow-.5);
  float nose=.81+.09*sin(angle*2.7+n*4.2);
  float axial=smoothstep(rear,rear+.20,q.x)*(1.-smoothstep(nose-.14,nose,q.x));
  float bulk=max(0.,(.98-radial)*2.30+(n-.44)*1.17);
  float erosion=smoothstep(.24,.45,n+(.79-radial)*.31);
  float clumps=.22+1.65*pow(clamp(billow*.66+n*.34,0.,1.),1.50);
  float density=bulk*erosion*axial*(.84+.83*n)*clumps*uVolumeFade;
  // Three localized, independently moving heat pockets reveal noise-isosurface
  // folds. There is no repeated axial sine band or evenly spaced red stripe.
  vec3 h0=(q-vec3(.51,-.35+.025*sin(uTime*.43),.24))*vec3(5.8,4.3,4.4);
  vec3 h1=(q-vec3(.15,.35,-.10+.035*sin(uTime*.61+1.7)))*vec3(4.9,4.8,4.7);
  vec3 h2=(q-vec3(-.13,-.38+.022*sin(uTime*.37+3.1),.16))*vec3(5.0,4.5,4.9);
  float patches=exp(-dot(h0,h0))*1.18+exp(-dot(h1,h1))*.72+exp(-dot(h2,h2))*.43;
  float folded=smokeNoise3(npos*1.31+warp*2.8+vec3(1.9,-2.6,4.7));
  float crease=pow(max(0.,1.-abs(folded-.48)*13.0),2.);
  float outerSheet=.30+.70*exp(-pow((radial-.61)*4.6,2.));
  float heat=patches*(.30+crease*1.18)*outerSheet;
  // Leading knots are sparse rose-red accents inside the dominant black body.
  heat+=exp(-dot(h0,h0)*3.4)*(.36+pow(max(0.,n-.31)*2.2,2.)*.78);
  return vec4(density,heat,n,radial);
}
`;

  const shaderBranch=String.raw`
 if(uMode==10){
   if(uVolumeFade<.0001)discard;
   vec3 rd=normalize(vWorld-uEye);
   mat3 inverseBasis=transpose(uVolumeBasis);
   vec3 ro=inverseBasis*(uEye-uVolumeCenter)/uVolumeRadii;
   vec3 localRay=inverseBasis*rd/uVolumeRadii;
   vec3 safeRay=mix(vec3(1.e-7),localRay,greaterThan(abs(localRay),vec3(1.e-7)));
   vec3 ta=(-vec3(1.)-ro)/safeRay,tb=(vec3(1.)-ro)/safeRay;
   vec3 tn=min(ta,tb),tf=max(ta,tb);
   float tStart=max(0.,max(tn.x,max(tn.y,tn.z)));
   float tEnd=min(tf.x,min(tf.y,tf.z));
   vec2 depthUV=gl_FragCoord.xy/uViewport;
   float sceneDepth=texture(uSceneDepth,depthUV).r;
   if(sceneDepth<.99999){
     vec4 opaqueWorld=uInverseVP*vec4(depthUV*2.-1.,sceneDepth*2.-1.,1.);
     opaqueWorld.xyz/=opaqueWorld.w;
     tEnd=min(tEnd,dot(opaqueWorld.xyz-uEye,rd));
   }
   if(tEnd<=tStart)discard;
   float stepLength=(tEnd-tStart)/88.;
   float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
   float rayT=tStart+stepLength*(.25+.5*jitter);
   float transmittance=1.;vec3 accumulated=vec3(0.),emitted=vec3(0.);
   vec3 lightLocal=normalize(inverseBasis*normalize(vec3(-.4,.85,.35)));
   vec3 lightRay=lightLocal/uVolumeRadii;
   vec3 emissionHue=mix(pow(max(uVolumeEdge,vec3(0.)),vec3(2.0)),pow(max(uVolumeCore,vec3(0.)),vec3(3.5)),.22);
   emissionHue/=max(.001,max(emissionHue.r,max(emissionHue.g,emissionHue.b)));
   vec3 weakTint=mix(uVolumeEdge,uVolumeCore,.17)*.008;
   for(int stepIndex=0;stepIndex<88;stepIndex++){
     vec3 q=ro+localRay*rayT;vec4 field=smokeField(q);
     if(field.x>.0001){
       float absorption=1.-exp(-field.x*stepLength*3.25);
       float weight=transmittance*absorption;
       // Two actual field probes expose the 3D rolls. The first measures the
       // density slope; the second estimates light occlusion farther into a
       // billow. This is an artistic single-scattering approximation, not an
       // environment-shadow or multiple-scattering solver.
       float nearDensity=smokeField(q+lightRay*.13).x;
       float farDensity=smokeField(q+lightRay*.37).x;
       float relief=pow(clamp(.45+(field.x-nearDensity)*2.7,0.,1.),1.20);
       float lightVisibility=exp(-(nearDensity*.13+farDensity*.30)*3.0);
       float inner=clamp(field.x*.90,0.,1.);
       vec3 scattering=vec3(.008,.006,.009)*(.64+.70*field.z);
       scattering+=(vec3(.060,.051,.056)+weakTint)*(.15+.85*relief)*lightVisibility*inner;
       scattering+=emissionHue*(.030*field.y*uVolumeGlow);
       vec3 emission=emissionHue*(1.96*field.y*uVolumeGlow);
       accumulated+=weight*(scattering+emission*.62);
       emitted+=weight*emission*.38;
       transmittance*=1.-absorption;
       if(transmittance<.012)break;
     }
     rayT+=stepLength;
   }
   float smokeAlpha=1.-transmittance;
   if(smokeAlpha<.0004)discard;
   vec3 sceneRGB=accumulated/max(smokeAlpha,.0001);
   vec3 glowRGB=emitted/max(smokeAlpha,.0001);
   if(uMonochrome==1){sceneRGB=vec3(dot(sceneRGB,vec3(.2126,.7152,.0722)));glowRGB=vec3(dot(glowRGB,vec3(.2126,.7152,.0722)));}
   outColor=vec4(sceneRGB,smokeAlpha);
   outGlow=vec4(glowRGB,smokeAlpha);
   return;
 }
`;
  root.DunguangExtraForms=root.DunguangExtraForms||{};
  root.DunguangExtraForms.smoke=smoke;
  root.DunguangSmokeV4={shaderFunctions,shaderBranch,steps:88,field:'bounded prescribed procedural 3D density; not a fluid solver'};
})(typeof window!=='undefined'?window:globalThis);
