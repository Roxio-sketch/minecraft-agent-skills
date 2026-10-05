vec3 haloEmission(vec3 q){
    if(HaloStrength<=0.0001)return vec3(0.0);

    float r=length(q.xz),y=abs(q.y);
    // The slider scales the original inner rim about the core. Keep the
    // centre inside the traced volume even for a very narrow accretion disk.
    float ringRadius=min(uOuter*1.35,
        uCore+(uInner+.12-uCore)*uHaloRadius);
    float ringWidth=max(uHaloWidth,.001);
    // A point-sampled volume can miss an extremely thin ring. Widen its
    // reconstruction footprint without increasing its integrated radiance.
    float stepFootprint=.35*(4.5*uOuter/float(uSteps));
    // Critical rays magnify a very small world-space ring into a subpixel
    // fringe.  Include the fragment footprint in the reconstruction width so
    // that the inner edge remains continuous at low quality and high bend.
    float pixelFootprint=max(fwidth(r),.0015);
    float effectiveWidth=sqrt(ringWidth*ringWidth+stepFootprint*stepFootprint
        +.35*pixelFootprint*pixelFootprint);
    float ringProfile=exp(-pow(abs(r-ringRadius)/effectiveWidth,
        max(uHaloSharpness,.01)))*(ringWidth/effectiveWidth);
    float edgeFootprint=max(.012,1.5*pixelFootprint);
    // Let the volume fade across the capture surface instead of terminating
    // on one hard march boundary.  A small inner overlap is hidden by the
    // black core but keeps the lensed rim continuous at its silhouette.
    float ringEnvelope=smoothstep(uCore-.04-edgeFootprint,uCore+.11+edgeFootprint,r)
        *(1.0-smoothstep(uOuter*1.3,uOuter*1.5,r));
    float ring=ringProfile*exp(-pow(y/.14,2.0))*ringEnvelope;
    float gradientPosition=smoothstep(0.0,1.0,
        clamp(.5+.5*(r-ringRadius)/effectiveWidth,0.0,1.0));
    vec3 ringGradient=mix(uHaloGradientStart,uHaloGradientEnd,gradientPosition);
    vec3 ringColor=mix(uHot,ringGradient,clamp(uHaloGradientMix,0.0,1.0));

    float envelope=smoothstep(uCore+.12,uInner+.1,r)
        *(1.0-smoothstep(uOuter,uOuter*1.38,r));
    float disk=exp(-pow(y/.26,2.0))*exp(-.9*max(0.0,r-uInner));
    float aura=exp(-pow(y/(.46+.06*r),2.0))*exp(-.32*r*r);
    vec3 warm=mix(uCool,uHot,exp(-.75*max(0.0,r-uInner)));
    vec3 diskGradient=mix(uHaloDiskInnerColor,uHaloDiskOuterColor,
        smoothstep(uInner,uOuter,r));
    vec3 diskColor=mix(warm,diskGradient,
        clamp(uHaloDiskGradientMix,0.0,1.0));
    vec3 auraGradient=mix(uHaloAuraInnerColor,uHaloAuraOuterColor,
        smoothstep(uCore,uOuter*1.38,r));
    vec3 auraColor=mix(uCool,auraGradient,
        clamp(uHaloAuraGradientMix,0.0,1.0));
    // Material grain belongs to disk-side radiation. Never multiply the
    // smooth circumferential rim by procedural dust or ribbon noise.
    float structure=1.0;
    if(uGranular==2)structure=mix(.65,1.0,
        clamp(previewDiskPattern(r,atan(q.z,q.x)).x,0.0,1.0));
    if(uGranular==3)structure=mix(.65,1.0,
        clamp(lensedDustVolume(q),0.0,1.0));
    return HaloStrength*(ringColor*ring*HaloRing*1.6
        +structure*envelope*(diskColor*disk*HaloDisk*.22
        +auraColor*aura*HaloAura*.20));
}

// A separate polar volume, evaluated along the same bent ray as the disk.
// Its strength defaults to zero for every existing recipe.  This is original
// procedural geometry: no billboard or borrowed texture is involved.
vec3 jetEmission(vec3 q){
    if(JetStrength<=0.0001)return vec3(0.0);
    float height=abs(q.y),radial=length(q.xz);
    float start=uCore*.82;
    float length=max(JetLength,start+.35);
    if(height<start || height>length)return vec3(0.0);
    float progress=clamp((height-start)/(length-start),0.0,1.0);
    float opening=JetWidth*(.42+.78*pow(progress,.72));
    float spine=exp(-pow(radial/max(opening*.30,.006),1.6));
    float sheath=exp(-pow(radial/max(opening,.012),2.0));
    float rise=smoothstep(0.0,.12,progress);
    float fall=1.0-smoothstep(.76,1.0,progress);
    float azimuth=atan(q.z,q.x);
    float winding=3.0*azimuth-9.0*progress-1.4*uOrbitPhase*sign(q.y);
    float filaments=pow(.5+.5*sin(winding),4.0);
    float crossSection=.62*spine+.24*sheath*(.55+.45*filaments);
    vec3 tint=mix(uJetBaseColor,uJetTipColor,smoothstep(.06,.9,progress));
    return JetStrength*rise*fall*crossSection*tint;
}
