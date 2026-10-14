const fs=require('fs'),path=require('path'),{chromium}=require('playwright');
(async()=>{const browser=await chromium.launch({headless:true,executablePath:"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"});try{
 const page=await browser.newPage();const base=path.resolve(__dirname,'../src/main/resources/assets/vfxworkbench/shaders/core');
 const sources=['world','particles'].map(n=>({name:n,vs:fs.readFileSync(path.join(base,`bh_${n}.vsh`),'utf8'),fs:fs.readFileSync(path.join(base,`bh_${n}.fsh`),'utf8')}));
 const results=await page.evaluate(sources=>{const gl=document.createElement('canvas').getContext('webgl2');if(!gl)throw Error('WebGL2 unavailable');return sources.map(s=>{
 const compile=(type,src)=>{const shader=gl.createShader(type);gl.shaderSource(shader,src.replace('#version 150','#version 300 es\nprecision highp float;precision highp int;'));gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(s.name+': '+gl.getShaderInfoLog(shader));return shader;};
 const program=gl.createProgram();gl.attachShader(program,compile(gl.VERTEX_SHADER,s.vs));gl.attachShader(program,compile(gl.FRAGMENT_SHADER,s.fs));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(s.name+': '+gl.getProgramInfoLog(program));return {name:s.name,linked:true};});},sources);
 console.log(JSON.stringify({webglInterfaceChecks:results,forgeCompiled:false,minecraftRuntimeVerified:false}));
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
