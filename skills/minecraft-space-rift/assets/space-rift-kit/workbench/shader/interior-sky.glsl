// Adapted from existing cosmic-sky-lab/workbench/sky-core.glsl.
// Source SHA256: cf8bb2bc5816c487b4477f8342538f09fa79ee3b0aa0152c1a2945a281c863f2
// Existing project algorithm reused; detached from world-sky uniforms.
vec3 riftInterior_skyHash(vec3 p) {
    // Integer lattice hash is stable within a recipe, across time and translation.
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float riftInterior_skyNoise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(riftInterior_skyHash(i).x,riftInterior_skyHash(i+vec3(1,0,0)).x,f.x),
                   mix(riftInterior_skyHash(i+vec3(0,1,0)).x,riftInterior_skyHash(i+vec3(1,1,0)).x,f.x),f.y),
               mix(mix(riftInterior_skyHash(i+vec3(0,0,1)).x,riftInterior_skyHash(i+vec3(1,0,1)).x,f.x),
                   mix(riftInterior_skyHash(i+vec3(0,1,1)).x,riftInterior_skyHash(i+vec3(1,1,1)).x,f.x),f.y),f.z);
}
float riftInterior_skyFbm(vec3 p, int octaves) {
    float result=0.0, amplitude=0.5, frequency=1.0;
    for(int i=0;i<6;i++) {
        if(i>=octaves) break;
        float band=1.-smoothstep(.22,.65,riftFootprint*frequency*6.);
        result+=amplitude*mix(.5,riftInterior_skyNoise(p),band);frequency*=2.03;
        p=vec3(0.8*p.x-0.6*p.z,p.y,0.6*p.x+0.8*p.z)*2.03+vec3(7.7,19.1,13.3);
        amplitude*=0.5;
    }
    return result;
}
vec3 riftInterior_galaxyDirection(vec3 d) {
    float cy=cos(vec4(0.,.18,.14,1.8).x),sy=sin(vec4(0.,.18,.14,1.8).x);
    float ct=cos(vec4(0.,.18,.14,1.8).y),st=sin(vec4(0.,.18,.14,1.8).y);
    d=vec3(cy*d.x+sy*d.z,d.y,-sy*d.x+cy*d.z);
    return vec3(ct*d.x-st*d.y,st*d.x+ct*d.y,d.z);
}
vec3 riftInterior_skyStars(vec3 d, float frequency, float layer, float clustering) {
    vec3 p=d*frequency, cell=floor(p);
    float footprint=riftFootprint*frequency;
    vec3 result=vec3(0.0);
    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
        vec3 id=cell+vec3(x,y,z);
        vec3 r=riftInterior_skyHash(id+vec3(layer,u_seed*0.011,u_seed*0.017));
        float select=riftInterior_skyHash(id+vec3(173.1,u_seed*0.021,layer)).y;
        float coverage=clamp(vec4(.055,2.2,.65,.15).x*(0.68+0.65*clustering),0.0,1.0);
        float exists=1.0-step(coverage,select);
        vec3 center=id+0.12+r*0.76;
        float radius=(0.028+0.085*r.z*r.z)*vec4(.055,2.2,.65,.15).z;
        float width=max(radius,footprint*0.62);
        vec3 delta=p-center;
        float distance2=dot(delta,delta);
        // Candidates are searched only within +/-1 cells. A removed/new center is
        // at least 1.12 units away across a cell boundary. All light, including
        // tangent spikes and halos, must reach zero before that boundary.
        // Tangent-only exponential tails otherwise leave dashed grid-plane seams.
        exists*=1.0-smoothstep(0.75*0.75,1.0,distance2);
        float core=exp(-distance2/(width*width));
        float energy=radius*radius/(width*width);
        float beat=0.5+0.5*sin((u_time*u_speed*u_insideSpeed)*vec4(.2,.8,0.,0.).x*(1.1+2.0*r.x)+r.y*35.0);
        float twinkle=mix(1.0,0.18+1.82*beat*beat,vec4(.055,2.2,.65,.15).w);
        float bright=2.0+7.0*pow(r.y,7.0);
        vec3 tint=mix(vec3(.38,.58,1.),vec3(1.,.7,.44),r.x);
        result+=exists*core*energy*tint*bright*twinkle;
        // Rare larger stars gain a local spherical glow and tangent-space spikes.
        if(r.y>0.96 && layer<20.0) {
            vec3 radial=normalize(center);
            vec3 axis=abs(radial.y)<0.95?vec3(0,1,0):vec3(1,0,0);
            vec3 side=normalize(cross(radial,axis)),up=cross(side,radial);
            vec2 uv=vec2(dot(delta,side),dot(delta,up));
            float thin=max(radius*0.24,footprint*0.6),longer=radius*6.0;
            float spikes=exp(-abs(uv.x)/longer-uv.y*uv.y/(thin*thin))+
                         exp(-abs(uv.y)/longer-uv.x*uv.x/(thin*thin));
            float halo=exp(-distance2/(radius*radius*14.0));
            result+=exists*tint*(halo*0.14+spikes*0.30)*energy*twinkle;
        }
    }
    return result*vec4(.055,2.2,.65,.15).y;
}
vec3 riftInterior_skyGalaxy(vec3 d, out float clustering) {
    vec3 g=riftInterior_galaxyDirection(d);
    float band=exp(-pow(g.y/max(vec4(0.,.18,.14,1.8).z,0.04),2.0));
    vec3 offset=vec3(u_seed*0.0011,u_seed*0.0017,u_seed*0.0023);
    float grains=riftInterior_skyFbm(g*5.0+offset,int(u_quality)+3);
    float knots=riftInterior_skyFbm(g*12.0+offset+17.0,int(u_quality)+2);
    float dense=band*smoothstep(0.20,0.72,grains);
    float core=pow(max(g.z,0.0),12.0)*band;
    float ridge=1.0-abs(2.0*knots-1.0);
    float dark=pow(ridge,3.0)*exp(-pow((g.y+0.02)/(vec4(0.,.18,.14,1.8).z*0.48),2.0));
    float transmission=exp(-dark*vec4(2.8,3.2,1.4,1.).z*3.2);
    clustering=band*(0.4+0.6*grains)*transmission;
    vec3 light=vec3(.28,.36,.82)*(dense*0.38+core*0.24)*(0.55+0.7*knots);
    light+=mix(vec3(.18,.08,.45),vec3(.06,.25,.62),grains)*dense*dense*0.20;
    return light*vec4(0.,.18,.14,1.8).w*transmission;
}
vec3 riftInterior_skyNebula(vec3 d) {
    vec3 origin=vec3(u_seed*0.0013,u_seed*0.0009,u_seed*0.0019);
    // Both drift and evolving warp are perceptible over a few seconds at default speed.
    float flow=(u_time*u_speed*u_insideSpeed)*vec4(.2,.8,0.,0.).y*0.15;
    vec3 p=d*vec4(2.8,3.2,1.4,1.).y+origin;
    vec3 warp=vec3(riftInterior_skyFbm(p+vec3(flow,0,0),int(u_quality)+2),
                   riftInterior_skyFbm(p+vec3(9.1,flow*0.7,3.7),int(u_quality)+2),
                   riftInterior_skyFbm(p+vec3(4.3,8.1,-flow*0.6),int(u_quality)+2));
    float dust=riftInterior_skyFbm(p*1.65+warp*2.1+vec3(flow*0.3,-flow*0.15,0),int(u_quality)+3);
    float envelope=riftInterior_skyFbm(d*2.2+origin+vec3(17.2,1.8,6.3),3);
    float density=smoothstep(0.34,0.70,dust)*smoothstep(0.30,0.67,envelope);
    float filaments=pow(max(1.0-abs(dust*2.0-1.0),0.0),9.0);
    vec3 tint=mix(vec3(.18,.08,.45),vec3(.06,.25,.62),smoothstep(0.30,0.65,warp.y));
    return (tint*density*0.6+tint*filaments*density*0.18)*vec4(2.8,3.2,1.4,1.).x;
}
