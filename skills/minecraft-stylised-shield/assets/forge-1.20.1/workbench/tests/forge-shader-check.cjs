// Syntax/link/interface check through GLES 3. Desktop Minecraft must still be
// checked separately: this does not exercise ShaderInstance or world rendering.
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve(__dirname,'../../src/main/resources/assets/shieldfx/shaders/core');
 const adapt=s=>s.trimStart().replace('#version 150','#version 300 es\nprecision highp float;');
 const source={vertex:adapt(fs.readFileSync(path.join(root,'water.vsh'),'utf8')),fragment:adapt(fs.readFileSync(path.join(root,'water.fsh'),'utf8')),json:JSON.parse(fs.readFileSync(path.join(root,'water.json'),'utf8'))};
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{const page=await browser.newPage();const result=await page.evaluate(source=>{
  const gl=document.createElement('canvas').getContext('webgl2');if(!gl)return {error:'GLES3 unavailable'};
  const program=gl.createProgram();for(const [type,text] of [[gl.VERTEX_SHADER,source.vertex],[gl.FRAGMENT_SHADER,source.fragment]]){
   const shader=gl.createShader(type);gl.shaderSource(shader,text);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))return {error:gl.getShaderInfoLog(shader)};gl.attachShader(program,shader);
  }
  gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))return {error:gl.getProgramInfoLog(program)};
  const missing=[];for(let i=0;i<gl.getProgramParameter(program,gl.ACTIVE_UNIFORMS);i++){
   const uniform=gl.getActiveUniform(program,i),record=source.json.uniforms.find(r=>r.name===uniform.name);
   const type={ [gl.FLOAT]:['float',1],[gl.FLOAT_VEC3]:['float',3],[gl.FLOAT_MAT4]:['matrix4x4',16]}[uniform.type];
   if(!record||!type||record.type!==type[0]||record.count!==type[1])missing.push(uniform.name);
  }
  return {linked:true,active:gl.getProgramParameter(program,gl.ACTIVE_UNIFORMS),missing};
 },source);assert.equal(result.error,undefined);assert.equal(result.linked,true);assert.deepEqual(result.missing,[]);console.log(`PASS: Forge material GLSL syntax/link and JSON uniform interface (${result.active} active uniforms); GLES3 surrogate only.`);
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
