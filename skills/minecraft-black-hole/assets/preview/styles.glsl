// Original lensed emission fields. Every feature is sampled along the bent ray,
// including the upper/lower disk images; no screen-space sprite overlay.
float volumeNoise(vec3 p,vec3 period);
vec2 cinematicPattern(float radius,float angle){
    // All features share a seamless cylindrical coordinate and bounded orbital
    // phase. The three frequency bands describe plasma, filaments and sparks.
    float phase=angle-uOrbitPhase;
    float radial=(radius-uInner)/max(.01,uOuter-uInner);
    float flow=mod(uRadialPhase,64.0)/max(.01,uOuter-uInner);
    float rho=radial+flow;
    vec2 orbit=vec2(cos(phase),sin(phase));
    float cloud=volumeNoise(vec3(rho*3.7,orbit*2.6)+vec3(uSeed*.13,11.0,7.0),vec3(128.0));
    float structure=volumeNoise(vec3(rho*8.2,orbit*5.1)+vec3(29.0,uSeed*.17,4.0),vec3(128.0));
    float speck=volumeNoise(vec3(rho*23.0,orbit*12.0)+vec3(17.0,41.0,uSeed*.21),vec3(128.0));
    // A second, finer field breaks the broad lanes into finite plasma wisps.
    // It is deliberately bounded and cylindrical so it cannot accumulate the
    // long-time radial shear that produced the old moiré rings.
    float micro=volumeNoise(vec3(rho*41.0,orbit*22.0)+vec3(53.0,uSeed*.31,19.0),vec3(128.0));
    // A few wide plasma lanes carry the image. Fine filaments sit inside them;
    // they must not turn the entire lensed disk into evenly spaced wire lines.
    float radialPhase=rho*uBandDetail*.62;
    float warped=radialPhase+.065*sin(radialPhase*2.1+2.0*phase)
        +.13*uFilament*(cloud-.5)+.045*sin(3.0*phase+2.0*structure)
        +.045*(micro-.5)*uFilament;
    // Continuous lanes and fine threads avoid hard, isolated emission blocks.
    // The angular terms are periodic, including at atan's branch cut.
    float lane=.5+.5*cos(6.28318530718*warped);
    float broad=smoothstep(.16,.80,lane);
    float ridge=pow(lane,3.0);
    float hair=pow(.5+.5*sin(6.28318530718*(warped*2.12+.16*structure)),9.0);
    float spiral=.5+.5*sin(2.0*phase-3.0*uSpiral*log(max(radius/uInner,1.0))+2.0*(cloud-.5));
    float arm=mix(1.0,.48+.52*spiral,uSpiral);
    float knots=smoothstep(.58,.82,speck)*smoothstep(.48,.72,structure);
    float microKnots=smoothstep(.62,.84,micro)*smoothstep(.57,.78,speck);
    // Dark dust lanes are part of the emission field, instead of an opaque
    // screen-space overlay. They provide depth cues when the sheet is lensed.
    float dustLane=1.0-.72*smoothstep(.48,.75,cloud);
    float filamentMask=.34+.66*smoothstep(.28,.74,micro);
    float plasma=(.075+.34*ridge+.22*uFilament*hair*filamentMask
        +.11*knots+.07*microKnots)
        *(.42+.58*broad)*(.7+.6*structure)*dustLane*arm;
    return vec2(max(0.0,plasma),clamp(.18+.37*broad+.22*ridge+.15*knots+.10*microKnots,0.0,1.0));
}
float volumeCell(ivec3 p);
float ribbonRadialNoise(float coordinate,float seed){
    float cell=floor(coordinate),f=fract(coordinate);
    f=f*f*(3.0-2.0*f);
    return mix(volumeCell(ivec3(int(mod(cell,128.0)),int(seed),19)),
        volumeCell(ivec3(int(mod(cell+1.0,128.0)),int(seed),19)),f);
}
vec2 ribbonPattern(float radius,float angle){
    // Radially stretched stochastic lanes, inspired by the reference's polar
    // noise lookup. Angular variation changes brightness only: it cannot warp
    // two independent periodic carriers into a braided lattice.
    float phase=angle-uOrbitPhase;
    float radial=clamp((radius-uInner)/max(uOuter-uInner,.01),0.0,1.0);
    float rho=radial*uBandDetail*1.6+uRadialPhase*.16;
    vec2 orbit=vec2(cos(phase),sin(phase));
    vec3 offset=vec3(uSeed*.13,9.0,13.0);
    float broad=ribbonRadialNoise(rho,uSeed);
    float detail=ribbonRadialNoise(rho*3.1,uSeed+17.0);
    float fine=ribbonRadialNoise(rho*7.3,uSeed+41.0);
    float lanes=smoothstep(.18,.78,.64*broad+.26*detail+.10*fine);
    float wisps=volumeNoise(vec3(radial*2.0,orbit*.7)+offset,vec3(128.0));
    float angular=mix(1.0,.86+.28*wisps,clamp(uFilament/3.0,0.0,1.0));
    float shade=(.035+1.1*pow(lanes,2.2))*angular;
    return vec2(shade,clamp(.35+.65*lanes,0.0,1.0));
}
vec2 lensedDustPattern(float radius,float angle){
    return vec2(1.0);
}
float volumeCell(ivec3 p){
    uint h=uint(p.x)*0x8da6b343u^uint(p.y)*0xd8163841u^uint(p.z)*0xcb1ab31fu;
    h^=h>>16u;h*=0x7feb352du;h^=h>>15u;h*=0x846ca68bu;h^=h>>16u;
    return float(h>>8u)/16777216.0;
}
float volumeNoise(vec3 p,vec3 period){
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float value=0.0;
    for(int z=0;z<2;z++)for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec3 corner=vec3(float(x),float(y),float(z));
        vec3 weight=mix(1.0-f,f,corner);
        value+=volumeCell(ivec3(mod(cell+corner,period)))*weight.x*weight.y*weight.z;
    }
    return value;
}
float lensedDustVolume(vec3 q){
    float radius=length(q.xz),angle=atan(q.z,q.x);
    float phase=(angle-uOrbitPhase)/6.28318530718;
    float rho=radius+uRadialPhase;
    vec3 p=vec3(rho*2.0,phase*12.0,atan(q.y,max(radius,.01))*8.0);
    vec3 period=vec3(256.0,12.0,128.0);
    float field=1.0;
    // Bounded angular offsets between octaves follow the reference's moving
    // density field without accumulating radial shear indefinitely.
    for(int i=0;i<4;i++){
        float scale=uDustScale*pow(2.0,float(i));
        vec3 drift=vec3(0.0,(i%2==0?uDustDriftA:uDustDriftB)*scale,0.0);
        float n=volumeNoise(p*scale+drift+vec3(uSeed,float(i)*13.0,17.0),period*scale);
        field*=.12+.88*n;
    }
    // Normalize the four-octave product before thresholding. This preserves
    // sparse dusty clumps and dark gaps without suppressing a narrow disk.
    float clustered=pow(max(field,1e-8),.25);
    float porous=pow(smoothstep(.43,.70,clustered),1.8);
    return porous*uDustFill;
}
