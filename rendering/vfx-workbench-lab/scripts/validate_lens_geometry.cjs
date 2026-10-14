// Independent CPU contract for artistic angular environment lensing, version 4.
// Environment rays share one radial map over all 360 azimuths. Only actual
// foreground actors are protected; terrain depth never reduces the full kick.
// Does not execute production shaders or validate game capture/state ordering.
const fs=require('node:fs'),path=require('node:path');
const add=(a,b)=>a.map((v,i)=>v+b[i]);
const subtract=(a,b)=>a.map((v,i)=>v-b[i]);
const scale=(v,s)=>v.map(x=>x*s);
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
const length=v=>Math.hypot(...v);
const normalize=v=>scale(v,1/length(v));
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const clamp=(v,lo,hi)=>Math.max(lo,Math.min(hi,v));
const smoothstep=(lo,hi,x)=>{const t=clamp((x-lo)/(hi-lo),0,1);return t*t*(3-2*t);};
const near=(a,b,tolerance=1e-11)=>Math.abs(a-b)<=tolerance;
const vecNear=(a,b,tolerance=1e-11)=>a.every((v,i)=>near(v,b[i],tolerance));
const requireValue=(condition,message)=>{if(!condition)throw new Error(message);};
const coneRay=(theta,azimuth=0)=>[Math.sin(theta)*Math.cos(azimuth),Math.sin(theta)*Math.sin(azimuth),Math.cos(theta)];
function rotate(v,axis,angle){
  axis=normalize(axis);
  return add(add(scale(v,Math.cos(angle)),scale(cross(axis,v),Math.sin(angle))),scale(axis,dot(axis,v)*(1-Math.cos(angle))));
}
function project(direction,width,height,fov=70){
  const t=Math.tan(fov*Math.PI/360),aspect=width/height;
  return [.5+direction[0]/(2*direction[2]*t*aspect),.5+direction[1]/(2*direction[2]*t)];
}
const base={center:[0,0,10],scale:1,core:.6,bend:1.1,strength:1,radius:5};

function environmentRay(ray,p=base){
  const rd=normalize(ray),distance=length(p.center),axis=normalize(p.center);
  const cosine=clamp(dot(rd,axis),-1,1),tangent=subtract(rd,scale(axis,cosine));
  const tangentLength=length(tangent),theta=Math.atan2(tangentLength,cosine);
  const impact=distance*tangentLength/p.scale,coreAngle=p.core*p.scale/distance;
  const soft=Math.max(.25*coreAngle,1e-5);
  const window=1-smoothstep(.75*p.radius,p.radius,impact);
  // FULL angular kick: sky, ground, and finite environment use this same map.
  const alpha=clamp(p.strength,0,1)*p.bend*2*coreAngle*theta/(theta*theta+soft*soft)*window;
  const beta=theta-alpha;
  const candidate=tangentLength<1e-14?rd:add(scale(axis,Math.cos(beta)),scale(normalize(tangent),Math.sin(beta)));
  const accepted=theta<1.5&&Math.abs(beta)<1.5;
  return {rd,axis,distance,theta,impact,alpha,beta,candidate,accepted,outgoing:accepted?candidate:rd};
}

// Depth rejects terrain in projected bounds. Foreground classification belongs
// to the entire actor, never to each arm/torso surface point independently.
function foregroundActor(pixel,massDistance){
  return pixel.actorBounds&&pixel.finalDepth<.999999
    &&pixel.finalDepth+1e-6<pixel.environmentDepth
    &&(pixel.cameraSubject||pixel.actorNearestLongitudinal<=massDistance+.02);
}
function restoredBackgroundSource(pixel,massDistance){
  return foregroundActor(pixel,massDistance)?pixel.environmentColor:pixel.finalColor;
}
function compositeEnvironment(outputPixel,sourcePixel,massDistance){
  return foregroundActor(outputPixel,massDistance)?outputPixel.finalColor:restoredBackgroundSource(sourcePixel,massDistance);
}
const actorPixel={actorBounds:true,finalDepth:.3,environmentDepth:.8,actorNearestLongitudinal:3.5,sourceLongitudinal:4,finalColor:[1,0,0],environmentColor:[0,.3,1]};
const terrainPixel={actorBounds:false,finalDepth:.7,environmentDepth:.7,sourceLongitudinal:4,finalColor:[0,.8,.2],environmentColor:[0,.8,.2]};

