#!/usr/bin/env node
const fs=require('fs'),path=require('path');
const args=process.argv.slice(2),get=k=>{const i=args.indexOf(k);return i<0?null:args[i+1]};
const out=get('--out'),mode=get('--mode')||'orbit',recipePath=get('--recipe');
if(!out){console.error('usage: node author.cjs --out <directory> [--mode orbit|strike|rain] [--recipe file]');process.exit(2)}
if(!['orbit','strike','rain'].includes(mode))throw new Error('mode must be orbit, strike or rain');
if(fs.existsSync(out)&&fs.readdirSync(out).length)throw new Error('refusing to overwrite non-empty output');
fs.mkdirSync(out,{recursive:true});
const source=path.resolve(__dirname,'..','assets');
fs.cpSync(path.join(source,'workbench'),path.join(out,'workbench'),{recursive:true});
fs.cpSync(path.join(source,'forge-1.20.1'),path.join(out,'forge-1.20.1'),{recursive:true,filter:p=>!path.relative(path.join(source,'forge-1.20.1'),p).split(path.sep).some(part=>['build','.gradle','run'].includes(part))});
const validate=require(path.join(source,'workbench','recipe.js')).validateRecipe;
let input=recipePath?JSON.parse(fs.readFileSync(recipePath,'utf8')):JSON.parse(fs.readFileSync(path.join(source,'workbench','recipe.json'),'utf8'));
if(recipePath){
 if(input&&typeof input==='object'&&!Array.isArray(input)){
  if(Object.hasOwn(input,'values')){if(input.values&&typeof input.values==='object'&&!Array.isArray(input.values)&&!Object.hasOwn(input.values,'mode'))input={...input,values:{...input.values,mode}};}
  else if(!Object.hasOwn(input,'mode'))input={...input,mode};
 }
}else input={...input,mode};
const base=validate(input);
fs.mkdirSync(path.join(out,'config','lightninglab'),{recursive:true});fs.writeFileSync(path.join(out,'config','lightninglab','recipe.json'),JSON.stringify(base,null,2));
console.log(`created ${out} (${base.mode})`);
