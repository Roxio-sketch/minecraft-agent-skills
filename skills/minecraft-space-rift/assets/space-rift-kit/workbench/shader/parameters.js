// Schema is independent of UI and GPU. All geometry values are local plane units.
export const controls = [
 ['u_length','裂缝长度',.1,8,.01,'geometry',1.8],['u_width','裂缝半宽',.002,1,.001,'geometry',.065],
 ['u_angle','旋转角度 / 弧度',-3.142,3.142,.01,'geometry',-.32],['u_bend','骨架弯曲',0,2,.01,'geometry',.42],
 ['u_roughness','边缘粗糙度',0,2,.01,'geometry',1.15],['u_frequency','细节密度',1,90,.1,'geometry',18],
 ['u_detail','噪声层级',1,8,1,'geometry',6],['u_branches','分支数量',0,8,1,'geometry',2],
 ['u_branchLength','分支长度',0,1.5,.01,'geometry',.22],['u_seed','随机种子',0,65535,1,'geometry',23],
 ['u_glow','边缘亮度',0,6,.01,'visual',1.6],['u_edgeThickness','边缘厚度',.0003,.05,.0001,'visual',.003],
 ['u_rimWidth','撕裂断面宽度',0,.18,.001,'visual',.045],['u_rimDepth','断面凹陷深度',0,.2,.001,'visual',.07],
 ['u_rimRelief','断面起伏',0,2,.01,'visual',.8],
 ['u_horizon','巨型无杂物模式',0,1,1,'geometry',0],['u_tipPower','两端收束',.7,3,.01,'geometry',1.65],
 ['u_domainWarp','轮廓域扭曲',0,1,.01,'geometry',.25],['u_edgeReach','边缘能量范围',.004,.12,.001,'visual',.032],
 ['u_interiorDepth','内部视差深度',.2,4,.01,'visual',1.4],['u_starDensity','内部星点密度',0,2,.01,'visual',1],
 ['u_galaxyX','主星系横向位置',-3,3,.01,'visual',0],['u_galaxyY','主星系纵向位置',-1,1,.01,'visual',0],
 ['u_galaxyScale','主星系尺度',.1,2,.01,'visual',.65],['u_interiorBrightness','内部明度',0,3,.01,'visual',1],
 ['u_viewTiltX','预览视差 X',-1.5,1.5,.01,'view',.28],['u_viewTiltY','预览视差 Y',-1.5,1.5,.01,'view',-.16],
 ['u_distortion','空间扭曲',0,3,.01,'visual',.7],['u_dispersion','色散',0,.4,.001,'visual',.04],
 ['u_insideSpeed','内部动画速度',0,4,.01,'visual',.4],['u_opacity','整体透明度',0,1,.01,'visual',1],
 ['u_debris','崩裂碎片',0,1,.01,'visual',.45],['u_layers','内部深度层数',1,6,1,'visual',3],
 ['u_corona','电离焰边',0,3,.01,'visual',1.2],['u_filaments','边缘流动放电',0,2,.01,'visual',.7],
 ['u_galaxies','内部旋涡星系',0,3,.01,'visual',1.2],['u_interiorMode','内部：0 幽暗 / 1 天空 / 2 外部',0,2,1,'visual',1],
 ['u_style','样式算法编号',0,8,1,'style',0],['u_styleAmount','样式强度',0,1,.01,'style',1],
 ['u_styleScale','样式细节尺度',.2,3,.01,'style',1],['u_edgeMotion','边缘运动速度',0,4,.01,'style',1],
 ['u_progress','生长进度',0,1,.001,'animation',1],['u_open','开口扩张',0,2.5,.01,'animation',1],
 ['u_shrink','收缩消失',0,1,.001,'animation',0],['u_localClose','局部闭合',0,1,.01,'animation',0],
 ['u_dynamic','动态扰动',0,1,.01,'animation',.15],['u_speed','动画速度',0,4,.01,'animation',1],
 ['u_sequenceEnabled','启用演出',0,1,1,'animation',0],['u_sequence','演出进度',0,1,.001,'animation',.82],
 ['u_time','时间 / 秒',0,86400,.01,'animation',0],['u_zoom','取景缩放',.1,12,.01,'view',1],
 ['u_grid','校验网格',0,1,1,'view',1]
];
export const defaults=Object.fromEntries(controls.map(c=>[c[0],c[6]]));
Object.assign(defaults,{u_edgeColor:'#9270ff',u_insideColor:'#171035',u_mode:0,u_quality:2,u_debug:0});
export const presets={
 slash:{...defaults,u_length:2.65,u_width:.07,u_angle:1.5708,u_bend:.13,u_roughness:.85,u_branches:0,u_grid:0,u_corona:.45,u_filaments:.3,u_galaxies:1.2,u_zoom:.85},
 breach:{...defaults,u_mode:1,u_length:2.65,u_width:.16,u_bend:.3,u_branches:0,u_branchLength:.13,u_roughness:1.45,u_seed:91,u_edgeColor:'#818dff',u_insideColor:'#283867',u_angle:1.5708,u_glow:1.6,u_grid:0,u_zoom:.85,u_corona:.65,u_filaments:.35},
 sky:{...defaults,u_mode:1,u_length:2.8,u_width:.12,u_bend:.22,u_branches:0,u_branchLength:.10,u_frequency:14,u_roughness:1.3,u_seed:271,u_edgeColor:'#768bff',u_insideColor:'#24335b',u_angle:1.5708,u_glow:1.7,u_zoom:.66,u_grid:0,u_corona:.65,u_filaments:.35,u_sequenceEnabled:1,u_sequence:1}
};
Object.assign(presets,{
 storm:{...presets.breach,u_style:1,u_length:2.0,u_width:.095,u_bend:.55,u_branches:7,u_branchLength:.25,u_seed:124,u_roughness:1.5,u_edgeColor:'#61bfff',u_insideColor:'#091329',u_corona:.6,u_glow:1.8,u_galaxies:0},
 cinder:{...presets.breach,u_style:2,u_length:1.65,u_width:.19,u_seed:57,u_edgeColor:'#ff742c',u_insideColor:'#18070f',u_glow:1.3,u_corona:.2,u_filaments:0,u_galaxies:0,u_interiorMode:0,u_branches:3,u_branchLength:.13},
 crystal:{...presets.breach,u_style:3,u_length:1.7,u_width:.18,u_seed:307,u_edgeColor:'#a1e8ff',u_insideColor:'#173c60',u_roughness:.45,u_frequency:7,u_bend:.35,u_corona:.15,u_glow:1.7,u_galaxies:0,u_branches:4,u_branchLength:.16},
 runic:{...presets.breach,u_style:4,u_length:1.65,u_width:.18,u_seed:409,u_edgeColor:'#ffc268',u_insideColor:'#29102e',u_roughness:.5,u_frequency:9,u_corona:.2,u_glow:1.2,u_branches:0,u_bend:.2,u_galaxies:.2},
 digital:{...presets.breach,u_style:5,u_length:1.7,u_width:.16,u_seed:112,u_edgeColor:'#51ffe5',u_insideColor:'#072c38',u_roughness:.35,u_frequency:8,u_corona:0,u_filaments:0,u_glow:1.4,u_branches:0,u_debris:0,u_dispersion:.16,u_galaxies:0},
 ink:{...presets.breach,u_style:6,u_length:1.75,u_width:.18,u_seed:691,u_edgeColor:'#b5ccc5',u_insideColor:'#122425',u_roughness:.4,u_frequency:6,u_corona:.1,u_filaments:0,u_glow:.65,u_galaxies:0,u_branches:2,u_branchLength:.1,u_debris:.1,u_interiorMode:0},
 echo:{...presets.breach,u_style:7,u_length:1.75,u_width:.14,u_seed:186,u_edgeColor:'#c986ff',u_insideColor:'#281645',u_corona:.1,u_glow:1.3,u_roughness:.6,u_branches:1,u_branchLength:.12,u_galaxies:.5},
 braid:{...presets.breach,u_style:8,u_length:2.0,u_width:.17,u_seed:821,u_edgeColor:'#fa74d3',u_insideColor:'#193b68',u_corona:.25,u_glow:1.4,u_bend:.24,u_branches:0,u_frequency:9,u_roughness:.5,u_galaxies:.2}
});
export const presetInfo={
 slash:['剑气切口','固定分形骨架与非对称窄开口','shadertoy'],breach:['破碎裂隙','宽开口、分支、星海与电离边','unity-portals'],sky:['天穹星海','横向天穹裂隙与三阶段演出','drei'],
 storm:['电弧断层','多条不规则跃迁电弧在开口边缘流窜','unreal-ribbons'],cinder:['黑焰侵蚀','脉动腐蚀轮廓、燃烧外缘与上升余烬','houdini-curl'],crystal:['棱晶冰裂','折面轮廓、局部晶格边界与棱镜色散','blender-voronoi'],
 runic:['符文封印','沿边界排列的算法符文和周期封印节律','unity-noise'],digital:['数字断层','分段阶梯轮廓、扫描线和背景错位','unity-glitch'],ink:['流墨幽门','低频液态起伏、纸墨笔触与扩散细丝','houdini-curl'],
 echo:['时空回声','沿距离场向外传播的多重轮廓与折射波','godot-screen'],braid:['磁暴双缝','双开口拓扑和交织运动的边缘丝带','webgpu-particles']
};
// A focused collection, reusing the same runtime/schema/core as the original lab.
const horizonBase={...presets.breach,u_horizon:1,u_angle:1.570796,u_length:3.25,u_width:.17,u_bend:.18,u_roughness:1.05,u_frequency:16,u_branches:0,u_debris:0,u_sequenceEnabled:0,u_style:0,u_corona:.75,u_filaments:.5,u_rimWidth:.024,u_rimDepth:.035,u_rimRelief:.65,u_zoom:.7,u_glow:1.35,u_grid:0,u_edgeColor:'#657eff',u_insideColor:'#17275a',u_galaxies:1.15,u_layers:3,u_dynamic:.06,u_distortion:.28};
export const horizonPresets={
 'horizon-slash':{...horizonBase,u_width:.065,u_roughness:.65,u_tipPower:2.3,u_seed:37,u_galaxyScale:.45},
 'horizon-tear':{...horizonBase,u_seed:194,u_width:.16,u_domainWarp:.3,u_galaxyX:-.15},
 'horizon-giant':{...horizonBase,u_length:3.8,u_width:.23,u_zoom:.63,u_seed:271,u_roughness:1.3,u_tipPower:1.35,u_domainWarp:.38,u_galaxyScale:.85,u_galaxyX:-.28}
};
export const horizonInfo={
 'horizon-slash':['锋锐细长','细线迅速延伸，尖端保持锐利','zenofi'],
 'horizon-tear':['自然撕裂','连续不对称切口，两级域扭曲和贴边流光','zenofi'],
 'horizon-giant':['巨型开天','横贯天穹的宽幅星河裂隙，无外围杂物','raul']
};
export function validateRecipe(raw){
 if(!raw || typeof raw!=='object' || Array.isArray(raw))throw Error('预设必须是 JSON 对象');
 if(raw.format && !['space-rift/2','ProceduralSpaceRiftKit.v1'].includes(raw.format))throw Error('未知预设版本');
 const p=raw.parameters??raw, next={...defaults};
 for(const [key,,min,max,step] of controls){
  if(p[key]===undefined)continue;
  if(typeof p[key]!=='number'||!Number.isFinite(p[key])||p[key]<min||p[key]>max)throw Error(`${key} 必须在 ${min}～${max} 范围内`);
  if(step===1&&!Number.isInteger(p[key]))throw Error(`${key} 必须是整数`);
  next[key]=step===1?Math.round(p[key]):p[key];
 }
 for(const key of ['u_mode','u_quality','u_debug'])if(p[key]!==undefined){
  const max=key==='u_mode'?1:key==='u_quality'?2:5;
  if(!Number.isInteger(p[key])||p[key]<0||p[key]>max)throw Error(`${key} 无效`);next[key]=p[key];
 }
 for(const key of ['u_edgeColor','u_insideColor'])if(p[key]!==undefined){if(!/^#[\da-f]{6}$/i.test(p[key]))throw Error(`${key} 颜色无效`);next[key]=p[key];}
 return next;
}
