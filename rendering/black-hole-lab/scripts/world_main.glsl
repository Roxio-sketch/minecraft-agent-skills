vec3 haloEmission(vec3 q){
    float r=length(q.xz),y=abs(q.y);
    float envelope=smoothstep(uCore+.12,uInner+.1,r)*(1.0-smoothstep(uOuter,uOuter*1.38,r));
    float ring=exp(-pow((r-uInner-.12)/.11,2.0)-pow(y/.14,2.0));
    float disk=exp(-pow(y/.26,2.0))*exp(-.9*max(0.0,r-uInner));
    float aura=exp(-pow(y/(.46+.06*r),2.0))*exp(-.32*r*r);
    vec3 warm=mix(uCool,uHot,exp(-.75*max(0.0,r-uInner)));
    return HaloStrength*envelope*(uHot*ring*HaloRing*1.6+warm*disk*HaloDisk*.22+uCool*aura*HaloAura*.20);
}
vec3 sceneLinear(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));}
vec3 sceneDisplay(vec3 c){return mix(c*12.92,1.055*pow(max(c,vec3(0)),vec3(1.0/2.4))-.055,step(vec3(.0031308),c));}
void main(){
    vec3 original=texture(Sampler0,texCoord).rgb;
    vec3 rd=normalize(scenePosition(texCoord,1.0));
    vec3 ro=-CenterRelative/WorldScale;
    vec2 range=bhSphere(ro,rd,uOuter*1.5);
    if(range.y<=0.0){fragColor=vec4(original,1);return;}
    float depth=texture(Sampler1,texCoord).r;
    float foreground=depth<.999999?length(scenePosition(texCoord,depth)):1e8;
    if(foreground<max(0.0,range.x)*WorldScale+.02){fragColor=vec4(original,1);return;}
    BHResult hit=bhTrace(ro,rd);
    vec2 lensUv=texCoord;
    vec4 projected;
    if(hit.blocked>.5)projected=ViewProjection*vec4(CenterRelative+hit.position*WorldScale,1);
    else projected=ViewProjection*vec4(hit.direction,0);
    if(projected.w>0.0001){
        vec2 candidate=projected.xy/projected.w*.5+.5;
        // Fade where the finite ray field terminates and where screen data ends.
        float impact=length(cross(ro,rd));
        float fieldFade=1.0-smoothstep(uOuter*.92,uOuter*1.5,impact);
        float edge=min(min(candidate.x,candidate.y),min(1-candidate.x,1-candidate.y));
        float validity=smoothstep(.005,.065,edge);
        lensUv=mix(texCoord,candidate,clamp(LensStrength*fieldFade*validity,0,1));
        if(!onScreen(lensUv))lensUv=texCoord;
        float sampleDepth=texture(Sampler1,lensUv).r;
        // Never drag a visible object in front of the effect into its background.
        if(sampleDepth<.999999){
            vec3 source=scenePosition(lensUv,sampleDepth);
            vec2 sourceRange=bhSphere(ro,normalize(source),uOuter*1.5);
            if(sourceRange.y>0.0&&length(source)<max(0.0,sourceRange.x)*WorldScale+.08)lensUv=texCoord;
        }
    }
    vec3 background=texture(Sampler0,lensUv).rgb;
    // Tone map only the emitted field; do not apply exposure to the existing world.
    vec3 emitted=1.0-exp(-max(hit.light,vec3(0))*uExposure);
    fragColor=vec4(sceneDisplay(emitted+hit.transmission*sceneLinear(background)),1.0);
}
