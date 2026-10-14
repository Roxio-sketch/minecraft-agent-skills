const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),template=path.join(root,'assets','forge-1.20.1');
const {defaults,validate}=require(path.join(template,'workbench','wave-core.js'));
const args=process.argv.slice(2),options={};for(let i=0;i<args.length;i+=2){if(!['--out','--preset','--recipe'].includes(args[i])||!args[i+1])throw Error('Usage: node author.cjs --out NEW_DIRECTORY [--preset rain] [--recipe recipe.json]');options[args[i]]=args[i+1];}
if(!options['--out'])throw Error('--out is required');
const presets={default:{},rain:{radius:.16,strength:.065,rainRate:12,damping:.985,shininess:140},impact:{radius:.8,strength:.4,damping:.996,width:12},shallow:{solver:'history',boundary:'reflect',speed:.8,damping:.995,maxHeight:.12,radius:.35,strength:.08},magic:{color:'#20ddc5',opacity:.65,reflection:1.6,normalStrength:2,strength:.3,speed:.7}};
const name=options['--preset']||'default';if(!Object.hasOwn(presets,name))throw Error('Unknown preset '+name);
const recipe=validate({...defaults,...presets[name],...(options['--recipe']?JSON.parse(fs.readFileSync(options['--recipe'],'utf8')):{})});
const out=path.resolve(options['--out']);if(fs.existsSync(out))throw Error('Output already exists; choose a new directory');
fs.cpSync(template,out,{recursive:true});
const config=path.join(out,'run','config','waterripple');fs.mkdirSync(config,{recursive:true});fs.writeFileSync(path.join(config,'recipe.json'),JSON.stringify(recipe,null,2)+'\n');
fs.writeFileSync(path.join(out,'recipe.json'),JSON.stringify(recipe,null,2)+'\n');
fs.writeFileSync(path.join(out,'workbench','recipe.js'),'window.WATER_RECIPE = '+JSON.stringify(recipe,null,2)+';\n');
console.log('Generated '+out+'\nPreset '+name+'\nOpen workbench/index.html; build using JDK 17 and gradlew build.');
