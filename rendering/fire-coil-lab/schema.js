export const DEFAULT_RECIPE = {
 schema:'fire-coil/2',attachment:0,weaponRadius:.022,weaponWidth:.115,weaponTurns:.88,weaponStrength:1.35,radius:.54,height:1.65,turns:.82,strands:2,width:.35,rotationSpeed:.22,riseSpeed:.95,
 waveAmplitude:.12,waveSpeed:1.15,turbulence:1.05,tongueLength:1.25,erosion:.48,intensity:1.12,opacity:.94,
 coreColor:'#f0fbff',midColor:'#6ccfff',edgeColor:'#1257b5',emberRate:20,emberLife:1.15,emberSize:.019,
 wispRate:12,wispLife:.95,wispSize:.18,outwardSpeed:.32,lift:.48,motionInheritance:.35,seed:17,segments:512,rampTime:.6,firstPerson:.18
};
const n=(key,label,group,min,max,step,unit='')=>({key,label,group,min,max,step,unit,type:'number'});
export const FIELDS=[
 n('attachment','附着模式','武器附着',0,2,1),n('weaponRadius','武器环绕间距','武器附着',.01,.6,.005,'米'),
 n('weaponWidth','武器火流宽度','武器附着',.02,.5,.005,'米'),n('weaponTurns','武器缠绕圈数','武器附着',.25,5,.05,'圈'),n('weaponStrength','武器火焰强度','武器附着',0,3,.01),
 n('radius','环绕半径','缠绕形状',.15,1.8,.01,'米'),n('height','缠绕高度','缠绕形状',.2,3.5,.01,'米'),
 n('turns','螺旋圈数','缠绕形状',.25,5,.01,'圈'),n('strands','火带条数','缠绕形状',1,8,1,'条'),
 n('width','火流宽度','缠绕形状',.04,.65,.01,'米'),
 n('rotationSpeed','整体转速','运动',-2,2,.01,'圈/秒'),n('riseSpeed','火焰流速','运动',0,3,.01),
 n('waveAmplitude','路径摆幅','运动',0,.3,.005,'米'),n('waveSpeed','路径摆速','运动',0,3,.01),
 n('turbulence','火舌扰动','火焰材质',0,2,.01),n('tongueLength','火舌拉伸','火焰材质',.1,2,.01),
 n('erosion','侵蚀与留空','火焰材质',0,1,.01),n('segments','体积采样精度','火焰材质',24,512,1,'步'),n('intensity','发光强度','火焰材质',.2,4,.01),
 n('opacity','主体透明度','火焰材质',0,1,.01),
 ...[['coreColor','火核颜色'],['midColor','中层颜色'],['edgeColor','边缘颜色']].map(([key,label])=>({key,label,group:'火焰材质',type:'color'})),
 n('wispRate','离体火舌出生率','离体火舌',0,80,1,'个/秒'),n('wispLife','离体火舌寿命','离体火舌',.15,2,.01,'秒'),
 n('wispSize','离体火舌尺寸','离体火舌',.05,.6,.005,'米'),
 n('emberRate','余烬出生率','余烬',0,160,1,'个/秒'),n('emberLife','余烬寿命','余烬',.15,3,.01,'秒'),
 n('emberSize','余烬尺寸','余烬',.005,.1,.001,'米'),
 n('outwardSpeed','离体外散速度','离体运动',0,2,.01,'米/秒'),n('lift','上升速度','离体运动',0,2.5,.01,'米/秒'),
 n('motionInheritance','身体运动继承','离体运动',0,1,.01),
 n('rampTime','引火与收火时间','生命周期',.05,2,.01,'秒'),n('firstPerson','第一人称自身强度','生命周期',0,1,.01),
 n('seed','随机种子','生命周期',0,1000000,1)
];
const INTEGERS=new Set(['strands','segments','seed','attachment']);
export function validateRecipe(value){
 if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('配方必须是 JSON 对象。');
 if(value.schema==='fire-coil/1'){
  const legacy=Object.keys(DEFAULT_RECIPE).filter(k=>!['attachment','weaponRadius','weaponWidth','weaponTurns','weaponStrength'].includes(k));
  if(Object.keys(value).length!==legacy.length||legacy.some(k=>!Object.hasOwn(value,k)))throw new Error('旧版配方必须包含精确31个字段。');
  value={...DEFAULT_RECIPE,...value,schema:'fire-coil/2'};
 }
 const keys=Object.keys(value),expected=Object.keys(DEFAULT_RECIPE);
 const unknown=keys.filter(k=>!expected.includes(k)),missing=expected.filter(k=>!Object.hasOwn(value,k));
 if(unknown.length||missing.length)throw new Error(`配方字段不匹配。多余：${unknown.join('、')||'无'}；缺少：${missing.join('、')||'无'}。`);
 if(value.schema!=='fire-coil/2')throw new Error('只支持 fire-coil/2 或完整 fire-coil/1 配方。');
 const result={schema:value.schema};
 for(const f of FIELDS){const v=value[f.key];if(f.type==='color'){if(typeof v!=='string'||!/^#[0-9a-fA-F]{6}$/.test(v))throw new Error(`${f.label}必须为 #RRGGBB。`);result[f.key]=v.toLowerCase();}
 else{if(typeof v!=='number'||!Number.isFinite(v)||v<f.min||v>f.max||(INTEGERS.has(f.key)&&!Number.isInteger(v)))throw new Error(`${f.label}须在 ${f.min}–${f.max} 范围${INTEGERS.has(f.key)?'且为整数':''}。`);result[f.key]=v;}}
 return result;
}
