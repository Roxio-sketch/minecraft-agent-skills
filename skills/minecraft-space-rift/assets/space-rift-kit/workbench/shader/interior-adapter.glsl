// Actual existing sky algorithms are linked by build.py after this shared geometry module.
vec3 riftCosmicInterior(vec2 p,float t){
 vec3 d=normalize(vec3(p.y*.62,p.x*.84,1.));float clustering;
 vec3 col=riftInterior_skyGalaxy(d,clustering)+riftInterior_skyNebula(d);
 col+=riftInterior_skyStars(d,95.,3.,clustering);
 if(u_quality>.5&&u_layers>1.5)col+=riftInterior_skyStars(d,170.,7.,clustering)*.65;
 // Logarithmic spiral galaxies add resolved structures within the existing sky field.
 for(int i=0;i<3;i++){
  if(float(i)>u_quality+.5||float(i)>=u_layers)break;
  float fi=float(i);vec2 center=vec2((fi-1.)*.035,(fi-1.)*u_length*.28);
  vec2 q=(p-center)/vec2(.12+.035*fi,.21+.04*fi);
  float a=.4+fi*1.1,ca=cos(a),sa=sin(a);q=mat2(ca,-sa,sa,ca)*q;
  float radius=length(q),angle=atan(q.y,q.x),twist=angle*3.-log(radius+.08)*5.5-t*u_insideSpeed*.07;
  float noise=riftFbm(q*3.1+u_seed,22.);
  float arms=pow(.5+.5*sin(twist+noise*2.7),7.)*exp(-radius*1.4);
  float dust=pow(.5+.5*sin(twist+.6),3.)*exp(-radius*1.5);
  col+=mix(vec3(.15,.3,1.1),vec3(.7,.24,.66),noise)*(arms*.9+dust*.1)*u_galaxies;
  col+=vec3(1.5,.62,.24)*exp(-radius*9.)*u_galaxies;
 }
 return col*(.6+u_insideColor*3.5);
}
