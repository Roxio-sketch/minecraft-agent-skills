
// Vanilla's scene attachment is display-referred RGB. Explicitly decode/encode
// the approximate sRGB transfer; clipping at the LDR target is a known limit.
vec3 veSRGBDecode(vec3 c){return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c));}
vec3 veSRGBEncode(vec3 c){c=max(c,vec3(0));return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-0.055,step(vec3(0.0031308),c));}
void main(){
    vec3 scene=veSRGBDecode(texture(SceneSampler,screenUV).rgb);
    float depth=texture(DepthSampler,screenUV).r;
    vec4 eyeH=InverseProjection*vec4(0.0,0.0,1.0,0.0);
    vec3 eyeView=abs(eyeH.w)>1e-7?eyeH.xyz/eyeH.w:vec3(0.0);
    vec3 origin=InverseViewRotation*eyeView+InverseViewTranslation;
    vec4 nearH=InverseProjection*vec4(screenUV*2.0-1.0,-1.0,1.0);
    vec3 direction=normalize(InverseViewRotation*(nearH.xyz/nearH.w-eyeView));
    vec4 hitH=InverseProjection*vec4(screenUV*2.0-1.0,depth*2.0-1.0,1.0);
    vec3 surface=InverseViewRotation*(hitH.xyz/hitH.w)+InverseViewTranslation;
    bool hit=depth<0.999999;
    float distanceLimit=hit?length(surface-origin):100000.0;
    // Compute derivatives outside the divergent branch. Single opaque depth
    // cannot provide real material normals or light-ray terrain occlusion.
    vec3 rawNormal=cross(dFdx(surface),dFdy(surface));
    vec3 normal=length(rawNormal)>1e-8?normalize(rawNormal):-direction;
    if(dot(normal,origin-surface)<0.0)normal=-normal;
    if(hit){direction=normalize(surface-origin);scene+=veSurfaceLight(surface,normal);}
    vec4 volume=veIntegrate(origin,direction,distanceLimit);
    if(ValidationProbe==1){fragColor=vec4(volume.a,veDensity(origin+direction*5.0),veDensity(origin+direction*10.0),1.0);return;}
    fragColor=vec4(veSRGBEncode(scene*volume.a+volume.rgb),1.0);
}
