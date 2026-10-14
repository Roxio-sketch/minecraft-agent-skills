/* Energy VFX recipe v2. All listed fields have a shared renderer/simulation consumer. */
const FIELDS = [
 ['radius','形态','半径 · 米',.25,4,.01,1.25],
 ['stretch','形态','纵向拉伸',.25,3,.01,1],
 ['shell','形态','壳层比例',0,1,.01,.64],
 ['shellWidth','形态','壳层厚度',.025,.6,.005,.22],
 ['core','形态','核心密度',0,4,.01,.5],
 ['scale','流动','大尺度纹理',.5,9,.01,2.7],
 ['detail','流动','细节频率',1,16,.01,5.8],
 ['speed','流动','演化速度',0,3,.01,.52],
 ['swirl','流动','旋涡强度',-4,4,.01,1.15],
 ['warp','流动','卷曲位移',0,1.5,.01,.38],
 ['radial','流动','径向流速 · 负值吸入',-2,2,.01,-.25],
 ['rise','流动','上升流速',-2,2,.01,.1],
 ['density','密度与细丝','总体密度',0,12,.01,4.5],
 ['erosion','密度与细丝','侵蚀阈值',0,.95,.005,.36],
 ['softness','密度与细丝','侵蚀过渡',.02,.5,.005,.15],
 ['filaments','密度与细丝','细丝占比',0,1,.01,.92],
 ['sharpness','密度与细丝','细丝锐度',1,30,.1,13],
 ['edge','密度与细丝','边界撕裂',0,.8,.005,.32],
 ['emission','发光与合成','发光强度',0,12,.01,3.8],
 ['hot','发光与合成','白热核心',0,2,.01,.65],
 ['absorption','发光与合成','体积吸收',0,4,.01,.85],
 ['bloom','发光与合成','Bloom 强度',0,3,.01,.65],
 ['bloomRadius','发光与合成','Bloom 半径 · 像素',0,32,.1,8],
 ['threshold','发光与合成','Bloom 发光阈值',0,5,.01,.55],
 ['distortion','发光与合成','背景扰动',0,.08,.001,.012],
 ['exposure','发光与合成','特效曝光',.1,3,.01,1],
 ['ring','冲击波','环强度',0,4,.01,.5],
 ['ringRadius','冲击波','环半径 · 相对主体',.1,2.5,.01,1.12],
 ['ringWidth','冲击波','环厚度',.025,.4,.005,.065],
 ['ringTilt','冲击波','环倾斜 · 弧度',-1.57,1.57,.01,.2],
 ['burstSpeed','冲击波','爆发扩张速度',0,2,.01,.9],
 ['mode','生命周期','模式：0持续 / 1蓄力爆发 / 2消散',0,2,1,0],
 ['charge','生命周期','蓄力时长 · 秒',.2,8,.1,2.4],
 ['burst','生命周期','爆发时长 · 秒',.2,8,.1,1.4],
 ['dissolve','生命周期','消散时长 · 秒',.2,8,.1,2],
 ['seed','生命周期','随机种子',0,9999,1,42],
 ['steps','渲染','光线步数',24,128,1,64],
 ['resolution','渲染','渲染比例',.25,1,.05,.6],
 ['shape','形态','载体：0球 / 1环 / 2涡柱 / 3束',0,3,1,0],
 ['attachment','持续发散','发射源：0空间 / 1身体 / 2装备 / 3两者',0,3,1,0],
 ['outflow','持续发散','离体扩散速度 · 米/秒',.03,1.5,.01,.32],
 ['puffRate','持续发散','三维气团生成率 · 每秒',2,30,1,14],
 ['puffLife','持续发散','气团寿命 · 秒',.4,5,.1,2],
 ['spread','持续发散','气团扩张 · 米',.06,1.2,.01,.23],
 ['bodyWeight','持续发散','身体发散强度',0,2,.01,.8],
 ['gearWeight','持续发散','装备发散强度',0,2,.01,1.3],
 ['firstPerson','持续发散','第一人称自身强度',0,1,.01,.1],
 ['inertia','持续发散','继承人物运动速度',0,1,.01,.15]
];
function defaultRecipe(){return {schema:'energy-vfx/2',name:'青焰 · 卷吸',color:'#3bcfff',secondary:'#8c52ff',params:Object.fromEntries(FIELDS.map(f=>[f[0],f[6]]))};}
function validateRecipe(r){
 if(!r||Array.isArray(r)||typeof r!=='object')throw Error('配方必须是 JSON 对象');
 const keys=['schema','name','color','secondary','params'];
 for(const k of Object.keys(r))if(!keys.includes(k))throw Error('未知配方字段：'+k);
 for(const k of keys)if(!(k in r))throw Error('缺少字段：'+k);
 if(!['energy-vfx/1','energy-vfx/2'].includes(r.schema))throw Error('不支持的配方版本');
 if(typeof r.name!=='string'||!r.name.trim()||r.name.length>80)throw Error('名称需为 1–80 字符');
 for(const k of ['color','secondary'])if(typeof r[k]!=='string'||!/^#[0-9a-f]{6}$/i.test(r[k]))throw Error(k+' 需为六位 HEX 色值');
 if(!r.params||Array.isArray(r.params)||typeof r.params!=='object')throw Error('params 必须为对象');
 const required=r.schema==='energy-vfx/1'?FIELDS.slice(0,39):FIELDS;
 for(const k of Object.keys(r.params))if(!required.some(f=>f[0]===k))throw Error('未知参数：'+k);
 for(const [k,,label,min,max,step] of required){const v=r.params[k];if(typeof v!=='number'||!Number.isFinite(v)||v<min||v>max||(step===1&&!Number.isInteger(v)))throw Error(label+' 超出范围或类型错误');}
 return {...JSON.parse(JSON.stringify(r)),schema:'energy-vfx/2',params:{...defaultRecipe().params,...r.params}};
}
const PRESETS=[
 defaultRecipe(),
 {...defaultRecipe(),name:'金焰 · 聚气',color:'#ff9c32',secondary:'#e62f5e',params:{...defaultRecipe().params,shell:.4,core:1.4,swirl:2.2,radial:-.65,filaments:.5,erosion:.28,hot:1,ring:0,mode:1}},
 {...defaultRecipe(),name:'紫霄 · 裂隙',color:'#b069ff',secondary:'#39dfff',params:{...defaultRecipe().params,stretch:1.7,shell:.82,shellWidth:.12,edge:.55,sharpness:22,filaments:.92,warp:.65,core:.12,ring:0,emission:5}},
 {...defaultRecipe(),name:'赤炎 · 冲击',color:'#ff4628',secondary:'#ffcb4a',params:{...defaultRecipe().params,shell:.6,ring:2,ringRadius:1.2,ringTilt:0,ringWidth:.08,mode:1,charge:1.4,burst:2,dissolve:2,burstSpeed:1.3}},
 {...defaultRecipe(),name:'冰蓝 · 护罩',color:'#35b8ff',secondary:'#87f7e4',params:{...defaultRecipe().params,shell:1,shellWidth:.045,core:0,edge:.12,warp:.12,erosion:.18,filaments:.25,absorption:.15,emission:2,ring:0}},
 {...defaultRecipe(),name:'幽气 · 消散',color:'#5bdba7',secondary:'#266bb7',params:{...defaultRecipe().params,core:.15,shell:.5,filaments:.3,emission:1.1,hot:0,density:2,absorption:1.8,rise:.5,mode:2,ring:0}},
 {...defaultRecipe(),name:'天旋 · 涡柱',color:'#25e6bb',secondary:'#3e61e8',params:{...defaultRecipe().params,shape:2,stretch:1.6,core:.1,shell:.8,swirl:3,radial:-.6,rise:.45,filaments:.94,ring:0}},
 {...defaultRecipe(),name:'白虹 · 能量束',color:'#a7e8ff',secondary:'#377cff',params:{...defaultRecipe().params,shape:3,stretch:2.4,shellWidth:.12,shell:.5,core:.12,rise:1.4,ring:0,hot:1.1}}
];
const BLUE_GHOST={...defaultRecipe(),name:'幽气 · 冰蓝发散',color:'#2388ff',secondary:'#73dbff',params:{...defaultRecipe().params,attachment:3,radius:1,stretch:1.4,core:0,ring:0,mode:0,density:2.5,filaments:.58,sharpness:11,erosion:.28,softness:.18,warp:.48,hot:0,emission:1.5,bloom:.35,bloomRadius:7,absorption:.5,distortion:.003,speed:.4,rise:.6,radial:0,scale:3.5,detail:7,steps:64}};
PRESETS.push(BLUE_GHOST,
 {...BLUE_GHOST,name:'玄霭 · 蓝色薄雾',color:'#3977b7',secondary:'#67b8e5',params:{...BLUE_GHOST.params,density:1,filaments:.14,emission:.7,absorption:1.1,spread:.34,puffLife:3.1,puffRate:10,outflow:.25,rise:.3,bloom:.12,erosion:.22}},
 {...BLUE_GHOST,name:'冥蓝 · 冷焰',color:'#0868ff',secondary:'#25d8ff',params:{...BLUE_GHOST.params,density:3.3,filaments:.74,emission:2.1,spread:.2,puffLife:1.8,puffRate:17,outflow:.18,rise:1.35,stretch:2,warp:.65,bloom:.4}},
 {...BLUE_GHOST,name:'魂丝 · 漂散',color:'#67b8ff',secondary:'#4977ff',params:{...BLUE_GHOST.params,attachment:1,density:4,filaments:.98,sharpness:22,emission:1.5,spread:.24,puffLife:1.7,puffRate:18,outflow:.5,rise:.4,erosion:.4,bloom:.27}},
 {...BLUE_GHOST,name:'霜息 · 装备溢散',color:'#3e91ff',secondary:'#9de6ff',params:{...BLUE_GHOST.params,attachment:2,radius:.85,bodyWeight:0,gearWeight:1.6,spread:.26,puffRate:16,filaments:.65,rise:.48,density:2.8}},
 {...BLUE_GHOST,name:'幽纹 · 卷曲游丝',color:'#3b78ff',secondary:'#52ecff',params:{...BLUE_GHOST.params,density:3.8,filaments:.94,sharpness:18,swirl:2.4,warp:.8,spread:.28,puffLife:2.1,puffRate:15,outflow:.32,rise:.65,bloom:.2}}
);
const SMOKE_VEIL={...BLUE_GHOST,name:'幽纱 · 月白卷烟',color:'#e7edf0',secondary:'#bbd0d5',params:{...BLUE_GHOST.params,radius:1.15,shell:1,shellWidth:.055,filaments:.8,sharpness:12,scale:1.05,detail:1.8,warp:1.4,swirl:1.6,density:1.8,erosion:.22,softness:.28,edge:.3,emission:12,exposure:1.8,absorption:.5,bloom:0,bloomRadius:4,threshold:1.2,distortion:0,spread:.28,puffLife:3,puffRate:8,rise:.5,outflow:.55,stretch:1.4,speed:1.2,steps:128,resolution:1}};
PRESETS.push(SMOKE_VEIL,
 {...SMOKE_VEIL,name:'幽纱 · 冰蓝卷烟',color:'#94d8ff',secondary:'#256aaf',params:{...SMOKE_VEIL.params,emission:7,density:2.2,exposure:1.5,bloom:.04,sharpness:10,bodyWeight:.7,gearWeight:1.4}},
 {...SMOKE_VEIL,name:'残魂 · 断续薄纱',color:'#769dcc',secondary:'#c2e7f5',params:{...SMOKE_VEIL.params,shell:.85,erosion:.46,softness:.15,sharpness:18,scale:1.9,warp:1.4,puffLife:2.4,puffRate:13,density:4.4,swirl:2.1,emission:.85,bloom:.08}},
 {...SMOKE_VEIL,name:'灵息 · 螺旋轻烟',color:'#73cce4',secondary:'#c7edf3',params:{...SMOKE_VEIL.params,swirl:3.1,scale:1.3,detail:2.6,warp:1.05,stretch:1.8,rise:1.1,outflow:.18,puffLife:2,puffRate:15,spread:.2,density:3.1,emission:.8}},
 {...SMOKE_VEIL,name:'霜缕 · 装备烟丝',color:'#639de8',secondary:'#c7e2ff',params:{...SMOKE_VEIL.params,attachment:2,bodyWeight:0,gearWeight:1.5,shell:.4,filaments:1,sharpness:16,scale:2.1,detail:4,spread:.2,puffLife:2,puffRate:15,density:4.8,warp:1.3,emission:1,bloom:.12}},
 {...SMOKE_VEIL,name:'幽澜 · 舒展流纱',color:'#5485bd',secondary:'#b6dfe9',params:{...SMOKE_VEIL.params,scale:1.05,detail:2.4,warp:1.5,swirl:-1.7,stretch:1.15,outflow:.5,rise:.2,spread:.28,puffLife:2.6,puffRate:12,sharpness:11,density:2.3,emission:.75,bloom:.1}}
);
if(typeof module!=='undefined')module.exports={FIELDS,defaultRecipe,validateRecipe,PRESETS,BLUE_GHOST};
