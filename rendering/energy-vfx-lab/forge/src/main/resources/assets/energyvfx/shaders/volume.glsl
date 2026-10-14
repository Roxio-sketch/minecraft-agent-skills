out vec4 volumeColor;
out vec4 emissionColor;
#ifdef ENERGY_PACKET_BOUNDS
uniform int puffBoundsEnabled;
uniform vec4 puffScreenBounds[32];
#endif
// Hard support used by auraSample, slightly enlarged for floating-point safety.
vec2 puffSupportHit(int parcel,vec3 ro,vec3 rd){
 vec3 flow=puffFlow[parcel].xyz,axis=length(flow)>.001?normalize(flow):vec3(0,1,0);
 vec3 q=(ro-puffs[parcel].xyz)/max(.01,puffs[parcel].w),v=rd/max(.01,puffs[parcel].w);
 q+=axis*dot(q,axis)*(1./STRETCH-1.);v+=axis*dot(v,axis)*(1./STRETCH-1.);
 float a=dot(v,v),b=dot(q,v),c=dot(q,q)-6.275025,disc=b*b-a*c;
 if(disc<0.)return vec2(1,-1);float root=sqrt(max(0.,disc));return vec2((-b-root)/a,(-b+root)/a);
}
void main(){
 vec2 uv=gl_FragCoord.xy/viewport;vec3 rd=ray(uv),life=lifecycle();
 float bound=RADIUS*life.y*max(STRETCH,1.)*max(1.5,u[27]+u[28]*4.);
 vec2 span=u[39]>.5?boxHit(camera,rd,auraMin,auraMax):boxHit(camera,rd,vec3(-bound),vec3(bound));
 vec4 bg=scene(uv);float first=max(span.x,0.),last=min(span.y,bg.a);
#ifndef ENERGY_DIRECT_PARCELS
 auraCandidateCount=0;auraSingle=-1;
 if(u[39]>.5){float nearest=last,farthest=first;for(int j=0;j<32;j++){if(j>=puffCount)break;
#ifdef ENERGY_PACKET_BOUNDS
 if(puffBoundsEnabled!=0){vec4 r=puffScreenBounds[j];if(uv.x<r.x||uv.y<r.y||uv.x>=r.z||uv.y>=r.w)continue;}
#endif
 vec2 support=puffSupportHit(j,camera,rd);if(support.y<=max(0.,support.x)||support.x>=bg.a)continue;float extent=puffs[j].w*2.65*max(1.,STRETCH);vec2 hit=boxHit(camera,rd,puffs[j].xyz-vec3(extent),puffs[j].xyz+vec3(extent));if(hit.y>max(first,hit.x)&&hit.x<last){auraIndices[auraCandidateCount++]=j;nearest=min(nearest,max(first,hit.x));farthest=max(farthest,min(last,hit.y));}}first=nearest;last=farthest;}
#endif
 vec3 light=vec3(0);float trans=1.;float distortionWeight=0.;
 if(u[39]>.5&&last>first&&life.x>.0001){
  // Give every intersected smoke parcel its own samples; empty space between
  // drifting parcels does not consume the thin-sheet sampling budget.
#ifdef ENERGY_DIRECT_PARCELS
  // All lanes visit the same parcel index, avoiding a private candidate array
  // and divergent cache layers. Preserve parcel order and the original grid.
  for(int parcel=0;parcel<32;parcel++){
   if(parcel>=puffCount)break;
#ifdef ENERGY_PACKET_BOUNDS
   if(puffBoundsEnabled!=0){vec4 r=puffScreenBounds[parcel];if(uv.x<r.x||uv.y<r.y||uv.x>=r.z||uv.y>=r.w)continue;}
#endif
   vec2 support=puffSupportHit(parcel,camera,rd);
   if(support.y<=max(0.,support.x)||support.x>=bg.a)continue;
#else
  for(int k=0;k<32;k++){
   if(k>=auraCandidateCount)break;int parcel=auraIndices[k];auraSingle=parcel;
#endif
   float extent=puffs[parcel].w*2.65*max(1.,STRETCH);vec2 hit=boxHit(camera,rd,puffs[parcel].xyz-vec3(extent),puffs[parcel].xyz+vec3(extent));
#ifdef ENERGY_DIRECT_PARCELS
   if(!(hit.y>max(first,hit.x)&&hit.x<last))continue;
#endif
   float begin=max(hit.x,0.),end=min(hit.y,bg.a);if(end<=begin)continue;
   float samples=floor(clamp(u[36]*.5,24.,64.)+.5);float ds=(end-begin)/samples;smokeStep=ds;
   // Preserve the original AABB sample grid; skip only whole empty segments.
#ifndef ENERGY_DIRECT_PARCELS
   vec2 support=puffSupportHit(parcel,camera,rd);
#endif
   int startStep=int(clamp(floor((max(begin,support.x)-begin)/ds),0.,samples-1.));
   int stopStep=int(clamp(ceil((min(end,support.y)-begin)/ds),1.,samples));
   vec4 a=auraSample(camera+rd*(begin+float(startStep)*ds),parcel,life);
   for(int step=0;step<64;step++){
    if(step<startStep)continue;if(step>=stopStep)break;
    vec4 b=auraSample(camera+rd*(begin+(float(step)+1.)*ds),parcel,life);
    float density=smokeSegment(a,b),mass=density*ds,opacity=1.-exp(-mass*u[20]);
    vec3 hue=mix(colorB,colorA,clamp(density/max(.01,u[12])*.98+.2,0.,1.));
    light+=trans*hue*mass*u[18]*.5;trans*=1.-opacity;a=b;
   }
  }
 }
 if(u[39]<.5&&last>first&&life.x>.0001){
 float ds=(last-first)/u[36];smokeStep=ds;float jitter=hash3(vec3(floor(gl_FragCoord.xy),u[35]));
 for(int i=0;i<128;i++){if(float(i)>=u[36]||trans<.006)break;float travel=first+(float(i)+jitter)*ds;vec3 p=camera+rd*travel;vec3 f=energyField(p,life);float opacity=1.-exp(-f.x*ds*u[20]);
 vec3 hue=mix(colorB,colorA,clamp(f.y*.7+.2,0.,1.));vec3 emitted=(hue+vec3(f.z))*f.x*u[18];
 light+=trans*emitted*ds*.5;trans*=1.-opacity;distortionWeight+=f.x*ds*.05;
 }
 }
 // Background is composed at full resolution, separate from the volume resolution.
 volumeColor=vec4(pow(1.-exp(-light*u[25]),vec3(1./2.2)),trans);
 emissionColor=vec4(max(light*u[25]-vec3(u[23]),vec3(0)),1);
}
