const {chromium}=require('playwright');
const fs=require('fs'),path=require('path');
const explicitLod=process.argv.includes('--explicit-lod');
const highpSampler=process.argv.includes('--highp-sampler');
const offscreen=process.argv.includes('--offscreen');
const manualMaterial=process.argv.includes('--manual-material');
const out=path.resolve(__dirname,'../evidence/vortex/'+(process.env.VORTEX_DIAG_CASE||'replay-diagnosis')+(explicitLod?'-lod':''));fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--use-angle=d3d11']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  if(offscreen)await page.addInitScript(()=>{
   const original=WebGL2RenderingContext.prototype.drawArrays,targets=new WeakMap();
   WebGL2RenderingContext.prototype.drawArrays=function(...args){
    if(this.canvas.id!=='viewport')return original.apply(this,args);
    const g=this,w=g.canvas.width,h=g.canvas.height;let target=targets.get(g);
    if(!target||target.w!==w||target.h!==h){
     if(target){g.deleteFramebuffer(target.fb);g.deleteTexture(target.tex);}
     const old=g.getParameter(g.TEXTURE_BINDING_2D),tex=g.createTexture();g.bindTexture(g.TEXTURE_2D,tex);g.texStorage2D(g.TEXTURE_2D,1,g.RGBA8,w,h);g.bindTexture(g.TEXTURE_2D,old);
     const fb=g.createFramebuffer();g.bindFramebuffer(g.FRAMEBUFFER,fb);g.framebufferTexture2D(g.FRAMEBUFFER,g.COLOR_ATTACHMENT0,g.TEXTURE_2D,tex,0);target={w,h,tex,fb};targets.set(g,target);
    }
    g.bindFramebuffer(g.FRAMEBUFFER,target.fb);original.apply(g,args);
    g.bindFramebuffer(g.READ_FRAMEBUFFER,target.fb);g.bindFramebuffer(g.DRAW_FRAMEBUFFER,null);g.blitFramebuffer(0,0,w,h,0,0,w,h,g.COLOR_BUFFER_BIT,g.NEAREST);g.bindFramebuffer(g.FRAMEBUFFER,null);
   };
  });
  if(explicitLod||highpSampler||manualMaterial)await page.route('**/vortex/*',async route=>{
   if(!/\/(preview\.js|volume-core\.glsl)$/.test(route.request().url()))return route.continue();
   const response=await route.fetch();let body=await response.text();
   if(explicitLod){body=body.replace(/texture\(VortexNoiseTex,([^;]+?)\)\.([rgba])/g,'textureLod(VortexNoiseTex,$1,0.0).$2');body=body.replace(/texture\(VortexNoiseTex,([^;]+?)\);/g,'textureLod(VortexNoiseTex,$1,0.0);');}
   if(highpSampler)body=body.replace('uniform sampler3D VortexNoiseTex;','uniform highp sampler3D VortexNoiseTex;');
   if(manualMaterial){
    if(route.request().url().endsWith('volume-core.glsl'))body=body.replace('uniform highp sampler3D VortexNoiseTex;','uniform highp sampler3D VortexNoiseTex;\n'+String.raw`
vec4 veMaterialNoise(vec3 p){
 vec3 c=fract(p)*64.0-.5;ivec3 i=ivec3(floor(c));vec3 f=fract(c);ivec3 mask=ivec3(63);
 vec4 a=mix(texelFetch(VortexNoiseTex,(i+ivec3(0,0,0))&mask,0),texelFetch(VortexNoiseTex,(i+ivec3(1,0,0))&mask,0),f.x);
 vec4 b=mix(texelFetch(VortexNoiseTex,(i+ivec3(0,1,0))&mask,0),texelFetch(VortexNoiseTex,(i+ivec3(1,1,0))&mask,0),f.x);
 vec4 d=mix(texelFetch(VortexNoiseTex,(i+ivec3(0,0,1))&mask,0),texelFetch(VortexNoiseTex,(i+ivec3(1,0,1))&mask,0),f.x);
 vec4 e=mix(texelFetch(VortexNoiseTex,(i+ivec3(0,1,1))&mask,0),texelFetch(VortexNoiseTex,(i+ivec3(1,1,1))&mask,0),f.x);
 return mix(mix(a,b,f.y),mix(d,e,f.y),f.z);
}
`);
    else{
     const token='textureLod(VortexNoiseTex,';let start=body.indexOf(token);
     while(start>=0){let cursor=start+token.length,depth=1;for(;cursor<body.length&&depth>0;cursor++){if(body[cursor]==='(')depth++;if(body[cursor]===')')depth--;}
      const coordinate=body.slice(start+token.length,cursor-1).replace(/,\s*0\.0\s*$/,'');body=body.slice(0,start)+'veMaterialNoise('+coordinate+')'+body.slice(cursor);start=body.indexOf(token,start+16);
     }
    }
   }
   await route.fulfill({response,body});
  });
  await page.goto('http://127.0.0.1:8774/vortex/',{timeout:180000});await page.evaluate(()=>window.volumeLab.ready);await page.evaluate(()=>window.volumeLab.pause(true));
  const save=async(name)=>{
   const state=await page.evaluate(()=>{
    const gl=document.querySelector('canvas').getContext('webgl2'),program=gl.getParameter(gl.CURRENT_PROGRAM),u={};
    for(let i=0;i<gl.getProgramParameter(program,gl.ACTIVE_UNIFORMS);i++){
     const a=gl.getActiveUniform(program,i);
     if(a.size>1){
      for(let j=0;j<a.size;j++){const name=a.name.replace('[0]','['+j+']'),v=gl.getUniform(program,gl.getUniformLocation(program,name));u[name]=ArrayBuffer.isView(v)?Array.from(v):v;}
     }else{const v=gl.getUniform(program,gl.getUniformLocation(program,a.name));u[a.name]=ArrayBuffer.isView(v)?Array.from(v):v;}
    }
    return {work:window.volumeLab.getWork(),uniforms:u,dither:gl.isEnabled(gl.DITHER),state:window.volumeLab.getState()};
   });
   const capture=await page.evaluate(()=>{
    const png=window.volumeLab.capture(),canvas=document.querySelector('canvas'),gl=canvas.getContext('webgl2');
    const pixels=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
    let binary='';for(let i=0;i<pixels.length;i+=65536)binary+=String.fromCharCode(...pixels.subarray(i,i+65536));
    return {png,raw:btoa(binary)};
   });
   fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(capture.png.split(',')[1],'base64'));
   fs.writeFileSync(path.join(out,name+'.rgba'),Buffer.from(capture.raw,'base64'));return state;
  };
  await page.evaluate(()=>window.volumeLab.setMode(2));await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
  const report={first:await save('first'),second:await save('second')};
  await page.evaluate(()=>window.volumeLab.setView('liquid'));await page.evaluate(()=>window.volumeLab.render(20));await save('near');
  await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
  report.reset=await save('reset');report.resetAgain=await save('reset-again');
  await page.evaluate(()=>document.querySelector('canvas').getContext('webgl2').disable(0x0BD0));await page.evaluate(()=>window.volumeLab.render(20));
  report.noDither=await save('no-dither');
  await page.evaluate(()=>window.volumeLab.setView('liquid'));await page.evaluate(()=>window.volumeLab.render(20));
  await page.evaluate(()=>window.volumeLab.setView('outside'));await page.evaluate(()=>window.volumeLab.render(20));
  report.noDitherReset=await save('no-dither-reset');
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(out);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
