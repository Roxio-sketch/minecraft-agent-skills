// Focused, clean horizontal rift. Reuses the cached spine, lifecycle, interior
// sky functions, adapter and shared parameter schema. No peripheral particles.
float horizonFbm(vec2 p,float alpha,float scale){
 float value=0.,amplitude=.5;
 for(int i=0;i<7;i++){
  if(float(i)>=min(u_detail,3.+u_quality*2.))break;
  vec2 gradient;float n=psrdnoise(p,vec2(0.),alpha,gradient);
  float lod=1.-smoothstep(.22,.62,riftFootprint*scale);
  value+=amplitude*n*lod;
  p=mat2(.8,-.6,.6,.8)*p*2.03+vec2(13.1,7.9);scale*=2.03;amplitude*=.43;
 }return value;
}
float riftHorizonMain(vec2 p,float t,out float cx,out float hw){
 float growth=riftEventGrowth(),halfL=max(.00001,u_length*.5*growth*(1.-u_shrink));
 // The default +pi/2 rotation maps positive local y to screen-left.
 float centerY=u_length*.5-halfL;
 float y=clamp(p.y,centerY-halfL,centerY+halfL),s=(y-centerY)/halfL;
 cx=riftSpine(y)*.20+sin(y*4.7+u_seed)*u_bend*.055;
 cx+=sin(y*5.1+t*.4)*u_dynamic*.008;
 float side=p.x<cx?-1.:1.;
 // Independent sides. Domain warp is static in time; seed does not reshuffle.
 vec2 base=vec2(y*u_frequency*.20,side*31.7+u_seed*.137),g;
 float warp=psrdnoise(base*.32+19.,vec2(0.),0.,g);
 float coarse=horizonFbm(base+g*u_domainWarp*.33,0.,u_frequency*.20);
 float micro=horizonFbm(vec2(y*u_frequency*2.4,side*19.+u_seed*.31),0.,u_frequency*2.4);
 float taper=pow(max(0.,1.-s*s),u_tipPower);
 float asym=1.+u_roughness*(coarse*.32+micro*.025);
 float tongues=0.;
 for(int i=0;i<5;i++){
  float k=float(i)+u_seed*.37+side*17.;float anchor=(riftHash11(k+7.7)-.5)*1.6;
  float span=mix(.07,.21,riftHash11(k+21.));float v=(s-anchor)/span;
  tongues+=pow(max(0.,1.-abs(v)/(v<0.?.65:1.45)),1.8)*mix(.12,.48,riftHash11(k+31.));
 }
 float localOpen=1.-u_localClose*exp(-pow((y/u_length-.13)*9.,2.));
 hw=u_width*(1.+u_mode*.55)*riftEventExpansion()*u_open*(1.-u_shrink)*taper*max(.28,asym+tongues*u_roughness)*localOpen;
 vec2 ds=vec2(abs(p.x-cx)-hw,abs(p.y-centerY)-halfL);
 return length(max(ds,vec2(0.)))+min(max(ds.x,ds.y),0.);
}
vec3 riftHorizonInterior(vec2 p,float t){
 // Reuse the project's directional sky and three-dimensional nebula fields.
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 viewLocal=rotation*vec2(u_viewTiltX,u_viewTiltY),view=viewLocal.yx,uv=vec2(p.y,p.x);
 uv+=view*.10*u_interiorDepth;
 vec3 dir=normalize(vec3(uv*.63,1.2*u_interiorDepth));float clustering;
 float footprint=riftFootprint;riftFootprint*=.63/max(.24,1.2*u_interiorDepth);
 vec3 sky=riftInterior_skyGalaxy(dir,clustering)*.26+riftInterior_skyNebula(dir)*.10;
 if(u_starDensity>0.){
  sky+=riftInterior_skyStars(dir,105.*sqrt(u_starDensity),3.,clustering)*.85;
  if(u_quality>.5&&u_layers>1.5)sky+=riftInterior_skyStars(dir,177.*sqrt(u_starDensity),7.,clustering)*.50;
  if(u_quality>1.5&&u_layers>2.5)sky+=riftInterior_skyStars(dir,265.*sqrt(u_starDensity),11.,clustering)*.35;
 }
 riftFootprint=footprint;
 if(u_starDensity>0.)sky+=riftStars(uv+u_seed*.113,75.*sqrt(u_starDensity))*vec3(.48,.60,1.4)*1.8;
 // A resolved inclined spiral, independent of aperture dimensions, not three
 // equal repeating vortex stamps. Central angle is suppressed as radius→0.
 vec2 q=(uv-vec2(u_galaxyX,u_galaxyY))/max(.1,u_galaxyScale);
 q=mat2(.94,-.342,.342,.94)*q;q.y*=2.5;
 float radius=length(q),angle=atan(q.y,q.x),safeRadius=max(radius,.012);
 float phase=angle*3.-log(safeRadius)*5.3-t*u_insideSpeed*.11;
 float angularGate=smoothstep(.015,.12,radius);
 float clouds=horizonFbm(q*7.+u_seed*.017,t*u_insideSpeed*.02,18.);
 float spiral=pow(max(0.,.5+.5*cos(phase+clouds*1.1)),3.5)*angularGate;
 float envelope=exp(-radius*1.6)*(1.-smoothstep(1.8,3.4,radius));
 vec3 arms=mix(vec3(.10,.22,.85),vec3(.66,.14,.72),.5+.5*clouds);
 sky+=arms*(spiral*1.4+.045)*envelope*u_galaxies;
 sky+=vec3(1.6,.95,.72)*exp(-radius*19.)*u_galaxies;
 sky+=vec3(.018,.025,.075);
 return sky*(.7+u_insideColor*2.)*u_interiorBrightness;
}
vec3 horizonEdgeLight(vec2 p,float t,float d,float hw,out float coverage){
 float aa=max(riftFootprint*.7,.00001),reach=min(u_edgeReach,max(hw,.001)*.7+.002);
 vec2 g;vec2 flowP=vec2(p.y,p.x)*u_frequency*1.3+u_seed*.073;
 float wave=psrdnoise(flowP,vec2(0.),t*u_edgeMotion*.4,g);
 float flowLod=1.-smoothstep(.25,.7,riftFootprint*u_frequency*1.3);wave*=flowLod;g*=flowLod;
 // Two-level gradient advection, not a radial UV division with a center pinch.
 vec2 g2;float fine=psrdnoise(flowP*2.4+g*.35,vec2(0.),-t*u_edgeMotion*.27,g2);
 float freq=u_frequency*3.12,lod=1.-smoothstep(.25,.7,riftFootprint*freq);
 fine*=lod;
 float width=max(min(u_edgeThickness,reach*.22),.00001);
 float lip=exp(-abs(d)/max(width,aa))*min(1.,width/aa);
 vec3 color=mix(vec3(.17,.36,1.),u_edgeColor,.5)*lip;
 color+=vec3(.48,.78,1.)*lip*.65;
 coverage=lip*.8;
 for(int i=0;i<4;i++){
  if(float(i)>=2.+u_quality)break;
  float k=float(i),target=reach*(.18+k*.19)+(wave*.12+fine*.05)*reach;
  float strand=.00055+k*.00022;
  float line=exp(-pow((d-target)/max(aa,strand),2.))*min(1.,strand/aa);
  float pulse=.2+.8*pow(.5+.5*sin(p.y*9.+k*3.-t*u_edgeMotion*1.5+wave*3.),3.);
  color+=mix(vec3(.16,.36,1.),vec3(.61,.10,1.),k/3.)*line*pulse*u_filaments;
  coverage+=line*pulse*.5*u_filaments;
 }
 float corona=exp(-abs(d-reach*.28)/max(reach*.3,.0001))*(.35+.65*wave*wave)*u_corona*.17;
 float bounded=1.-smoothstep(reach*.9,reach*1.45,max(d,0.));
 color+=mix(vec3(.12,.21,.8),vec3(.54,.08,.9),.5+.5*wave)*corona;
 coverage=clamp((coverage+corona*.4)*bounded,0.,1.);
 return color*bounded;
}
RiftSample riftHorizonSurface(vec2 p,float t,float d,float cx,float hw,float opening,float alive){
 RiftSample r;r.distance=d;r.opening=opening;r.warp=vec2(0.);r.interiorVisibility=opening;r.surface=vec4(0.);
 float coverage;vec3 energy=horizonEdgeLight(p,t,d,hw,coverage);
 float aa=max(riftFootprint,.00001),wallWidth=min(u_rimWidth,hw*.35);
 float inner=1.-smoothstep(-wallWidth-aa,-aa,d);
 // A narrow shadowed inner lip. No heavy metallic extrusion or hanging spikes.
 float wall=opening*(1.-inner)*smoothstep(0.,.008,u_rimDepth);
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 interiorP=p+rotation*vec2(u_viewTiltX,u_viewTiltY)*u_rimDepth*wall;
 vec3 interior=vec3(0.);if(opening>.0001)interior=riftInterior(interiorP,t);
 float shaded=clamp(.5-u_rimRelief*.3,.08,.5);
 interior*=mix(1.,shaded,wall);
 r.interiorVisibility=opening*(1.-wall*(1.-shaded));
 float life=u_opacity*alive;
 vec3 col=interior*opening+energy*u_glow;
 r.surface=vec4((1.-exp(-max(col,vec3(0.))*1.2))*life,clamp(opening+coverage*.7,0.,1.)*life);
 float reach=max(.004,u_edgeReach),field=exp(-abs(d)/(reach*.35))*(1.-opening);
 field*=1.-smoothstep(reach*.9,reach*1.45,max(d,0.));
 float side=sign(p.x-cx);
 r.warp=vec2(side,sin(p.y*7.+t*.3)*.08)*.013*u_distortion*field*life;
 return r;
}
vec3 riftHorizonEdgeDebug(vec2 p,float t){float cx,hw,d=riftHorizonMain(p,t,cx,hw),coverage;return horizonEdgeLight(p,t,d,hw,coverage)*u_glow;}
