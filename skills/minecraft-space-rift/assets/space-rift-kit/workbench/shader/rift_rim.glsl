// Procedural relief cross-section. No color/normal/displacement textures.
// A view-dependent 2.5D wall, not a watertight extruded mesh or volume solver.
vec4 riftRim(vec2 p,float t,float distance,float hw,out vec4 fringe){
 // Derivatives are evaluated before region branches. Invert the screen-to-plane
 // Jacobian: dFdx(distance) alone is not a local-space normal on a rotated plane.
 vec2 px=dFdx(p),py=dFdy(p);float det=px.x*py.y-px.y*py.x;
 vec2 screenGradient=vec2(dFdx(distance),dFdy(distance));
 vec2 gradient=abs(det)>1e-12?vec2(py.y*screenGradient.x-px.y*screenGradient.y,
    -py.x*screenGradient.x+px.x*screenGradient.y)/det:vec2(1.,0.);
 // The union/left-right medial axis is not differentiable. Bounded metric
 // prevents a discontinuity from projecting a spurious wall across the opening.
 float metric=clamp(length(gradient),.65,2.5);vec2 n=gradient/metric;
 n=normalize(n+vec2(1e-7));vec2 tangent=vec2(-n.y,n.x);
 // Keep relief coverage tied to the continuous distance bound. A per-pixel
 // gradient magnitude would emboss tiny medial-axis discontinuities as spikes.
 // Thickness at sharp corners remains approximate; normals use the Jacobian.
 float d=distance,aa=max(riftFootprint*.75,1e-5);
 float gate=smoothstep(0.,.04,riftEventGrowth())*(1.-smoothstep(.91,1.,u_shrink));
 float width=min(u_rimWidth,max(0.,hw)*.40)*sqrt(max(u_open,0.))*(1.-u_shrink);
 float depth=u_rimDepth*gate*min(1.,width/max(u_rimWidth,1e-5));
 fringe=vec4(0.);
 if(width<aa*.25||depth<.00001||d>width*2.8||d<-width-depth*2.2)return vec4(0.);
 mat2 rotation=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));
 vec2 view=rotation*vec2(u_viewTiltX,u_viewTiltY);
 float facing=dot(n,view),wallWidth=max(aa,width+depth*facing*.65);
 float across=clamp(-d/wallWidth,0.,1.);
 // Mapping to the recessed wall. Tangential coordinates are never divided by
 // u_length/u_width. Stretching a rift exposes fresh procedural material.
 vec2 q=p+view*depth*across;
 float frequency=max(8.,u_frequency*1.7);
 float low=riftValueNoise(q*frequency*.29+vec2(u_seed*.41,t*.08));
 float phase=dot(q,vec2(17.3,5.7))*frequency+low*5.-t*.7;
 float bandLimit=1.-smoothstep(.25,.8,riftFootprint*frequency*18.2);
 float grooves=sin(phase)*bandLimit;
 float crest=.5+.5*grooves;
 vec3 normal=normalize(vec3(n*(depth/max(wallWidth,1e-5))+
    tangent*cos(phase)*bandLimit*u_rimRelief*.65, .55+across));
 vec3 light=normalize(vec3(-.45,.65,1.));
 float diffuse=max(dot(normal,light),0.);
 float spec=pow(max(dot(normal,normalize(light+normalize(vec3(view,1.)))),0.),24.);
 float wall=(1.-smoothstep(wallWidth*.78-aa,wallWidth+aa,-d))*smoothstep(-aa,aa,-d);
 wall*=1.-smoothstep(max(width*2.,aa),max(width*3.,aa*2.),-distance);
 float occlusion=mix(1.,.24,smoothstep(.15,1.,across));
 vec3 violet=mix(u_edgeColor,vec3(.35,.09,.75),.45);
 vec3 pigment=mix(violet*.14,u_edgeColor*.55,crest*.65+low*.2);
 vec3 col=pigment*(.30+diffuse*1.5)*occlusion;
 col+=u_edgeColor*pow(crest,12.)*bandLimit*.20*u_rimRelief*occlusion;
 col+=vec3(.48,.72,1.)*spec*.55*occlusion;
 // A narrow back lip and a dark recess separate wall from the distant world.
 float backLip=exp(-abs(d+wallWidth*.83)/max(aa,wallWidth*.035));
 col+=violet*backLip*.32;
 // Distinct suspended strands, depth ordered by radius and brightness. Each
 // samples q in physical local units; analytically filtered, no blurred mask.
 float outer=1.-smoothstep(-aa,aa,-d);
 for(int i=0;i<6;i++){
  if(float(i)>=2.+u_quality*2.)break;
  float k=float(i),z=(k+1.)/7.;
  vec2 field=q+n*depth*z+vec2(k*7.1,u_seed*.23);
  float flow=riftValueNoise(field*frequency*.8+vec2(-t*.19,t*.11));
  float target=width*(.12+z*1.35)+(flow-.5)*width*.65;
  float strandWidth=.0007+z*.0008;
  float line=exp(-pow((d-target)/max(strandWidth,aa),2.))*min(1.,strandWidth/aa);
  float packet=.15+.85*pow(.5+.5*sin(dot(field,vec2(11.,3.))*3.-t*(1.+z)),3.);
  vec3 tint=mix(u_edgeColor,vec3(.47,.13,1.),z);
  float opacity=line*packet*outer*(1.-z*.65)*u_corona;
  fringe.rgb+=tint*opacity*(1.2-z*.4);fringe.a+=opacity*.65;
 }
 fringe.a=clamp(fringe.a,0.,1.);
 return vec4(col*wall,wall);
}