const results=[];
function check(name,run){
  try{results.push({name,passed:true,...(run()||{})});}
  catch(error){results.push({name,passed:false,error:String(error.message||error)});}
}

check('full_360_azimuth_radial_consistency',()=>{
  let cases=0,maxRadialError=0,maxPlaneError=0;
  for(const theta of [.12,.25,.4])for(let degree=0;degree<360;degree++){
    const angle=degree*Math.PI/180,r=environmentRay(coneRay(theta,angle));
    const expected=[Math.sin(r.beta)*Math.cos(angle),Math.sin(r.beta)*Math.sin(angle),Math.cos(r.beta)];
    const error=length(subtract(r.outgoing,expected));
    const planeError=Math.abs(dot(cross(r.rd,r.axis),r.outgoing));
    maxRadialError=Math.max(maxRadialError,error);maxPlaneError=Math.max(maxPlaneError,planeError);cases++;
    requireValue(r.accepted&&error<1e-10&&planeError<1e-10,'Angular map changed with azimuth or left the radial ray plane');
  }
  return {cases,azimuthDegrees:360,maxRadialError,maxPlaneError};
});

check('equal_sky_and_near_ground_use_same_angular_kick',()=>{
  const rd=coneRay(.4,.8),reference=environmentRay(rd);
  for(const originalTerrainDepth of [0,.1,.5,.9,1]){
    const r=environmentRay(rd,{...base,originalTerrainDepth});
    requireValue(near(r.alpha,reference.alpha)&&vecNear(r.outgoing,reference.outgoing),'Near terrain depth weakened the environment lens');
  }
  return {originalDepthCases:5,fullAlphaRadians:reference.alpha};
});

check('radial_map_remains_circular_in_nonsquare_pixels',()=>{
  let maxPixelRadiusError=0;
  for(const [w,h] of [[854,480],[1681,999],[2501,1221],[500,1000]]){
    const a=project(environmentRay(coneRay(.4,0)).outgoing,w,h),b=project(environmentRay(coneRay(.4,Math.PI/2)).outgoing,w,h);
    const error=Math.abs(Math.abs((a[0]-.5)*w)-Math.abs((b[1]-.5)*h));maxPixelRadiusError=Math.max(maxPixelRadiusError,error);
    requireValue(error<1e-8,'Aspect ratio changed the circular radial transport');
  }
  return {viewports:4,maxPixelRadiusError};
});

check('angular_map_is_world_rotation_equivariant',()=>{
  let cases=0,maxDirectionError=0;
  for(const axis of [[1,0,0],[0,1,0],[.3,.7,.2]])for(const angle of [-2,-.4,.8,2.4]){
    const rd=coneRay(.3,.6),reference=environmentRay(rd);
    const r=environmentRay(rotate(rd,axis,angle),{...base,center:rotate(base.center,axis,angle)});
    const error=length(subtract(r.outgoing,rotate(reference.outgoing,axis,angle)));maxDirectionError=Math.max(maxDirectionError,error);cases++;
    requireValue(error<1e-10&&near(r.alpha,reference.alpha),'Angular lens depends on arbitrary world axes');
  }
  return {cases,maxDirectionError};
});

check('foreground_actor_requires_bounds_depth_difference_and_object_policy',()=>{
  const cases=[
    [actorPixel,true],
    [{...actorPixel,actorBounds:false},false],
    [{...actorPixel,finalDepth:.8},false],
    [{...actorPixel,finalDepth:.9},false],
    [{...actorPixel,actorNearestLongitudinal:12,sourceLongitudinal:14},false],
    [{...actorPixel,finalDepth:1,environmentDepth:1},false]
  ];
  for(const [pixel,expected] of cases)requireValue(foregroundActor(pixel,10)===expected,'Actor protection used an incomplete mask condition');
  return {cases:cases.length,frontActorProtected:true,behindActorProtected:false};
});

check('one_actor_crossing_mass_plane_keeps_all_visible_parts',()=>{
  for(const sourceLongitudinal of [9.7,10,10.1,10.8]){
    const pixel={...actorPixel,actorNearestLongitudinal:9.7,sourceLongitudinal};
    requireValue(foregroundActor(pixel,10),'One crossing actor was split into foreground and lensed body parts');
    requireValue(vecNear(restoredBackgroundSource(pixel,10),pixel.environmentColor),'A crossing limb leaked into the environment source');
  }
  return {bodySurfaceCases:4,frontAndRearSurfacesPreservedTogether:true};
});

