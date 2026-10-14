// Executes the shipped vertex shader with GPU transform feedback, not a JS trajectory copy.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(n,d)=>{const i=args.indexOf(n);return i<0?d:args[i+1];};
const out=path.resolve(arg('--output','particle-motion-evidence'));
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE),args:['--enable-webgl']});
 try{
  const page=await browser.newPage();
  const source=fs.readFileSync(path.join(__dirname,'particles.vsh'),'utf8');
  const result=await page.evaluate(source=>{
   const gl=document.createElement('canvas').getContext('webgl2');if(!gl)throw Error('No WebGL2');
   const shader=(type,src)=>{const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
   const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,source.replace('#version 150','#version 300 es\nprecision highp float;precision highp int;')));
   gl.attachShader(program,shader(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}'));
   gl.transformFeedbackVaryings(program,['ParticleLocal','ParticleLife','ParticleAlpha','ParticleRadius','ParticlePeriod'],gl.INTERLEAVED_ATTRIBS);gl.linkProgram(program);
   if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
   const vao=gl.createVertexArray();gl.bindVertexArray(vao);
   const input=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,input);const vertices=new Float32Array(640*3);for(let i=0;i<640;i++)vertices[i*3]=i;
   gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);const pos=gl.getAttribLocation(program,'Position');gl.enableVertexAttribArray(pos);gl.vertexAttribPointer(pos,3,gl.FLOAT,false,0,0);
   const loc=n=>gl.getUniformLocation(program,n),f=(n,v)=>gl.uniform1f(loc(n),v);
   f('WorldScale',1.6);f('uTilt',0);f('uCore',.58);f('ParticleSize',.022);f('ParticleOuter',5.4);gl.uniform1i(loc('ParticleDebug'),0);
   gl.uniform3f(loc('CenterRelative'),0,0,14);gl.uniform3f(loc('CameraRight'),1,0,0);gl.uniform3f(loc('CameraUp'),0,1,0);
   gl.uniformMatrix4fv(loc('ViewProjection'),false,new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]));
   const feedback=gl.createTransformFeedback();gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,feedback);
   const output=gl.createBuffer();gl.bindBuffer(gl.TRANSFORM_FEEDBACK_BUFFER,output);gl.bufferData(gl.TRANSFORM_FEEDBACK_BUFFER,640*7*4,gl.DYNAMIC_READ);gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,output);
   const run=(time,speed=1)=>{f('uTime',time);f('ParticleSpeed',speed);gl.enable(gl.RASTERIZER_DISCARD);gl.beginTransformFeedback(gl.POINTS);gl.drawArrays(gl.POINTS,0,640);gl.endTransformFeedback();gl.disable(gl.RASTERIZER_DISCARD);const data=new Float32Array(640*7);gl.getBufferSubData(gl.TRANSFORM_FEEDBACK_BUFFER,0,data);return Array.from({length:640},(_,i)=>Array.from(data.slice(i*7,i*7+7)));};
   const a=run(0),b=run(.2),stillA=run(0,0),stillB=run(10,0);
   const continuous=a.map((p,i)=>({a:p,b:b[i]})).filter(p=>p.b[3]>p.a[3]);
   const inward=continuous.filter(p=>p.b[5]<p.a[5]);
   const rotated=continuous.filter(p=>Math.abs(p.a[0]*p.b[2]-p.a[2]*p.b[0])>.001);
   const seed=a[0][3],period=a[0][6],t=(life)=>((life-seed+1)%1)*period;
   const track=[.001,.10,.30,.60,.85,.95,.985,.999].map(life=>({targetLife:life,values:run(t(life))[0]}));
   const glError=gl.getError(),extension=gl.getExtension('WEBGL_debug_renderer_info');
   return {renderer:extension?gl.getParameter(extension.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),glError,
    checks:[
     {name:'same_particle_moves_inward',passed:continuous.length>600&&inward.length===continuous.length,checked:continuous.length,inward:inward.length},
     {name:'same_particle_rotates',passed:rotated.length===continuous.length,rotated:rotated.length},
     {name:'zero_speed_freezes_positions',passed:JSON.stringify(stillA)===JSON.stringify(stillB)},
     {name:'lifecycle_reaches_core',passed:track.at(-1).values[5]<.58},
     {name:'spawn_and_capture_are_invisible',passed:track[0].values[4]<.005&&track.at(-1).values[4]===0},
     {name:'midlife_visible',passed:track[3].values[4]>.99},
     {name:'radius_monotonically_decreases',passed:track.every((p,i)=>i===0||p.values[5]<track[i-1].values[5])},
     {name:'gpu_no_error',passed:glError===0}],track};
  },source);
  result.status=result.checks.every(c=>c.passed)?'passed':'failed';result.scope='GPU transform feedback of actual particle vertex shader; Forge occlusion verified separately';
  fs.writeFileSync(path.join(out,'motion.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));if(result.status!=='passed')process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
