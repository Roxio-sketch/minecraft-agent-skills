// Four measured diagonal windows. Sources are outside the outer bars (2.0506m).
uniform float FaceHeatStrength, FaceHeatHeight, FaceHeatSpread;
uniform float FaceSparkRate, FaceSparkLife, FaceSparkSize, FaceSparkSpeed;
uniform float AromaStrength, AromaHeight;
uniform float IngredientCount, IngredientSize, IngredientStrength, GatherSpeed;
uniform float EnergyRingRadius, EnergyRingHeight, EnergyRingThickness, EnergyRingStrength, EnergyRingSpeed;
uniform vec3 AromaColor, IngredientColor, EnergyRingColor;

void avWindowEffects(vec3 p,float t,float seed,float power,inout vec3 light,inout float density){
 for(int face=0;face<4;face++){
  float angle=AV_PI*.25+float(face)*AV_PI*.5;
  vec2 normal=vec2(cos(angle),sin(angle)),tangent=vec2(-normal.y,normal.x);
  vec3 source=vec3(normal.x*2.085,1.393125,normal.y*2.085),v=p-source;
  float outwards=dot(v.xz,normal),across=dot(v.xz,tangent);
  // Never create a sheet through the solid back of the window.
  if(outwards<-.025||outwards>2.2||abs(across)>1.4||v.y<-.38||v.y>3.)continue;
  float y=(v.y+.24)/FaceHeatHeight;
  if(y>0.&&y<1.5&&FaceHeatStrength>0.){
   float drift=.10+.22*y,bend=.045*sin(y*6.1-t*FlowSpeed*.6+float(face));
   vec3 q=avWarp(vec3(across-bend,v.y,outwards-drift),t*FlowSpeed,seed+float(face)*7.1);
   float width=FaceHeatSpread*(.75+.75*y);
   float envelope=exp(-q.x*q.x/max(width*width,.001)-q.z*q.z/max(width*width*.72,.001));
   float breakup=smoothstep(.25,.66,avFbm(q*vec3(8.,3.5,8.)-vec3(0,t*FlowSpeed,0)));
   float life=smoothstep(0.,.08,y)*(1.-smoothstep(.6,1.45,y));
   avAdd(light,density,envelope*breakup*life*FaceHeatStrength*power*.38,mix(FireMid,vec3(.52,.50,.45),.8));
  }
  float hy=(v.y+.12)/AromaHeight;
  if(hy>0.&&hy<1.55&&AromaStrength>0.){
   vec3 q=avWarp(vec3(across,v.y,outwards),t*FlowSpeed*.65,seed+float(face)*19.);
   float centerOut=.12+hy*.43,spread=.065+hy*.15;
   float curl=hy*5.9-t*FlowSpeed*.42+float(face)*2.3;
   float distance=length(vec2(q.x-sin(curl)*spread,q.z-centerOut-cos(curl*.87)*spread*.4));
   float width=.025+hy*.034;
   float veil=exp(-distance*distance/(width*width));
   float noise=avFbm(q*vec3(9.,3.1,9.)+vec3(seed,-t*FlowSpeed*.7,13.));
   float soft=exp(-distance*distance/(width*width*8.))*smoothstep(.33,.65,noise)*.15;
   float ends=smoothstep(0.,.09,hy)*(1.-smoothstep(.70,1.5,hy));
   avAdd(light,density,(veil*smoothstep(.23,.57,noise)+soft)*ends*AromaStrength*power,AromaColor);
  }
  if(FaceSparkRate>0.&&FaceSparkSize>0.){
   int count=int(ceil(FaceSparkRate*FaceSparkLife));
   for(int j=0;j<72;j++){
    if(j>=count)break;
    float clock=(t+float(j)/FaceSparkRate)/FaceSparkLife,age=fract(clock)*FaceSparkLife,birth=floor(clock);
    float random=avHash(vec3(float(j)+float(face)*31.,seed,birth));
    float s=age/FaceSparkLife,a=across;
    float cx=(random-.5)*.54+sin(age*4.7+random*19.)*.035*Turbulence;
    float cy=-.17+random*.31+age*(.32+random*.52)-.08*age*age;
    float cz=.025+age*FaceSparkSpeed*(.55+random*.7);
    vec3 delta=vec3(across-cx,v.y-cy,outwards-cz);
    float size=FaceSparkSize*(1.-s*.6),d=length(delta);
    if(d>size*4.)continue;
    float core=exp(-d*d/max(size*size,.00001));
    float tail=exp(-dot(delta/vec3(size,size*2.5,size*1.5),delta/vec3(size,size*2.5,size*1.5)))*.23;
    float life=smoothstep(0.,.06,s)*pow(1.-s,1.35);
    avAdd(light,density,(core*7.+tail)*life*power,avThermal(.78+random*.2));
   }
  }
 }
}

