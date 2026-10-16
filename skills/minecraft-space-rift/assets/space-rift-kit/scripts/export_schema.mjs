import {controls,defaults,presets,horizonPresets} from '../shader/parameters.js';
import {writeFileSync,mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const root=new URL('../',import.meta.url);
const ranges=Object.fromEntries(controls.map(c=>[c[0],[c[2],c[3],c[4]]]));
Object.assign(ranges,{u_mode:[0,1,1],u_quality:[0,2,1],u_debug:[0,5,1]});
writeFileSync(new URL('forge/src/main/resources/assets/spacerift/parameters.json',root),JSON.stringify({defaults,ranges},null,2));
mkdirSync(new URL('forge/src/main/resources/assets/spacerift/presets/',root),{recursive:true});
for(const[k,v]of Object.entries({...presets,...horizonPresets})){
 const text=JSON.stringify({format:'space-rift/2',parameters:v},null,2);
 writeFileSync(new URL('presets/'+k+'.json',root),text);
 writeFileSync(new URL('forge/src/main/resources/assets/spacerift/presets/'+k+'.json',root),text);
}
console.log('Exported shared schema and '+(Object.keys(presets).length+Object.keys(horizonPresets).length)+' presets.');
