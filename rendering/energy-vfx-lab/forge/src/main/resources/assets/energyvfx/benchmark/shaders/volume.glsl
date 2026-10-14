out vec4 volumeColor;
out vec4 emissionColor;
void main(){
 vec2 uv=gl_FragCoord.xy/viewport;vec3 rd=ray(uv),life=lifecycle();
 float bound=RADIUS*life.y*max(STRETCH,1.)*max(1.5,u[27]+u[28]*4.);
 vec2 span=u[39]>.5?boxHit(camera,rd,auraMin,auraMax):boxHit(camera,rd,vec3(-bound),vec3(bound));
 vec4 bg=scene(uv);float first=max(span.x,0.),last=min(span.y,bg.a);
 auraCandidateCount=0;auraSingle=-1;
 if(u[39]>.5){float nearest=last,farthest=first;for(int j=0;j<32;j++){if(j>=puffCount)break;float extent=puffs[j].w*2.65*max(1.,STRETCH);vec2 hit=boxHit(camera,rd,puffs[j].xyz-vec3(extent),puffs[j].xyz+vec3(extent));if(hit.y>max(first,hit.x)&&hit.x<last){auraIndices[auraCandidateCount++]=j;nearest=min(nearest,max(first,hit.x));farthest=max(farthest,min(last,hit.y));}}first=nearest;last=farthest;}
 vec3 light=vec3(0);float trans=1.;float distortionWeight=0.;
 if(u[39]>.5&&last>first&&life.x>.0001){
  // Give every intersected smoke parcel its own samples; empty space between
  // drifting parcels does not consume the thin-sheet sampling budget.
  for(int k=0;k<32;k++){
   if(k>=auraCandidateCount)break;int parcel=auraIndices[k];auraSingle=parcel;
   float extent=puffs[parcel].w*2.65*max(1.,STRETCH);vec2 hit=boxHit(camera,rd,puffs[parcel].xyz-vec3(extent),puffs[parcel].xyz+vec3(extent));
   float begin=max(hit.x,0.),end=min(hit.y,bg.a);if(end<=begin)continue;
   float samples=floor(clamp(u[36]*.5,24.,64.)+.5);float ds=(end-begin)/samples;smokeStep=ds;
   vec4 a=auraSample(camera+rd*begin,parcel,life);
   for(int step=0;step<64;step++){
    if(float(step)>=samples)break;
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
