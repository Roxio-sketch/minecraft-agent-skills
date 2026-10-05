// Links the shipped world/particle shaders under WebGL2; Forge must be checked separately.
const fs=require('node:fs'),path=require('node:path'),{chromium}=require('playwright');
const args=process.argv.slice(2),arg=(name,fallback)=>{const i=args.indexOf(name);return i<0?fallback:args[i+1];};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:arg('--browser',process.env.BROWSER_EXECUTABLE)});
 try{
  const page=await browser.newPage();
  const base=path.resolve(__dirname,'../src/main/resources/assets/blackholelab/shaders/core');
  const sources=['world','particles'].map(name=>({name,vs:fs.readFileSync(path.join(base,name+'.vsh'),'utf8'),fs:fs.readFileSync(path.join(base,name+'.fsh'),'utf8')}));
  const result=await page.evaluate(sources=>{
   const gl=document.createElement('canvas').getContext('webgl2');if(!gl)throw Error('WebGL2 unavailable');
   return sources.map(source=>{
    const compile=(type,text)=>{const shader=gl.createShader(type);gl.shaderSource(shader,text.replace('#version 150','#version 300 es\nprecision highp float;precision highp int;'));gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(source.name+': '+gl.getShaderInfoLog(shader));return shader;};
    const program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,source.vs));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,source.fs));gl.linkProgram(program);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(source.name+': '+gl.getProgramInfoLog(program));
    return {name:source.name,linked:true,activeUniforms:Array.from({length:gl.getProgramParameter(program,gl.ACTIVE_UNIFORMS)},(_,i)=>gl.getActiveUniform(program,i).name)};
   });
  },sources);
  const report={status:'passed',webglInterfaces:result,minecraftRuntimeVerified:false};
  const output=arg('--output');if(output){fs.mkdirSync(path.dirname(path.resolve(output)),{recursive:true});fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');}
  console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
