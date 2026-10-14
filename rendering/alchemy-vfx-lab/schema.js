export const DEFAULT_RECIPE={schema:'alchemy-vfx/2',fireRadius:1.49,fireHeight:.92,fireStrength:1.25,
 vaporRadius:.22,vaporHeight:1.55,vaporSpread:.36,vaporStrength:.8,flowSpeed:.7,turbulence:1.1,
 filamentWidth:.028,filamentStrength:1.3,orbitRadius:.78,orbitSpeed:.14,coreRadius:.14,coreStrength:1.5,
 burstRadius:1.3,burstStrength:1.1,sigilRadius:2.75,sigilStrength:.26,intensity:1.1,opacity:.88,
 fireCore:'#fff2b4',fireMid:'#ffae54',fireEdge:'#bd3b2c',vaporCore:'#e3ffee',vaporMid:'#79decb',vaporEdge:'#1c5865',coreColor:'#fff4c9',seed:17,segments:400,faceHeatStrength:0.32,faceHeatHeight:0.85,faceHeatSpread:0.16,faceSparkRate:6,faceSparkLife:1.3,faceSparkSize:0.018,faceSparkSpeed:0.48,aromaStrength:0.24,aromaHeight:1.35,ingredientCount:6,ingredientSize:0.072,ingredientStrength:0.72,gatherSpeed:0.65,energyRingRadius:2.72,energyRingHeight:1.28,energyRingThickness:0.036,energyRingStrength:0.28,energyRingSpeed:0.13,aromaColor:"#b5dfbd",ingredientColor:"#ead4a3",energyRingColor:"#9ddfd4"};
