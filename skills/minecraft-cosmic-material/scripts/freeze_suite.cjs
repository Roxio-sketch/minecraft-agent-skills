// Retains zero tolerance, and stores the first failing pair with exact inputs.
const fs=require('node:fs'),path=require('node:path');
async function runFreezeSuite(page,runs,evidence){
  const frozen=await page.evaluate(async runs=>{
    const summary={zeroMotion:[],sameState:[],uniforms:[],firstFailure:null};
    const delta=(a,b)=>{let pixels=0,max=0,channels=0;for(let i=0;i<a.length;i+=4){let changed=false;for(let c=0;c<3;c++){const d=Math.abs(a[i+c]-b[i+c]);if(d){changed=true;channels++;max=Math.max(max,d);}}if(changed)pixels++;}return {changedPixels:pixels,maxChannelDifference:max,changedChannels:channels};};
    const png=(rgba,width,height)=>{const c=document.createElement('canvas');c.width=width;c.height=height;const flipped=new Uint8ClampedArray(rgba.length);for(let y=0;y<height;y++)flipped.set(rgba.slice(y*width*4,(y+1)*width*4),(height-1-y)*width*4);c.getContext('2d').putImageData(new ImageData(flipped,width,height),0,0);return c.toDataURL('image/png').split(',')[1];};
    const inspect=(kind,a,b,run,preset,recipes)=>{
      for(let i=0;i<3;i++){
        const d=delta(a[i],b[i]);summary[kind].push({run,preset,canvas:i,...d});
        if(d.changedPixels&&!summary.firstFailure){const sizes=[[800,540],[16,16],[32,32]];const [width,height]=sizes[i];summary.firstFailure={kind,run,preset,canvas:i,width,height,difference:d,recipes,diagnostics:cosmicLab.diagnostics(),beforePng:png(a[i],width,height),afterPng:png(b[i],width,height)};}
      }
    };
    for(let run=0;run<runs;run++){
      const id=cosmicLab.presets[run%cosmicLab.presets.length];cosmicLab.selectPreset(id);
      cosmicLab.render({time:0,parameters:{speed:0,starSpeed:0,twinkle:0},shape:0,view:[0,0],layers:{starsOn:1,cloudsOn:1,dustOn:1}});
      const beforeRecipe=cosmicLab.recipe();const a=[0,1,2].map(i=>cosmicLab.capture(i));
      await new Promise(resolve=>requestAnimationFrame(resolve));
      cosmicLab.render({time:30+run*13});const afterRecipe=cosmicLab.recipe();const b=[0,1,2].map(i=>cosmicLab.capture(i));
      inspect('zeroMotion',a,b,run,id,[beforeRecipe,afterRecipe]);summary.uniforms.push(cosmicLab.diagnostics().materialTime);
      await new Promise(resolve=>requestAnimationFrame(resolve));
      cosmicLab.render({time:30+run*13});const c=[0,1,2].map(i=>cosmicLab.capture(i));inspect('sameState',b,c,run,id,[afterRecipe,cosmicLab.recipe()]);
    }
    return summary;
  },runs);
  if(frozen.firstFailure){const f=frozen.firstFailure;fs.writeFileSync(path.join(evidence,'freeze-before.png'),Buffer.from(f.beforePng,'base64'));fs.writeFileSync(path.join(evidence,'freeze-after.png'),Buffer.from(f.afterPng,'base64'));delete f.beforePng;delete f.afterPng;fs.writeFileSync(path.join(evidence,'freeze-failure.json'),JSON.stringify(f,null,2));}
  return frozen;
}
module.exports={runFreezeSuite};