void avGatherEffects(vec3 p,float t,float seed,float gather,float power,inout vec3 light,inout float density){
 // Material lights stay inside the actual vessel, above its .56125m floor.
 if(length(p.xz)<1.32&&p.y>.58&&p.y<2.28){
  vec3 target=AV_MATERIAL+vec3(0,gather*.28,0);
  float breathe=.035*sin(t*GatherSpeed*1.7);
  for(int j=0;j<12;j++){
   if(float(j)>=IngredientCount)break;
   float k=float(j),theta=t*GatherSpeed*.68+k*AV_TAU/max(IngredientCount,1.)+seed*.1;
   float radius=OrbitRadius*(.65+.21*sin(k*2.7+seed))*(1.-gather*.94);
   vec3 center=target+vec3(cos(theta)*radius,(.22*sin(theta*1.3+k)+breathe)*(1.-gather),sin(theta)*radius);
   float size=IngredientSize*(.7+.3*avHash(vec3(k,seed,3.)));
   float d=length(p-center),orb=exp(-d*d/max(size*size,.0001));
   avAdd(light,density,orb*IngredientStrength*power*3.,IngredientColor);
   // A curved 3D intake stream leads each orbiting material towards the centre.
   vec3 delta=p-target;float r=length(delta.xz),s=clamp(r/max(radius,.04),0.,1.);
   float aa=atan(delta.z,delta.x),path=theta-(1.-s)*1.6;
   float ad=atan(sin(aa-path),cos(aa-path));
   float z=delta.y-center.y+target.y;
   float width=max(.012,FilamentWidth*.75),distance=length(vec2(ad*max(r,.08),delta.y-(center.y-target.y)*s-.05*sin(s*AV_PI)));
   float thread=exp(-distance*distance/(width*width))*smoothstep(.03,.12,s)*(1.-smoothstep(.85,1.05,s));
   float flow=.22+.78*smoothstep(.1,.75,avNoise(vec3(s*9.-t*GatherSpeed*2.,k,seed)));
   avAdd(light,density,thread*flow*IngredientStrength*power*.42,mix(IngredientColor,EnergyRingColor,s));
  }
 }
 // Broken, helical currents around the vessel have real depth and changing height.
 vec3 q=avWarp(p,t*FlowSpeed*.38,seed+41.);
 float radius=length(q.xz),theta=atan(q.z,q.x)-t*EnergyRingSpeed*AV_TAU;
 if(abs(radius-EnergyRingRadius)<.42&&abs(q.y-EnergyRingHeight)<.7){
  for(int j=0;j<3;j++){
   float k=float(j),height=EnergyRingHeight+.14*sin(theta*2.+k*2.1)+.09*sin(theta*5.1+k-t*FlowSpeed*.3);
   float rr=EnergyRingRadius+.06*sin(theta*3.+k*2.4),width=EnergyRingThickness*(.7+.3*avNoise(vec3(theta*2.,k,t*.17)));
   float d=length(vec2(radius-rr,q.y-height-k*.10));
   float fragments=smoothstep(.28,.62,avNoise(vec3(theta*2.7-t*EnergyRingSpeed*4.,k*5.1,seed)));
   float flow=.25+.75*smoothstep(-.6,.6,sin(theta*2.7-k*2.1));
   avAdd(light,density,exp(-d*d/max(width*width,.00005))*fragments*flow*EnergyRingStrength*power*1.2,EnergyRingColor);
  }
 }
}