const n=(key,label,group,min,max,step,unit='',help='')=>({key,label,group,min,max,step,unit,help,type:'number'});
export const FIELDS=[
 n('fireRadius','炉底火床半径','炉底温火',1.46,2.05,.01,'米','圆底内部持续燃烧，半径同时控制外围火流；真实炉底会遮挡其后的火焰。'),n('fireHeight','底火上舔高度','炉底温火',.2,1.8,.01,'米'),n('fireStrength','炉底火焰密度','炉底温火',0,3,.01),
 ...[['fireCore','火核颜色'],['fireMid','中层颜色'],['fireEdge','外焰颜色']].map(([key,label])=>({key,label,group:'炉底温火',type:'color'})),
 n('vaporRadius','炉口出生半径','炉口药气',.10,.28,.005,'米','真实烟囱内切半径约0.285米，此处限制出气源不穿口沿。'),n('vaporHeight','药气上升高度','炉口药气',.3,3,.01,'米'),
 n('vaporSpread','升高后的扩散','炉口药气',0,1.2,.01,'米'),n('vaporStrength','药气密度','炉口药气',0,3,.01),
 ...[['vaporCore','细丝亮部'],['vaporMid','药气中层'],['vaporEdge','药气外层']].map(([key,label])=>({key,label,group:'炉口药气',type:'color'})),
 n('flowSpeed','内部流动速度','卷曲与细丝',0,2,.01),n('turbulence','局部卷吸','卷曲与细丝',0,2,.01),n('filamentWidth','灵气细丝厚度','卷曲与细丝',.008,.12,.001,'米'),n('filamentStrength','灵气细丝亮度','卷曲与细丝',0,3,.01),
 n('orbitRadius','炉内炼化轨道','炼化与凝丹',.2,1.1,.01,'米'),n('orbitSpeed','炼化旋转速度','炼化与凝丹',-.5,.5,.01,'圈/秒'),n('coreRadius','凝丹光核半径','炼化与凝丹',.03,.35,.005,'米'),n('coreStrength','凝丹光核密度','炼化与凝丹',0,4,.01),
 {key:'coreColor',label:'凝丹金白色',group:'炼化与凝丹',type:'color'},
 n('burstRadius','成丹外扩范围','成丹与底部环',.3,2.5,.01,'米'),n('burstStrength','成丹爆发强度','成丹与底部环',0,4,.01),n('sigilRadius','底部能量环半径','成丹与底部环',2.52,4,.01,'米'),n('sigilStrength','底部能量环强度','成丹与底部环',0,2,.01),

 n('faceHeatStrength','炉窗热气密度','四面炉窗',0,2,0.01,''),
 n('faceHeatHeight','炉窗热气高度','四面炉窗',0.2,2,0.01,'米'),
 n('faceHeatSpread','炉窗热气扩散','四面炉窗',0.03,0.55,0.005,'米'),
 n('faceSparkRate','每窗火星出生率','四面火星',0,24,1,'个/秒'),
 n('faceSparkLife','火星存续时间','四面火星',0.3,3,0.05,'秒'),
 n('faceSparkSize','火星核心大小','四面火星',0.006,0.06,0.001,'米'),
 n('faceSparkSpeed','火星外扩速度','四面火星',0.1,1.3,0.01,'米/秒'),
 n('aromaStrength','四窗药香密度','炉窗药香',0,2,0.01,''),
 n('aromaHeight','药香上升高度','炉窗药香',0.3,2.5,0.01,'米'),
 n('ingredientCount','炉内素材数量','炉内素材汇集',0,12,1,'个'),
 n('ingredientSize','素材光团大小','炉内素材汇集',0.025,0.16,0.001,'米'),
 n('ingredientStrength','素材光团强度','炉内素材汇集',0,3,0.01,''),
 n('gatherSpeed','素材汇集速度','炉内素材汇集',0,2,0.01,''),
 n('energyRingRadius','环流半径','炉外能量环流',2.5,4.2,0.01,'米'),
 n('energyRingHeight','环流高度','炉外能量环流',0.3,3.2,0.01,'米'),
 n('energyRingThickness','环流厚度','炉外能量环流',0.012,0.12,0.001,'米'),
 n('energyRingStrength','环流发光强度','炉外能量环流',0,2,0.01,''),
 n('energyRingSpeed','环流旋转速度','炉外能量环流',-0.5,0.5,0.005,'圈/秒'),
 {key:'aromaColor',label:'药香颜色',group:'炉窗药香',type:'color'},
 {key:'ingredientColor',label:'素材光团颜色',group:'炉内素材汇集',type:'color'},
 {key:'energyRingColor',label:'环流颜色',group:'炉外能量环流',type:'color'},
  n('intensity','整体发光强度','画质',.2,3,.01),n('opacity','整体透明度','画质',0,1,.01),n('segments','体积积分精度','画质',24,512,1,'步'),n('seed','随机种子','画质',0,1000000,1)
];
export const LEGACY_KEYS=["schema", "fireRadius", "fireHeight", "fireStrength", "vaporRadius", "vaporHeight", "vaporSpread", "vaporStrength", "flowSpeed", "turbulence", "filamentWidth", "filamentStrength", "orbitRadius", "orbitSpeed", "coreRadius", "coreStrength", "burstRadius", "burstStrength", "sigilRadius", "sigilStrength", "intensity", "opacity", "fireCore", "fireMid", "fireEdge", "vaporCore", "vaporMid", "vaporEdge", "coreColor", "seed", "segments"];
function validateComplete(value,keys,version){
 if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('配方需要JSON对象。');
 if(value.schema!==version||Object.keys(value).length!==keys.length||keys.some(k=>!Object.hasOwn(value,k)))throw new Error('配方字段必须完整且无多余字段。');
 const result={schema:version};
 for(const f of FIELDS.filter(f=>keys.includes(f.key))){const v=value[f.key];if(f.type==='color'){if(typeof v!=='string'||!/^#[a-fA-F0-9]{6}$/.test(v))throw new Error(f.label+'须为#RRGGBB。');result[f.key]=v.toLowerCase();}
 else{if(typeof v!=='number'||!Number.isFinite(v)||v<f.min||v>f.max||(['seed','segments','ingredientCount'].includes(f.key)&&!Number.isInteger(v)))throw new Error(f.label+'超出允许范围。');result[f.key]=v;}}
 return result;
}
export function validateRecipe(value){
 if(value?.schema==='alchemy-vfx/1'){const old=validateComplete(value,LEGACY_KEYS,'alchemy-vfx/1');value={...DEFAULT_RECIPE,...old,schema:'alchemy-vfx/2'};}
 return validateComplete(value,Object.keys(DEFAULT_RECIPE),'alchemy-vfx/2');
}
