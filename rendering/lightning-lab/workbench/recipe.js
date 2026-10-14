const defaultRecipe={
 schema:1,mode:'orbit',seed:1337,strands:16,samples:48,orbitAnimation:'grow',orbitDirection:1,
 objectRadius:1.35,orbitRadius:1.52,orbitHeight:0,rodLength:8.5,rodRadius:.15,emitterSpread:.10,orbitWobble:.28,orbitFlow:1.8,orbitNoiseFrequency:.75,
 arcLength:2.2,lengthRandomness:.65,pulseInterval:6,throttle:.25,orbitExtendSpeed:20,
 currentWidth:.014,glowWidth:.07,coreGain:3.8,glowGain:2.4,
 strikeHeight:18,strikeWidth:.055,strikeDisplacement:1.5,strikeRoughness:.53,
 branchProbability:.16,branchDecay:.5,branchLength:.3,branchAngle:.72,branchJitter:.18,
 progressionTicks:1.2,durationTicks:14,restrikeCount:2,flicker:.22,
 strikeGenerations:7,maxBranchDepth:2,maxSegments:1200,branchLimit:64,rainCount:5,rainSpread:12,rainDepthSpread:4,rainStagger:1.2,rainMaxBranchDepth:1,rainMaxBranches:1,rainBranchProbability:.04,rainStyle:'spread',impactEnabled:true,impactRadius:1.25,impactDuration:12,impactRays:9,impactSparks:14,gradientEnabled:false,coreColor:'#ffffff',glowColor:'#87cfff',gradientStart:'#ffffff',gradientEnd:'#ffffff'
};
const presets={
 spektr:{...defaultRecipe},
 orbit:{...defaultRecipe},
 source:{...defaultRecipe,orbitAnimation:'spektr',strands:20,samples:29,rodLength:20,arcLength:16,lengthRandomness:.9,pulseInterval:6,throttle:.1,orbitWobble:1.2,orbitNoiseFrequency:.1,orbitFlow:.1,currentWidth:.035,glowWidth:.13},
 shortSource:{...defaultRecipe,orbitAnimation:'spektr',strands:20,samples:29,rodLength:20,arcLength:2,lengthRandomness:0,pulseInterval:6,throttle:.573,orbitWobble:1.2,orbitNoiseFrequency:.1,orbitFlow:.1,currentWidth:.035,glowWidth:.13},
 ring:{...defaultRecipe,orbitAnimation:'ring',strands:12,arcLength:2.8,orbitRadius:2.5,objectRadius:1,orbitWobble:.38,orbitNoiseFrequency:1.2,orbitFlow:.35,pulseInterval:8,throttle:.4,gradientEnabled:true,gradientStart:'#ffffff',gradientEnd:'#6ec8ff'},
 rain:{...defaultRecipe,mode:'rain',branchLimit:1,gradientEnabled:true,gradientStart:'#fff1b8',gradientEnd:'#aa88ff',glowColor:'#ffffff'},
 cluster:{...defaultRecipe,mode:'rain',rainStyle:'cluster',rainCount:9,rainSpread:3,rainDepthSpread:2.4,rainStagger:.22,rainMaxBranchDepth:0,rainMaxBranches:0,rainBranchProbability:0,strikeWidth:.065,glowWidth:.23,impactRadius:1.6,impactDuration:18,impactRays:10,impactSparks:18,gradientEnabled:true,gradientStart:'#fff1b8',gradientEnd:'#b49bff',glowColor:'#dcecff'},
 dense:{...defaultRecipe,strands:24,throttle:.9,arcLength:1.7,orbitWobble:.23,pulseInterval:2.8},
 strike:{...defaultRecipe,mode:'strike',seed:90421},
 tribulation:{...defaultRecipe,mode:'strike',seed:18234,strikeWidth:.085,strikeDisplacement:2.8,branchProbability:.35,maxBranchDepth:3,glowWidth:.32,coreGain:5,glowGain:3.5,glowColor:'#a9bcff'}
};
const recipeRanges={
 schema:[1,1,true],seed:[-2147483648,2147483647,true],strands:[1,32,true],samples:[16,256,true],orbitDirection:[-1,1,true],orbitNoiseFrequency:[.05,8],
 objectRadius:[.1,8],orbitRadius:[.1,12],orbitHeight:[-8,8],rodLength:[.5,32],rodRadius:[.01,2],emitterSpread:[0,2],orbitWobble:[0,2],orbitFlow:[-12,12],
 arcLength:[.1,32],lengthRandomness:[0,.95],pulseInterval:[.5,20],throttle:[0,1],orbitExtendSpeed:[.1,120],
 currentWidth:[.001,.4],glowWidth:[.01,2],coreGain:[.1,16],glowGain:[0,12],
 strikeHeight:[2,128],strikeWidth:[.004,.4],strikeDisplacement:[.05,12],strikeRoughness:[.35,.85],
 branchProbability:[0,1],branchDecay:[.1,.95],branchLength:[.05,1],branchAngle:[.1,1.5],branchJitter:[0,2],
 progressionTicks:[.1,8],durationTicks:[1,40],restrikeCount:[0,4,true],flicker:[0,1],
 strikeGenerations:[3,9,true],maxBranchDepth:[0,4,true],maxSegments:[64,10000,true],branchLimit:[0,128,true],rainCount:[1,24,true],rainSpread:[1,64],rainDepthSpread:[0,32],rainStagger:[0,12],rainMaxBranchDepth:[0,1,true],rainMaxBranches:[0,2,true],rainBranchProbability:[0,.12],impactRadius:[.1,6],impactDuration:[2,40],impactRays:[1,24,true],impactSparks:[0,32,true]
};
function validateRecipe(input){
 if(!input||typeof input!=='object'||Array.isArray(input))throw Error('JSON 必须是参数对象');
 for(const key of Object.keys(input))if(!(key in defaultRecipe))throw Error('未知参数：'+key);
 const r={...defaultRecipe,...input};if(!['orbit','strike','rain'].includes(r.mode))throw Error('mode 必须为 orbit、strike 或 rain');
 if(typeof r.gradientEnabled!=='boolean')throw Error('gradientEnabled 必须为布尔值');
 if(typeof r.impactEnabled!=='boolean'||!['spread','cluster'].includes(r.rainStyle))throw Error('击地开关或落雷样式无效');
 if(!['grow','spektr','ring'].includes(r.orbitAnimation)||![1,-1].includes(r.orbitDirection))throw Error('电弧动画或方向无效');
 for(const [key,[lo,hi,integer]] of Object.entries(recipeRanges))
  if(typeof r[key]!=='number'||!Number.isFinite(r[key])||r[key]<lo||r[key]>hi||(integer&&!Number.isInteger(r[key])))throw Error(key+': '+lo+'..'+hi+(integer?'（整数）':''));
 if(r.durationTicks<r.progressionTicks+2)throw Error('闪光总时长必须比先行时间至少长 2 tick');
 for(const key of ['coreColor','glowColor','gradientStart','gradientEnd'])if(!/^#[\da-f]{6}$/i.test(r[key]))throw Error(key+' 必须为 #RRGGBB');
 return r;
}
if(typeof module!=='undefined')module.exports={defaultRecipe,presets,validateRecipe};