check('third_person_camera_subject_is_readable_behind_and_inside_mass',()=>{
  for(const massDistance of [0,.1,2,10]){
    const pixel={...actorPixel,cameraSubject:true,actorNearestLongitudinal:14,sourceLongitudinal:15};
    requireValue(foregroundActor(pixel,massDistance),'Camera subject disappeared while entering the mass');
    requireValue(vecNear(compositeEnvironment(pixel,terrainPixel,massDistance),pixel.finalColor),'Camera subject was distorted');
  }
  return {cameraMassDistances:[0,.1,2,10]};
});

check('terrain_under_actor_bounding_box_is_not_falsely_protected',()=>{
  const ground={...terrainPixel,actorBounds:true};
  requireValue(!foregroundActor(ground,10),'Bounding-box overlap falsely protected terrain');
  requireValue(vecNear(restoredBackgroundSource(ground,10),ground.finalColor),'Foot-level ground was replaced as if it were actor color');
  const closePrecision={...ground,finalDepth:.7-5e-7};
  requireValue(!foregroundActor(closePrecision,10),'Subthreshold depth precision caused false actor coverage');
  return {bboxTerrainRejected:true,depthTolerance:1e-6};
});

check('foreground_actor_output_is_preserved_inside_decorative_volume',()=>{
  const massDistance=6,decorativeRadius=12;
  const output=compositeEnvironment(actorPixel,terrainPixel,massDistance);
  requireValue(massDistance<decorativeRadius&&vecNear(output,actorPixel.finalColor),'Foreground actor changed because camera entered decorative bounds');
  return {cameraInsideVolume:true,massDistance,decorativeRadius};
});

check('foreground_actor_source_is_restored_from_pre_entity_environment',()=>{
  const output=compositeEnvironment(terrainPixel,actorPixel,10);
  requireValue(vecNear(output,actorPixel.environmentColor)&&!vecNear(output,actorPixel.finalColor),'Foreground actor color was cloned into an environment image');
  return {foregroundSourceRejected:true,preEntityEnvironmentRestored:true};
});

check('all_linear_footprint_taps_restore_foreground_source_colors',()=>{
  const samples=[actorPixel,{...actorPixel,finalColor:[.8,0,0]},terrainPixel,{...terrainPixel,finalColor:[0,.2,.6]}];
  const weights=[.15,.25,.35,.25];
  let restored=[0,0,0];
  samples.forEach((pixel,i)=>{restored=add(restored,scale(restoredBackgroundSource(pixel,10),weights[i]));});
  const expected=add(scale(actorPixel.environmentColor,.4),add(scale(terrainPixel.finalColor,.35),scale([0,.2,.6],.25)));
  requireValue(vecNear(restored,expected)&&restored[0]===0,'Bilinear sampling leaked red actor colors through a depth-only nearest check');
  return {footprintTaps:4,restoredColor:restored};
});

check('behind_mass_actors_remain_lensable_source_colors',()=>{
  const behind={...actorPixel,actorNearestLongitudinal:13.5,sourceLongitudinal:14};
  requireValue(!foregroundActor(behind,10)&&vecNear(restoredBackgroundSource(behind,10),behind.finalColor),'Behind-mass actor was erroneously erased from environment lens');
  return {sourceLongitudinal:14,massDistance:10};
});

check('zero_strength_preserves_environment_projection',()=>{
  let cases=0,maxUvError=0;
  for(const [w,h] of [[854,480],[1681,999],[500,1000],[1000,1000]])for(const theta of [.001,.12,.4])for(const azimuth of [0,.8,2.5]){
    const rd=coneRay(theta,azimuth),r=environmentRay(rd,{...base,strength:0});
    const a=project(rd,w,h),b=project(r.outgoing,w,h),error=Math.hypot(a[0]-b[0],a[1]-b[1]);maxUvError=Math.max(maxUvError,error);cases++;
    requireValue(error<1e-10&&r.alpha===0,'Zero strength changed the environment lookup');
  }
  return {cases,maxUvError};
});

