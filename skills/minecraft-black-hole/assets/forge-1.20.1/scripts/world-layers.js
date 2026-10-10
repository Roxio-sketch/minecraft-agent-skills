'use strict';
window.blackHoleLayers=(async()=>{
  const read=async name=>{const r=await fetch(name);if(!r.ok)throw Error(name+': '+r.status);return r.text();};
  const [structure,halo,vertex,particleHeader,particleMain,styles]=await Promise.all(
    ['disk_structure.glsl','halo.glsl','particles.vsh','particles_header.glsl','particles_main.glsl','styles.glsl'].map(read));
  const styleIndex=s=>({classic:0,granular:1,ribbons:2,dust:3,cinematic:4}[s.diskStyle]);
  const patternHeader=`uniform int uGranular;
uniform sampler2D RibbonNoise;
#define BH_REFERENCE_RIBBONS (uGranular==2)
uniform float uBandDetail,uDustScale,uDustFill,uFilament,uSpiral;
uniform float uOrbitPhase,uRadialPhase,uClassicRadialPhase,uShearLocal,uShearParity,uDustDriftA,uDustDriftB;
#define BH_SHEAR_CLOCK (uShearLocal+8.0*uShearParity)
#define BH_CLASSIC_RADIAL_PHASE uClassicRadialPhase
vec2 worldDiskPattern(float radius,float angle);
vec2 bhDiskPattern(float radius,float angle);
vec2 ribbonPattern(float radius,float angle);
vec2 lensedDustPattern(float radius,float angle);
vec2 cinematicPattern(float radius,float angle);
float lensedDustVolume(vec3 q);
vec2 previewDiskPattern(float r,float a){
 if(uGranular==3)return lensedDustPattern(r,a);
 if(uGranular==2)return ribbonPattern(r,a);
 if(uGranular==4)return cinematicPattern(r,a);
 return uGranular==1?worldDiskPattern(r,a):bhDiskPattern(r,a);
}
#define BH_PATTERN previewDiskPattern
#define BH_DISK_DENSITY(pattern) (uGranular==4?.12+pattern.x*1.7:(uGranular==3?lensedDustVolume(q)*3.0:(uGranular==2?pattern.x*3.0:(.3+.5*pattern.x+.2*pattern.y))))
#define BH_SURFACE_DISK (uGranular==2||uGranular==4)
#define BH_CINEMATIC_DISK (uGranular==4)
#define BH_DUST_STYLE (uGranular==3)
#define BH_CAPTURED_FOREGROUND_SCALE (uGranular==1?.22:1.0)
#define BH_SURFACE_CRITICAL_MIN_RADIUS 0.0
`;
  const header=patternHeader+`uniform float HaloStrength,HaloRing,HaloDisk,HaloAura;
uniform float JetStrength,JetLength,JetWidth;
uniform vec3 uJetBaseColor,uJetTipColor;
#define BH_TRACE_BOUND max(uOuter*1.5,JetStrength>0.0001?JetLength+uCore+.5:0.0)
uniform float uHaloRadius,uHaloWidth,uHaloSharpness,uHaloGradientMix;
uniform float uHaloDiskGradientMix,uHaloAuraGradientMix;
uniform vec3 uHaloGradientStart,uHaloGradientEnd;
uniform vec3 uHaloDiskInnerColor,uHaloDiskOuterColor,uHaloAuraInnerColor,uHaloAuraOuterColor;
vec3 haloEmission(vec3 q);
vec3 jetEmission(vec3 q);
#define BH_VOLUME_SAMPLE(q0,q1,ds,result) { if(HaloStrength>0.0001||JetStrength>0.0001){ vec3 bhHaloA=(q0),bhHaloB=(q1),bhHaloM=.5*(bhHaloA+bhHaloB); vec3 bhHaloIntegral=(haloEmission(bhHaloA)+jetEmission(bhHaloA)+4.0*(haloEmission(bhHaloM)+jetEmission(bhHaloM))+haloEmission(bhHaloB)+jetEmission(bhHaloB))*(ds/6.0); result.light+=result.transmission*bhHaloIntegral; } }
#define BH_DISK_EMISSION(pattern) (uGranular==4?(.2+.6*pattern.y):(uGranular==1?((.25+.75*pow(pattern.x,1.3))*(.65+.35*pattern.y)):1.0))
`;
  function particles(gl,kernel,linear){
    const compile=(type,src)=>{const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
    const version='#version 300 es\nprecision highp float;precision highp int;\n';
    const vs=compile(gl.VERTEX_SHADER,vertex.replace('#version 150',version));
    const prefix=particleHeader.replace('#version 150',version).replace('#define BH_PATTERN worldDiskPattern',patternHeader);
    const mask=`uniform int uMode;uniform vec2 uResolution;
`;
    const body=particleMain.replace('void main(){',`void main(){
      if(uMode==1){vec2 q=gl_FragCoord.xy/uResolution*2.0-1.0;q.x*=uResolution.x/uResolution.y;
      float w=.24*clamp((.85-q.y)/.23,0.0,1.0);if(!(abs(q.x)<w&&q.y>-.8&&q.y<.85))discard;}
`);
    const fs=compile(gl.FRAGMENT_SHADER,prefix+mask+kernel+structure+styles+body);
    const program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.deleteShader(vs);gl.deleteShader(fs);
    const vao=gl.createVertexArray();gl.bindVertexArray(vao);
    const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
    const data=new Float32Array(4096*6*5),corners=[[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]];
    let j=0;for(let i=0;i<4096;i++)for(const [x,y] of corners){data[j++]=i;data[j++]=x;data[j++]=y;data[j++]=(x+1)/2;data[j++]=(y+1)/2;}
    gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);
    for(const [name,size,offset] of [['Position',3,0],['UV0',2,12]]){const loc=gl.getAttribLocation(program,name);if(loc>=0){gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,20,offset);}}
    gl.bindVertexArray(null);
    const locations={},L=n=>locations[n]??(locations[n]=gl.getUniformLocation(program,n));
    return (s,t,width,height)=>{
      if(!s.particles||s.particleCount===0)return;
      gl.useProgram(program);gl.bindVertexArray(vao);
      const sy=Math.sin(s.yaw),cy=Math.cos(s.yaw),sp=Math.sin(s.pitch),cp=Math.cos(s.pitch);
      const origin=[s.viewDistance*sy*cp,s.viewDistance*sp,s.viewDistance*cy*cp],right=[cy,0,-sy],up=[-sy*sp,cp,-cy*sp],forward=origin.map(v=>-v/s.viewDistance);
      const f=1/.47,aspect=width/height,near=.01,far=100,A=(far+near)/(far-near),B=-2*far*near/(far-near);
      const m=new Float32Array(16);for(let c=0;c<3;c++){m[c*4]=right[c]*f/aspect;m[c*4+1]=up[c]*f;m[c*4+2]=forward[c]*A;m[c*4+3]=forward[c];}m[14]=B;
      gl.uniformMatrix4fv(L('ViewProjection'),false,m);
      gl.uniform3fv(L('CenterRelative'),origin.map(v=>-v));gl.uniform3fv(L('CameraRight'),right);gl.uniform3fv(L('CameraUp'),up);
      for(const [n,v] of Object.entries({WorldScale:1,uTime:t,uCore:s.core,uInner:s.inner,uOuter:s.outer,uThickness:s.thickness,uTilt:s.tilt,uBend:s.bend,uDensity:s.density,uSpeed:s.speed,uInfall:s.infallSpeed,uSeed:s.seed,ParticleSpeed:s.particleSpeed,ParticleSize:s.particleSize,ParticleOuter:s.particleOuter,ParticleBrightness:s.particleBrightness}))gl.uniform1f(L(n),v);
      for(const [n,v] of Object.entries({uHot:s.hot,uCool:s.cool,uCoreColor:s.coreColor,uCoreEdgeColor:s.coreEdgeColor,uDiskMid:s.diskMid,ParticleCold:s.particleCold,ParticleMid:s.particleMid,ParticleHot:s.particleHot}))gl.uniform3fv(L(n),linear(v));
      gl.uniform1f(L('ParticleGradientPosition'),s.particleGradientPosition);
      gl.uniform1f(L('uDiskGradientPosition'),s.diskGradientPosition);
      gl.uniform1f(L('uCoreGradientStrength'),s.coreGradientStrength);
      gl.uniform1i(L('uSteps'),s.steps);gl.uniform1i(L('ParticleDebug'),0);gl.uniform1i(L('uGranular'),styleIndex(s));
      gl.uniform1f(L('uBandDetail'),s.bandDetail);gl.uniform1f(L('uDustScale'),s.dustScale);gl.uniform1f(L('uDustFill'),s.dustFill);
      gl.uniform1f(L('uFilament'),s.filament);gl.uniform1f(L('uSpiral'),s.spiral);gl.uniform1f(L('uBeaming'),s.beaming);
      const phase=(v,n)=>((v%n)+n)%n,clock=t*Math.abs(s.speed),tau=Math.PI*2;
      for(const [n,v] of Object.entries({uOrbitPhase:phase(t*s.speed,tau),uRadialPhase:phase(t*s.infallSpeed,128),uClassicRadialPhase:phase(t*s.infallSpeed,tau),uShearLocal:phase(clock,8),uShearParity:Math.floor(clock/8)%2,uDustDriftA:phase(t*s.speed*.35,12),uDustDriftB:phase(-t*s.speed*.25,12)}))gl.uniform1f(L(n),v);
      gl.uniform1i(L('uMode'),s.mode==='blade'?1:0);gl.uniform2f(L('uResolution'),width,height);
      gl.enable(gl.BLEND);gl.blendFuncSeparate(gl.ONE,gl.ONE,gl.ZERO,gl.ONE);
      gl.drawArrays(gl.TRIANGLES,0,s.particleCount*6);
      gl.disable(gl.BLEND);gl.bindVertexArray(null);
    };
  }
  return {header,functions:structure+styles+halo,particles,styleIndex};
})();
