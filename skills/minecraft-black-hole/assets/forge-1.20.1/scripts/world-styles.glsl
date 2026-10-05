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
vec2 ribbonPattern(float radius,float angle){
    // Uniform angular advection follows the Unity reference. A radius-dependent
    // time phase produces ever finer shear and aliases after minutes of playback.
    float phase=angle-uOrbitPhase;
    float radial=(radius-uInner)/max(uOuter-uInner,.01);
    float rho=radial*uBandDetail*1.2+uRadialPhase*.16;
    vec2 orbit=vec2(cos(phase),sin(phase));
    float warp=volumeNoise(vec3(rho*1.7,orbit*3.0)+vec3(uSeed*.13,9.0,13.0),vec3(128.0));
    float grain=volumeNoise(vec3(rho*5.2,orbit*8.0)+vec3(3.0,uSeed*.29,17.0),vec3(128.0));
    float lane=.5+.5*cos(6.28318530718*(rho+.11*(warp-.5)));
    float filament=pow(.5+.5*cos(6.28318530718*(rho*2.3+.16*(grain-.5))),7.0);
    float shade=(.16+.64*pow(lane,2.0)+.20*filament)*(.46+.54*warp);
    return vec2(shade,clamp(.18+.58*lane+.24*grain,0.0,1.0));
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
    float porous=pow(smoothstep(.11,.34,field),2.2);
    return porous*uDustFill;
}
