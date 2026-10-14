const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const folder = path.resolve(process.argv[2] || path.join(root, 'run/lightning-lab/game-probes'));
const names = ['supplied-strike-peak','supplied-strike-replay','supplied-strike-half-leader','supplied-strike-tail','supplied-strike-ended','source','short-source','grow','ring','cluster-before-impact','cluster-impact','cluster-impact-tail','cluster-ended','spread-rain'];
const frames = {};
let checks=0;
function assert(condition,label){checks++;if(!condition)throw Error(label);}
for(const name of names){
    frames[name]=JSON.parse(fs.readFileSync(path.join(folder,name+'.json'),'utf8'));
    const png=fs.readFileSync(path.join(folder,name+'.png'));
    assert(png.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))&&png.readUInt32BE(16)>0&&png.readUInt32BE(20)>0,'Invalid GPU capture: '+name);
}
const frame=name=>frames[name];
const supplied={gradientRatio:50,...JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/assets/vfxworkbench/lightning/recipe.json'),'utf8'))};
const normalize=value=>JSON.stringify(value,Object.keys(value).sort());
for(const name of names.slice(0,5))assert(normalize({gradientRatio:50,...frame(name).recipe})===normalize(supplied),'Supplied recipe changed during GPU capture: '+name);
const peak=frame('supplied-strike-peak'),replay=frame('supplied-strike-replay');
assert(peak.generatedMainSegments<=supplied.maxSegments&&peak.visibleMainSegments>0,'User strike did not render within its segment budget');
assert(peak.generatedMainSegments===replay.generatedMainSegments&&peak.visibleMainSegments===replay.visibleMainSegments,'Replayed user strike changed geometry counts');
assert(frame('supplied-strike-half-leader').visibleMainSegments>0&&frame('supplied-strike-half-leader').visibleMainSegments<peak.visibleMainSegments,'Leader timing is incorrect');
assert(frame('supplied-strike-tail').visibleMainSegments===peak.visibleMainSegments,'Propagated strike tail is truncated');
assert(frame('supplied-strike-ended').visibleMainSegments===0,'Ended strike leaves visible geometry');
for(const name of ['source','short-source','grow','ring'])assert(frame(name).visibleMainSegments>0,'Orbit preset has no visible paths: '+name);
assert(frame('source').recipe.orbitAnimation==='spektr'&&frame('short-source').recipe.orbitAnimation==='spektr','Source presets do not select their actual animation');
assert(frame('grow').recipe.orbitAnimation==='grow'&&frame('ring').recipe.orbitAnimation==='ring','Independent animation selection failed');
assert(frame('cluster-before-impact').visibleImpactSegments===0,'Impact starts before the leader reaches the floor');
assert(frame('cluster-impact').visibleImpactSegments>0&&frame('cluster-impact').visibleMainSegments>0,'Cluster impact or main bolts did not render');
assert(frame('cluster-impact-tail').visibleMainSegments===0&&frame('cluster-impact-tail').visibleImpactSegments>0,'Independent impact tail was cut off');
assert(frame('cluster-ended').visibleMainSegments===0&&frame('cluster-ended').visibleImpactSegments===0,'Cluster ended stage leaves residue');
assert(frame('spread-rain').visibleMainSegments>0&&frame('spread-rain').visibleImpactSegments>0,'Spread rain did not render both channels');
const report={passed:true,checks,sourceFolder:folder,stageCount:names.length,note:'Checks actual client recipe and segment counts plus PNG capture availability. Visual inspection and shader compile logs remain separate evidence.',frames:Object.fromEntries(names.map(name=>[name,{tick:frame(name).elapsedTicks,generatedMain:frame(name).generatedMainSegments,visibleMain:frame(name).visibleMainSegments,visibleImpact:frame(name).visibleImpactSegments}]))};
const output=path.resolve(process.argv[3] || path.join(root,'evidence/lightning-skill-reimport-20261005/game-probe-check.json'));fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(`PASS: ${checks} client recipe/timing/budget/impact and GPU capture availability checks across ${names.length} stages. Visual QA is separate.`);
