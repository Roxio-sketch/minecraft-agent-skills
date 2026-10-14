// Run the shipped world pattern on the GPU and track its sampled features.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const args=process.argv.slice(2),arg=(n,d)=>{const i=args.indexOf(n);return i<0?d:args[i+1];};
const out=path.resolve(arg('--output','disk-motion-evidence'));
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE),args:['--enable-webgl']});
 try{
  const page=await browser.newPage();
  const source=fs.readFileSync(path.join(__dirname,'disk_structure.glsl'),'utf8');
  const result=await page.evaluate(source=>{
   const canvas=document.createElement('canvas');canvas.width=512;canvas.height=1;
   const gl=canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});if(!gl)throw Error('No WebGL2');gl.disable(gl.DITHER);
   const compile=(type,source)=>{const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
   const program=gl.createProgram();
   gl.attachShader(program,compile(gl.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);}'));
   gl.attachShader(program,compile(gl.FRAGMENT_SHADER,`#version 300 es
precision highp float;precision highp int;
uniform float uTime,uSpeed,uInfall,uSeed,uRadius,uAngleOffset;
uniform int uAxis;
${source}
out vec4 color;
void main(){float x=(gl_FragCoord.x-.5)/512.0;
 float radius=uAxis==0?1.0+2.0*x:uRadius;
 float angle=(uAxis==0?.37:-3.14159265359+6.28318530718*x)+uAngleOffset;
 color=vec4(worldDiskPattern(radius,angle),0,1);}`));
   gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
   gl.useProgram(program);gl.viewport(0,0,512,1);const loc=n=>gl.getUniformLocation(program,n);
   const render=({axis=0,time=0,speed=0,infall=.22,radius=1.5,offset=0}={})=>{
    gl.uniform1i(loc('uAxis'),axis);
    for(const [n,v] of Object.entries({uTime:time,uSpeed:speed,uInfall:infall,uSeed:17,uRadius:radius,uAngleOffset:offset}))gl.uniform1f(loc(n),v);
    gl.drawArrays(gl.TRIANGLES,0,3);const p=new Uint8Array(512*4);gl.readPixels(0,0,512,1,gl.RGBA,gl.UNSIGNED_BYTE,p);return p;
   };
   const error=(a,b,lag=0)=>{let sum=0;for(let i=64;i<448;i++)for(let c=0;c<2;c++)sum+=Math.abs(a[i*4+c]-b[(i+lag)*4+c]);return sum/768;};
   const bestLag=(a,b)=>{const c=[];for(let lag=-60;lag<=60;lag++)c.push({lag,error:error(a,b,lag)});return c.sort((a,b)=>a.error-b.error)[0];};
   const a=render(),b=render({time:.125/.22});
   const radial={best:bestLag(b,a),expectedLag:32,correct:error(b,a,32),opposite:error(b,a,-32),stationary:error(b,a)};
   const speed=.8,seconds=.35;
   const angular=[1.1,1.6,2.5].map(radius=>{
    const initial=render({axis:1,infall:0,speed,radius});
    const later=render({axis:1,infall:0,speed,radius,time:seconds});
    return {radius,expectedLag:-seconds*speed*1.2/Math.pow(radius,1.5)/(2*Math.PI)*512,best:bestLag(later,initial)};
   });
   const exactTime=32*2*Math.PI/512*Math.pow(1.5,1.5)/(1.2*speed);
   const exact=bestLag(render({axis:1,infall:0,speed,time:exactTime}),render({axis:1,infall:0,speed}));
   const staticError=error(render({infall:0,time:10}),render({infall:0}));
   const seamError=error(render({axis:1,offset:2*Math.PI}),render({axis:1}));
   const period=128/.22,wrapError=error(render({time:period-.001}),render({time:period+.001}));
   const extension=gl.getExtension('WEBGL_debug_renderer_info'),glError=gl.getError();
   return {renderer:extension?gl.getParameter(extension.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),radial,angular,exact,staticError,seamError,wrapError,glError,
    checks:[
     {name:'positive_inflow_moves_features_inward',passed:radial.best.lag===32&&radial.correct<.15&&radial.opposite>5&&radial.stationary>5},
     {name:'angular_rotation_without_inflow',passed:exact.lag===-32&&exact.error<.15},
     {name:'differential_rotation_matches_radius',passed:angular.every(a=>Math.abs(a.expectedLag-a.best.lag)<1)&&Math.abs(angular[0].best.lag)>Math.abs(angular[2].best.lag)},
     {name:'zero_speeds_freeze_pattern',passed:staticError===0},
     {name:'angular_seam_is_periodic',passed:seamError<.15},
     {name:'radial_wrap_is_continuous',passed:wrapError<1},
     {name:'gpu_no_error',passed:glError===0}]};
  },source);
  result.status=result.checks.every(c=>c.passed)?'passed':'failed';
  result.scope='Actual disk_structure.glsl GPU sampling; independent angular and radial transport';
  fs.writeFileSync(path.join(out,'disk-motion.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));if(result.status!=='passed')process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
