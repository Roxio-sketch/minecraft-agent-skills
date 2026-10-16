import assert from 'node:assert/strict';
import {geometry} from '../shader/geometry.js';
import {defaults,validateRecipe} from '../shader/parameters.js';
const a=geometry(defaults),b=geometry(defaults);
assert.deepEqual(a,b);assert.notDeepEqual(a,geometry({...defaults,u_seed:92}));
for(const [k,v]of Object.entries(a))assert.ok([...v].every(Number.isFinite),k);
assert.ok(Math.abs(a.spine[1]+defaults.u_length*.5)<1e-6);assert.ok(Math.abs(a.spine[129]-defaults.u_length*.5)<1e-6);
assert.deepEqual(a,geometry({...defaults,u_progress:.2,u_shrink:.5,u_time:42}));
for(let i=0;i<8;i++){const y=a.branchA[i*4+1],pos=(y/defaults.u_length+.5)*32,j=Math.floor(pos),x=a.spine[j*4]*(1-(pos-j))+a.spine[(j+1)*4]*(pos-j);assert.ok(Math.abs(x-a.branchA[i*4])<1e-5);}
for(const bad of [{u_length:0},{u_detail:Infinity},{u_seed:'12'},{u_insideColor:'#z00000'}])assert.throws(()=>validateRecipe(bad));
assert.deepEqual(validateRecipe({format:'space-rift/2',parameters:defaults}),defaults);
console.log('PASS: deterministic midpoint geometry, seed variation, stable growth, 8 connected roots, strict atomic recipe validation.');
