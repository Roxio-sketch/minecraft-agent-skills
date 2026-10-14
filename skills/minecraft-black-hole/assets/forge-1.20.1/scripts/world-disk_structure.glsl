// Original periodic cell noise. The user-selected blackhole-main reference
// motivates differential rotation and multiscale granular bands, not a code copy.
float fieldCell(ivec2 p){
    uint h=uint(p.x)*0x8da6b343u^uint(p.y)*0xd8163841u;
    h^=h>>16u;h*=0x7feb352du;h^=h>>15u;h*=0x846ca68bu;h^=h>>16u;
    return float(h>>8u)/16777216.0;
}
float fieldNoise(vec2 p,vec2 period){
    vec2 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    float a=fieldCell(ivec2(mod(cell,period))),b=fieldCell(ivec2(mod(cell+vec2(1,0),period)));
    float c=fieldCell(ivec2(mod(cell+vec2(0,1),period))),d=fieldCell(ivec2(mod(cell+vec2(1,1),period)));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
vec2 worldDiskField(float radius,float angle,float seed){
    float azimuth=angle/6.28318530718;
    float rho=radius+uRadialPhase;
    vec2 p=vec2(azimuth*24.0+rho*1.5,rho*4.0+seed),period=vec2(24,64);
    float coarse=fieldNoise(p,period),medium=fieldNoise(p*2.0+vec2(0,7),period*2.0);
    float fine=fieldNoise(p*4.0+vec2(0,19),period*4.0);
    float cloud=.57*coarse+.29*medium+.14*fine;
    return vec2(smoothstep(.22,.78,cloud),smoothstep(.25,.8,fine));
}
vec2 worldDiskPattern(float radius,float angle){
    float cycle=8.0,clock=BH_SHEAR_CLOCK,local=mod(clock,cycle);
    float parity=mod(floor(clock/cycle),2.0),omega=1.2/pow(max(radius,.3),1.5);
    vec2 older=worldDiskField(radius,angle-local*omega,uSeed+37.0*parity);
    vec2 younger=worldDiskField(radius,angle-(local-cycle)*omega,uSeed+37.0*(1.0-parity));
    return mix(older,younger,smoothstep(0.0,1.0,local/cycle));
}
