// Independently implemented style extension. Research links do not imply copied engine code.
// Inputs/outputs are local-plane units, with the same lifetime and aperture contracts as core.
float riftSegStyle(vec2 p,vec2 a,vec2 b);
float riftStyleCenter(float y,float t){
 float s=u_style,clock=t*u_edgeMotion,scale=u_styleScale,offset=0.;
 if(s>4.5&&s<5.5){
  float row=floor(y*18.*scale),phase=clock*2.;
  float j=mix(riftHash11(row+floor(phase)+u_seed),riftHash11(row+ceil(phase)+u_seed),smoothstep(0.,1.,fract(phase)));
  offset=(j-.5)*.025;
 }else if(s>5.5&&s<6.5){offset=sin(y*5.*scale+clock*.7)*.055+sin(y*9.-clock*.3)*.018;}
 else if(s>7.5){offset=sin(y*8.*scale-clock)*.025;}
 return offset*u_styleAmount;
}
float riftStyleWidth(float y,float side,float t){
 float s=u_style,clock=t*u_edgeMotion,scale=u_styleScale,w=1.;
 if(s>1.5&&s<2.5)w=.85+.25*sin(y*11.*scale+side*1.7-clock*1.3);
 else if(s>2.5&&s<3.5){float segment=fract(y*5.*scale+side*.27);w=.7+.45*abs(segment*2.-1.);}
 else if(s>3.5&&s<4.5)w=.87+.18*cos(y*17.*scale+side*.9);
 else if(s>4.5&&s<5.5)w=.94+.12*riftHash11(floor(y*18.*scale)+side*7.+u_seed);
 else if(s>5.5&&s<6.5)w=.80+.32*riftValueNoise(vec2(y*6.*scale-clock*.32,side*13.+u_seed));
 else if(s>7.5)w=.95+.12*sin(y*12.*scale-clock*.8);
 return mix(1.,w,u_styleAmount);
}
float riftStyledDistance(vec2 p,float cx,float d){
 // Dual rift keeps an actual strip of original space between both independent openings.
 if(u_style>7.5&&u_styleAmount>0.){float divider=u_width*u_open*riftEventExpansion()*(1.-u_shrink)*.22*u_styleAmount;d=max(d,divider-abs(p.x-cx));}
 return d;
}
float riftStroke(vec2 p,vec2 a,vec2 b,float width){return exp(-riftSegStyle(p,a,b)/max(width,riftFootprint*.7))*min(1.,width/max(riftFootprint*.7,1e-7));}
float riftSegStyle(vec2 p,vec2 a,vec2 b){vec2 ab=b-a;float h=clamp(dot(p-a,ab)/max(dot(ab,ab),1e-8),0.,1.);return length(p-a-h*ab);}
float riftCellSeam(vec2 p){
 // Approximate F2-F1 boundary, used only in the finite edge/interior region, not as the main opening.
 vec2 cell=floor(p),f=fract(p);float first=100.,second=100.;
 for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
  vec2 id=cell+vec2(i,j),delta=vec2(i,j)+.15+.7*riftHash22(id+u_seed)-f;
  float d=dot(delta,delta);if(d<first){second=first;first=d;}else second=min(second,d);
 }return max(0.,sqrt(second)-sqrt(first));
}
vec4 riftStyleSurface(vec2 p,float t,float d,float cx,float hw,float edge,float opening){
 float style=u_style,clock=t*u_edgeMotion,scale=u_styleScale,outer=1.-opening;
 vec3 light=vec3(0.);float coverage=0.;
 float band=exp(-max(d,0.)/.13)*outer;
 if(style<.5||abs(p.y)>u_length*.5*riftEventGrowth()+.4)return vec4(0.);
 if(style<1.5){
  for(int i=0;i<3;i++){
   float k=float(i),wave=riftValueNoise(vec2(p.y*23.*scale+u_seed+k*17.,clock*.75+k))-.5;
   float target=.018+k*.024+wave*.045;
   float bolt=exp(-abs(d-target)/max(.0015,riftFootprint*.8));
   float packet=.18+.82*pow(.5+.5*sin(p.y*31.-clock*(4.+k)+k*7.),5.);
   light+=mix(u_edgeColor,vec3(.8,.92,1.),.55)*bolt*packet*outer;coverage+=bolt*packet*outer*.65;
  }
 }else if(style<2.5){
  float n=riftFbm(vec2(p.y*17.*scale-clock*.3,d*22.-clock*1.3+u_seed),22.*scale);
  float flame=band*pow(max(n,0.),2.)*(.5+.5*sin(d*55.-clock*2.+n*8.));
  light+=mix(u_edgeColor,vec3(.5,.04,.15),smoothstep(0.,.13,d))*flame*2.;coverage+=flame;
  vec2 q=vec2((p.x-cx)*30.,p.y*24.-clock*.55),id=floor(q);vec2 r=fract(q)-.5;
  float ember=exp(-dot(r,r)*200.)*step(.91,riftHash21(id+u_seed))*band;
  light+=vec3(1.,.24,.03)*ember*2.;coverage+=ember;
 }else if(style<3.5){
  float seam=riftCellSeam(p*16.*scale);
  float lines=exp(-seam/max(.018,riftFootprint*16.*scale))*band;
  vec3 spectrum=.5+.5*cos(vec3(0.,2.1,4.2)+p.y*6.+d*35.);
  light+=(spectrum*edge*.85+u_edgeColor*lines*.65);coverage+=lines*.32;
 }else if(style<4.5){
  float spacing=.16/scale,row=floor((p.y+.5*spacing)/spacing),localY=(p.y-row*spacing)/spacing;
  vec2 glyph=vec2((abs(p.x-cx)-hw-.065)/spacing,localY);
  float a=riftHash11(row+u_seed),sigil=0.;
  sigil+=riftStroke(glyph,vec2(-.16,-.33),vec2(-.16,.33),.014);
  sigil+=riftStroke(glyph,vec2(-.16,.20),vec2(.18,a>.5?.34:-.04),.014);
  sigil+=riftStroke(glyph,vec2(-.16,-.03),vec2(.15,-.26),.014);
  sigil+=riftStroke(glyph,vec2(.15,-.26),vec2(.15,.22),.011)*step(.4,a);
  float cadence=.4+.6*pow(.5+.5*sin(row*.7-clock*1.7),3.);
  light+=u_edgeColor*sigil*cadence;coverage+=clamp(sigil,0.,1.)*.7;
 }else if(style<5.5){
  float row=floor(p.y*38.*scale),gate=step(.64,riftHash11(row+u_seed));
  float scan=exp(-pow(fract(p.y*38.*scale-clock*.3)-.5,2.)/max(.001,riftFootprint*8.));
  light+=mix(u_edgeColor,vec3(.9,.15,.75),gate)*(scan*band*.32+edge*gate*.8);
  coverage+=scan*band*.18;
 }else if(style<6.5){
  float flow=riftFbm(vec2(p.y*7.*scale-clock*.12,d*18.+sin(p.y*5.+clock)*.9),18.*scale);
  float strokes=pow(max(.0,1.-abs(flow*2.-.7)),7.);
  light+=u_edgeColor*band*strokes*.22;coverage+=band*strokes*.55;
 }else if(style<7.5){
  float wavelength=.058/scale,phase=fract(clock*.18);
  for(int i=0;i<4;i++){
   float target=(float(i)+phase)*wavelength+.024;
   float ring=exp(-abs(d-target)/max(.0013,riftFootprint))*(1.-float(i)/4.5)*outer;
   light+=u_edgeColor*ring*.5;coverage+=ring*.36;
  }
 }else{
  float twist=.5+.5*sin(p.y*22.*scale-clock*2.5);
  vec3 tint=mix(u_edgeColor,vec3(.22,.65,1.),step(0.,p.x-cx));
  light+=tint*(edge*twist*.7+band*pow(twist,12.)*.4);coverage+=band*pow(twist,12.)*.2;
 }
 return vec4(light*u_styleAmount,clamp(coverage*u_styleAmount,0.,1.));
}
vec2 riftStyleWarp(vec2 p,float t,float d,float cx){
 float outer=(1.-smoothstep(-.005,.003,-d))*exp(-max(d,0.)/.16),clock=t*u_edgeMotion;
 vec2 v=vec2(0.);
 if(u_style>4.5&&u_style<5.5){float row=floor(p.y*38.*u_styleScale);v.x=(riftHash11(row+u_seed)-.5)*.065*(.5+.5*sin(clock*3.+row));}
 if(u_style>6.5&&u_style<7.5)v=normalize(vec2(p.x-cx+.0001,.02))*sin(d*100.-clock*4.)*.018;
 return v*outer*u_styleAmount;
}
vec3 riftStyleInteriorRaw(vec2 p,float t,vec3 base){
 float clock=t*u_insideSpeed;
 if(u_style>1.5&&u_style<2.5)return base*.15+u_insideColor*pow(riftValueNoise(p*13.+vec2(0.,-clock)),4.);
 if(u_style>2.5&&u_style<3.5){float seam=riftCellSeam(p*12.*u_styleScale);float facets=riftHash21(floor(p*9.));return base*.5+u_insideColor*(.12+facets*.28)+u_edgeColor*exp(-seam/.02)*.12;}
 if(u_style>4.5&&u_style<5.5){float scan=pow(.5+.5*sin(p.y*180.-clock*3.),5.);return base*.25+u_insideColor*.2+u_edgeColor*scan*.07;}
 if(u_style>5.5&&u_style<6.5){float ink=riftFbm(p*8.+vec2(clock*.06,0.),8.);return u_insideColor*(.08+pow(ink,3.)*.9);}
 return base;
}

vec3 riftStyleInterior(vec2 p,float t,vec3 base){return mix(base,riftStyleInteriorRaw(p,t,base),u_styleAmount);}
