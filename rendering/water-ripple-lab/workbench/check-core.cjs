const assert=require('node:assert/strict');
const {defaults,validate,WaveField}=require('./wave-core.js');
for(const solver of ['velocity','history'])for(const boundary of ['absorb','reflect']){
 const f=new WaveField({...defaults,solver,boundary,maxHeight:.12});f.drop(25,32,3,.1);
 for(let i=0;i<500;i++){if(i%20===0)f.drop(40,30,4,-.3);f.tick();}
 assert(f.h.every(h=>Number.isFinite(h)&&Math.abs(h)<.12001));
 console.log('PASS bounded',solver,boundary);
}
for(const key of ['speed','width','damping','radius','maxHeight'])assert.throws(()=>validate({...defaults,[key]:Infinity}));
assert.throws(()=>validate({...defaults,grid:256}));assert.throws(()=>validate({...defaults,schema:2}));
assert.deepEqual(validate(JSON.parse(JSON.stringify(defaults))),defaults);
const f=new WaveField({...defaults,solver:'history',boundary:'reflect'});f.drop(24,32,3,.1);for(let i=0;i<25;i++)f.tick();
console.log('PARITY:',f.sample(32,32),f.sample(25,30),f.sample(44,32));
console.log('PASS validation and JSON round-trip');