check('core_strength_radius_controls_have_independent_geometric_effects',()=>{
  const rd=coneRay(.25),reference=environmentRay(rd);
  const half=environmentRay(rd,{...base,strength:.5});
  requireValue(near(half.alpha*2,reference.alpha),'Strength is not linear in the full angular kick');
  const doubledCore=environmentRay(rd,{...base,core:1.2});
  const expectedCoreRatio=2*(.25*.25+.015*.015)/(.25*.25+.03*.03);
  requireValue(near(doubledCore.alpha/reference.alpha,expectedCoreRatio),'Core scale does not follow independent softened-mass formula');
  const nearEdge=coneRay(.45),small=environmentRay(nearEdge,{...base,radius:3}),large=environmentRay(nearEdge,{...base,radius:6});
  requireValue(small.alpha===0&&large.alpha>0,'Radius did not expand the environment lens independently');
  return {halfStrengthRatio:half.alpha/reference.alpha,doubledCoreRatio:doubledCore.alpha/reference.alpha,smallRadiusDeflection:small.alpha,largeRadiusDeflection:large.alpha};
});

check('uniform_world_scale_and_decorative_bound_do_not_change_angular_map',()=>{
  const rd=coneRay(.3,.9),reference=environmentRay(rd);
  for(const factor of [.2,.7,2,8]){
    const r=environmentRay(rd,{...base,center:scale(base.center,factor),scale:factor,decorativeRadius:100*factor});
    requireValue(near(r.alpha,reference.alpha)&&vecNear(r.outgoing,reference.outgoing),'Uniform world scale changed normalized angular lens');
  }
  return {scales:[.2,.7,2,8]};
});

check('excessive_deflection_cannot_wrap_into_forward_snapshot',()=>{
  const rd=coneRay(.008),r=environmentRay(rd,{...base,bend:2});
  requireValue(Math.abs(r.beta)>2*Math.PI&&r.candidate[2]>0,'Reference case must expose trigonometric forward wrapback');
  requireValue(!r.accepted&&vecNear(r.outgoing,rd),'Multi-turn candidate must never be sampled');
  return {rawAlpha:r.alpha,rawBeta:r.beta,rawCandidateForwardCosine:r.candidate[2],accepted:r.accepted};
});

check('finite_axis_and_bounded_angular_branch',()=>{
  let accepted=0,rejected=0;
  for(const bend of [.1,1,1.3,2])for(let i=0;i<=1000;i++){
    const r=environmentRay(coneRay(i*1.49/1000),{...base,bend});
    requireValue([r.alpha,r.beta,...r.outgoing].every(Number.isFinite),'Axis or branch is nonfinite');
    requireValue(near(length(r.outgoing),1),'Outgoing direction is not normalized');
    if(r.accepted){accepted++;requireValue(Math.abs(r.beta)<1.5,'Accepted branch winds around the mass');}
    else{rejected++;requireValue(vecNear(r.outgoing,r.rd),'Rejected branch does not fall back');}
  }
  return {cases:accepted+rejected,accepted,rejected};
});

check('analytic_exterior_critical_curve_has_signed_parity',()=>{
  const thetaE=Math.sqrt(.132-.015*.015),at=environmentRay(coneRay(thetaE));
  const inner=environmentRay(coneRay(thetaE*.75)),outer=environmentRay(coneRay(thetaE*1.2));
  requireValue(at.impact<.75*base.radius&&Math.abs(at.beta)<1e-12&&inner.beta<0&&outer.beta>0,'Exterior map lost its circular critical curve or signed parity');
  return {einsteinAngleRadians:thetaE,innerBeta:inner.beta,outerBeta:outer.beta};
});

const failed=results.filter(r=>!r.passed);
const report={
  version:4,model:'Artistic full angular environment lens with 360-degree radial symmetry and whole-actor foreground restoration',
  status:failed.length?'failed':'passed',checks:results.length,passed:results.length-failed.length,failed:failed.length,tests:results,
  productionShaderExecuted:false,minecraftRuntimeVerified:false,
  limits:[
    'Synthetic actor/depth samples check the intended mask contract, not actual raster coverage or Minecraft entity ordering.',
    'Sky and nearby terrain intentionally share a full angular map; this is an artistic environment lens, not finite-source transport or GR.',
    'Pre-entity source-color recovery requires separate same-frame environment capture; this CPU script does not validate that capture.',
    'Actual 360-degree ground ring appearance, foreground pixels, offscreen recovery, depth tolerance, and performance require Forge captures.'
  ]
};
const arg=process.argv.indexOf('--output');
const output=arg>=0?path.resolve(process.argv[arg+1]):path.resolve(__dirname,'../evidence/third-person-lens-20261005/math.json');
fs.mkdirSync(path.dirname(output),{recursive:true});
fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({version:4,status:report.status,checks:report.checks,failed:report.failed,output,productionShaderExecuted:false,minecraftRuntimeVerified:false}));
if(failed.length)process.exitCode=1;
