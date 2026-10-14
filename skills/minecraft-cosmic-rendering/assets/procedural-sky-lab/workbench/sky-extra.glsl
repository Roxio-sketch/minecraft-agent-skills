// Original finite direction-space celestial impostors. No external textures.
// Concatenate after sky-core helpers. The host declares GiantBodySettings as
// (style 0..7, angular scale 1..4, separation 0..3, atmosphere/corona 0..2).
// skyCelestial delegates here only for style > 0. Normal style 0 stays intact.
// BodySettings.y is the base angular radius in radians; the effective radius
// is capped at 1.18 rad (67.6 degrees), before optional rings or corona.
float skyGiantWindow(float r,float outer) {
    return 1.0-smoothstep(outer*0.78,outer,r);
}
vec2 skyGiantRotate(vec2 p,float a) {
    float c=cos(a),s=sin(a);
    return vec2(c*p.x-s*p.y,s*p.x+c*p.y);
}
float skyGiantDisc(vec2 p,float radius,float aa) {
    return 1.0-smoothstep(radius-aa,radius+aa,length(p));
}
vec3 skyGiantNormal(vec2 p) {
    // Analytic apparent hemisphere: finite both inside and outside its limb.
    return normalize(vec3(p,sqrt(max(1.0-dot(p,p),0.0)))+vec3(0.0,0.0,0.00001));
}
float skyGiantLight(vec3 n) {
    vec3 light=normalize(vec3(sin(BodySurface.y),0.18,cos(BodySurface.y)));
    return max(dot(n,light),0.0);
}
vec3 skyGiantSeed() {
    return vec3(Seed*0.0017,Seed*0.0023,Seed*0.0031)+vec3(7.1,31.9,13.7);
}
vec3 skyGiantRotatingNormal(vec3 n,float clock) {
    vec2 xz=skyGiantRotate(n.xz,clock*0.16);
    return vec3(xz.x,n.y,xz.y);
}
float skyGiantCorona(vec2 p,float clock,float aa,float width) {
    float r=length(p);
    // Cartesian ring sampling is periodic on the complete circumference.
    // It introduces no atan/fract longitude discontinuity or pole axis choice.
    vec2 unit=p/max(r,0.001);
    vec2 moving=skyGiantRotate(unit,clock*0.12);
    float grain=skyFbm(vec3(moving*4.2,r*5.0-clock*0.24)+skyGiantSeed(),Quality+2);
    float edge=1.0+width*(0.13+0.85*pow(grain,2.0));
    float outside=smoothstep(1.0-aa,1.0+aa,r);
    float filament=pow(max(grain,0.0),2.0)*(0.75+0.25*sin(r*59.0-clock*2.1+grain*7.0));
    return outside*exp(-max(r-edge,0.0)/max(width*0.30,0.01))*
           skyGiantWindow(r,1.0+width*2.8)*(0.15+2.5*filament);
}
void skyGiantGas(vec2 p,float aa,float clock,inout vec3 color) {
    vec2 q=p/vec2(1.0,0.91);
    float r=length(q),mask=skyGiantDisc(q,1.0,aa);
    vec3 n=skyGiantNormal(q),rot=skyGiantRotatingNormal(n,clock);
    float warp=skyFbm(rot*3.5+skyGiantSeed(),Quality+2);
    float storm=skyFbm(rot*11.0+skyGiantSeed()+vec3(0.0,clock*0.08,0.0),Quality+2);
    float bands=0.5+0.5*sin(rot.y*(20.0+BodySurface.z*1.5)+warp*6.0+storm*1.6);
    float oval=exp(-dot((q-vec2(0.26,0.14))/vec2(0.27,0.12),
                       (q-vec2(0.26,0.14))/vec2(0.27,0.12)));
    float swirling=0.5+0.5*sin(length((q-vec2(0.26,0.14))/vec2(0.8,0.35))*45.0-warp*8.0-clock*0.25);
    vec3 surface=mix(BodyColorA,BodyColorB,clamp(bands*0.80+storm*0.20,0.0,1.0));
    surface=mix(surface,BodyColorB*(0.6+0.6*swirling),oval*0.62);
    vec3 body=surface*(0.055+1.18*skyGiantLight(n));
    float halo=exp(-max(r-1.0,0.0)/0.055)*smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.33);
    // Optional thin elliptical ring; front half overlays the visible planet.
    vec2 ringPoint=skyGiantRotate(p,-0.28);
    float inclination=max(abs(cos(RingSettings.y)),0.13);
    vec2 ringUv=ringPoint/vec2(1.0,inclination*0.45);
    float rr=length(ringUv),outer=1.50+0.18*GiantBodySettings.z;
    float ringMask=smoothstep(1.12-aa,1.12+aa,rr)*(1.0-smoothstep(outer-aa,outer+aa,rr));
    float stripes=0.58+0.42*sin(rr*140.0+storm*1.8);
    float ringAlpha=ringMask*clamp(RingSettings.x*0.72,0.0,0.92);
    vec3 ringColor=RingColor*(0.24+0.90*stripes)*BodySettings.x;
    if(ringPoint.y>=0.0) color=mix(color,ringColor,ringAlpha);
    color=mix(color,body*BodySettings.x,mask);
    if(ringPoint.y<0.0) color=mix(color,ringColor,ringAlpha);
    color+=BodyColorA*halo*BodySettings.x*GiantBodySettings.w*0.20;
}
void skyGiantFrozen(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float continents=skyFbm(rot*max(BodySurface.z*0.35,2.0)+skyGiantSeed(),Quality+3);
    float cracks=abs(skyFbm(rot*10.0+skyGiantSeed(),Quality+2)-0.43);
    float ice=smoothstep(0.35,0.53,continents+abs(rot.y)*0.13);
    float seams=1.0-smoothstep(0.012,0.04,cracks);
    vec3 surface=mix(BodyColorB*0.28,BodyColorA*(0.68+continents*0.65),ice);
    surface+=BodyColorB*seams*ice*0.15;
    float rim=pow(1.0-n.z,3.0);
    vec3 body=surface*(0.07+skyGiantLight(n)*1.10)+BodyColorA*rim*0.18;
    color=mix(color,body*BodySettings.x,mask);
    float haze=exp(-max(r-1.0,0.0)/0.07)*smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.40);
    color+=mix(BodyColorA,BodyColorB,0.25)*haze*BodySettings.x*GiantBodySettings.w*0.28;
}
void skyGiantShattered(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float rock=skyFbm(rot*max(BodySurface.z,3.0)+skyGiantSeed(),Quality+3);
    float crooked=0.065*sin(p.y*7.0+rock*2.0);
    float fracture=min(abs(p.x+0.40*p.y+crooked),
                       min(abs(p.y-0.25*p.x+0.19+crooked),abs(p.x-0.72*p.y-0.42)));
    float gap=0.013+0.018*GiantBodySettings.z;
    float intact=smoothstep(gap-aa*0.5,gap+aa,fracture);
    float hot=exp(-fracture/max(gap*1.4,0.014))*(0.70+0.30*sin(clock*0.8+rock*6.0));
    vec3 body=mix(BodyColorB*0.3,BodyColorA,rock)*(0.05+skyGiantLight(n));
    color=mix(color,body*BodySettings.x,mask*intact);
    color+=BodyColorB*hot*mask*BodySettings.x*0.8;
    // Detached fragments orbit beyond the broken silhouette, not a color swap.
    for(int i=0;i<9;i++) {
        vec3 h=skyHash(vec3(float(i),27.1,Seed*0.01));
        float angle=h.x*2.0*SKY_PI+clock*(0.025+h.z*0.025);
        float distance=1.10+h.y*(0.25+GiantBodySettings.z*0.17);
        vec2 center=vec2(cos(angle),sin(angle))*distance;
        vec2 q=skyGiantRotate(p-center,angle*1.7)/vec2(0.035+h.z*0.075,0.025+h.y*0.050);
        float fragment=1.0-smoothstep(0.70,1.0,max(abs(q.x),abs(q.y)));
        color=mix(color,BodyColorA*(0.20+0.55*h.y)*BodySettings.x,fragment*0.92);
    }
    color+=BodyColorB*exp(-pow((r-1.0)/0.045,2.0))*mask*BodySettings.x*GiantBodySettings.w*0.15;
}
void skyGiantDarkDisc(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    color*=1.0-mask*min(BodySettings.x,1.0);
    float first=skyGiantCorona(p,clock,aa,0.23+GiantBodySettings.w*0.16);
    float second=skyGiantCorona(skyGiantRotate(p,0.9),-clock*0.73,aa,0.12+GiantBodySettings.w*0.17);
    float north=0.5+0.5*p.y/max(r,0.001);
    vec3 emission=BodyColorA*first*(0.25+0.75*north)+BodyColorB*second*(1.0-0.40*north);
    float edge=exp(-pow((r-1.018)/max(aa,0.015),2.0));
    color+=(emission*0.82+mix(BodyColorA,BodyColorB,0.42)*edge*0.75)*BodySettings.x;
}
void skyGiantStar(vec2 p,float aa,float clock,vec3 tintA,vec3 tintB,float strength,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    vec3 n=skyGiantNormal(p),rot=skyGiantRotatingNormal(n,clock);
    float cells=skyFbm(rot*(8.0+BodySurface.z*0.8)+skyGiantSeed()+vec3(0.0,clock*0.06,0.0),Quality+2);
    float broad=skyFbm(rot*3.5+skyGiantSeed()+vec3(clock*0.08,0.0,0.0),Quality+2);
    float limb=0.35+0.65*pow(max(n.z,0.0),0.40);
    vec3 photosphere=mix(tintB,tintA,clamp(0.28+cells*0.85,0.0,1.0))*limb*(0.50+0.85*broad);
    color=mix(color,photosphere*strength*1.6,mask);
    float corona=skyGiantCorona(p,clock,aa,0.22+GiantBodySettings.w*0.18);
    vec2 unit=p/max(r,0.001),w=skyGiantRotate(unit,clock*0.10);
    // Local closed prominence loops lift away from the limb at selected arcs.
    float lobe=pow(max(0.0,0.5+0.5*(3.0*w.y-4.0*w.y*w.y*w.y)),8.0);
    float loopRadius=1.10+(0.09+0.15*GiantBodySettings.w)*lobe;
    float loops=exp(-pow((r-loopRadius)/0.030,2.0))*lobe*
                smoothstep(1.0-aa,1.0+aa,r)*skyGiantWindow(r,1.72);
    color+=(tintB*corona*0.8+mix(tintA,tintB,0.5)*loops*0.9)*strength;
}
void skyGiantBinary(vec2 p,float aa,float clock,inout vec3 color) {
    float separation=0.55+0.24*GiantBodySettings.z;
    vec2 axis=vec2(cos(clock*0.075+0.22),sin(clock*0.075+0.22));
    vec2 first=axis*separation,second=-axis*separation;
    float sizeA=0.64,sizeB=0.46;
    vec2 bridge=vec2(dot(p,axis),dot(p,vec2(-axis.y,axis.x)));
    float connection=exp(-pow(bridge.y/0.105,2.0))*skyGiantWindow(abs(bridge.x),separation)*
                     (0.6+0.4*skyNoise(vec3(bridge*12.0,clock*0.7)+skyGiantSeed()));
    color+=mix(BodyColorA,BodyColorB,0.5)*connection*BodySettings.x*GiantBodySettings.w*0.16;
    skyGiantStar((p-first)/sizeA,aa/sizeA,clock,BodyColorA,BodyColorB,BodySettings.x,color);
    skyGiantStar((p-second)/sizeB,aa/sizeB,-clock*1.3,BodyColorB,BodyColorA,BodySettings.x*0.76,color);
}
void skyGiantVortex(vec2 p,float aa,float clock,inout vec3 color) {
    float r=length(p),mask=skyGiantDisc(p,1.0,aa);
    color*=1.0-mask*min(BodySettings.x,1.0);
    float twist=clock*0.24+2.8/(r*r+0.18)+r*2.5;
    vec2 warped=skyGiantRotate(p,twist),unit=warped/max(r,0.001);
    float arms=0.5+0.5*(3.0*unit.y-4.0*unit.y*unit.y*unit.y);
    float gas=skyFbm(vec3(warped*5.0,r*3.0-clock*0.25)+skyGiantSeed(),Quality+2);
    float strands=0.5+0.5*sin(r*72.0-clock*2.5+gas*8.0);
    float outside=smoothstep(1.0-aa,1.0+aa,r);
    float ring=exp(-max(r-1.035,0.0)/0.17)*outside;
    float cloud=exp(-max(r-1.0,0.0)/0.65)*pow(max(arms,0.0),3.0)*(0.25+gas);
    float support=skyGiantWindow(r,2.30+GiantBodySettings.w*0.30);
    vec3 emission=mix(BodyColorB,BodyColorA,gas)*(ring*(0.7+0.8*strands)+cloud*outside*0.70);
    color+=emission*support*BodySettings.x*(0.7+GiantBodySettings.w*0.35);
}
void skyGiantCelestial(vec3 d,inout vec3 color) {
    if(BodySettings.x<=0.0 || GiantBodySettings.x<0.5) return;
    vec3 center=vec3(sin(BodySettings.z)*cos(BodySettings.w),sin(BodySettings.w),
                     cos(BodySettings.z)*cos(BodySettings.w));
    // A yaw-defined side stays finite at the exact north and south poles.
    vec3 side=vec3(cos(BodySettings.z),0.0,-sin(BodySettings.z));
    vec3 up=cross(center,side);
    float alignment=dot(d,center);
    float angularRadius=clamp(BodySettings.y*GiantBodySettings.y,0.003,1.18);
    vec2 p=vec2(dot(d,side),dot(d,up))/(max(alignment,0.02)*tan(angularRadius));
    // Differentiate a bounded field before support rejection. No tangent-plane
    // derivative can create the horizontal far-field lines seen in old effects.
    float support=3.65;
    vec2 bounded=clamp(p,vec2(-support-1.0),vec2(support+1.0));
    float aa=clamp(max(fwidth(bounded.x),fwidth(bounded.y)),0.0006,0.045);
    if(alignment<=0.0 || length(p)>=support) return;
    float clock=Time*BodySurface.w;
    int style=int(floor(GiantBodySettings.x+0.5));
    if(style==1) skyGiantGas(p,aa,clock,color);
    else if(style==2) skyGiantFrozen(p,aa,clock,color);
    else if(style==3) skyGiantShattered(p,aa,clock,color);
    else if(style==4) skyGiantDarkDisc(p,aa,clock,color);
    else if(style==5) skyGiantBinary(p,aa,clock,color);
    else if(style==6) skyGiantStar(p,aa,clock,BodyColorA,BodyColorB,BodySettings.x,color);
    else if(style==7) skyGiantVortex(p,aa,clock,color);
}

// Original direction-space extensions. Concatenate after sky-core.glsl, before
// environment-core.glsl in both hosts. No sampled images or third-party code.
// Recipe angular widths are converted to radians by the host exactly once.
vec2 skyWeatherAmounts() {
    return mix(clamp(WeatherSettings.xy,vec2(0.0),vec2(1.0)),
               clamp(WorldWeather,vec2(0.0),vec2(1.0)),clamp(WeatherMotion.y,0.0,1.0));
}
float skyCompactGaussian(float coordinate,float width,float support) {
    float q=abs(coordinate);
    return exp(-pow(q/max(width,0.00001),2.0))*(1.0-smoothstep(support*0.78,support,q));
}
vec3 skyOpticalSpectrum(float coordinate) {
    // Violet -> blue -> cyan -> green -> yellow -> orange -> red.
    float t=clamp(coordinate,0.0,1.0)*6.0;
    if(t<1.0) return mix(vec3(0.36,0.035,0.8),vec3(0.04,0.22,1.0),t);
    if(t<2.0) return mix(vec3(0.04,0.22,1.0),vec3(0.06,0.8,0.8),t-1.0);
    if(t<3.0) return mix(vec3(0.06,0.8,0.8),vec3(0.10,0.9,0.16),t-2.0);
    if(t<4.0) return mix(vec3(0.10,0.9,0.16),vec3(1.0,0.86,0.06),t-3.0);
    if(t<5.0) return mix(vec3(1.0,0.86,0.06),vec3(1.0,0.29,0.025),t-4.0);
    return mix(vec3(1.0,0.29,0.025),vec3(0.95,0.055,0.025),t-5.0);
}
void skyRainbow(vec3 d,vec3 sun,vec2 weather,inout vec3 color) {
    if(RainbowSettings.x<=0.0 || AtmosphereSettings.x<=0.0) return;
    // Droplets can persist after rain: wetness is an independent explicit input.
    float wet=clamp(max(weather.x,RainbowSettings.w),0.0,1.0);
    float daylight=smoothstep(0.0,0.08,sun.y)*min(SunSettings.x,1.6);
    float cloudCover=clamp(ThinCloudSettings.x*ThinCloudSettings.y+
                          VolumeCloudSettings.x*VolumeCloudSettings.y,0.0,1.0);
    float visibility=wet*daylight*(1.0-0.75*cloudCover)*(1.0-0.85*weather.y)*
                     smoothstep(-0.018,0.025,d.y)*AtmosphereSettings.x;
    // Primary rainbow is centered on the antisolar point, red outside; the
    // secondary is near 51 degrees with the color order reversed. These are
    // bounded artistic band approximations, not a spectral droplet integrator.
    float angle=acos(clamp(dot(d,-sun),-1.0,1.0));
    float aa=clamp(fwidth(angle),0.00004,0.012);
    float halfWidth=max(RainbowSettings.y,0.00436);
    float primaryCenter=41.6*SKY_PI/180.0;
    float secondaryCenter=51.6*SKY_PI/180.0;
    float primaryDistance=angle-primaryCenter;
    float secondaryDistance=angle-secondaryCenter;
    float primary=1.0-smoothstep(halfWidth-aa,halfWidth+aa,abs(primaryDistance));
    float secondary=1.0-smoothstep(halfWidth*1.4-aa,halfWidth*1.4+aa,abs(secondaryDistance));
    float tPrimary=0.5+primaryDistance/(2.0*halfWidth);
    float tSecondary=0.5-secondaryDistance/(2.8*halfWidth);
    float innerWindow=smoothstep(42.8*SKY_PI/180.0,44.0*SKY_PI/180.0,angle);
    float outerWindow=1.0-smoothstep(49.2*SKY_PI/180.0,50.4*SKY_PI/180.0,angle);
    color*=1.0-0.12*innerWindow*outerWindow*visibility*min(RainbowSettings.x,1.0);
    color+=skyOpticalSpectrum(tPrimary)*primary*RainbowSettings.x*visibility*0.30;
    color+=skyOpticalSpectrum(tSecondary)*secondary*RainbowSettings.z*visibility*0.16;
}
void skyIceHalo(vec3 d,vec3 sun,vec3 moon,vec2 weather,inout vec3 color) {
    if(HaloSettings.x<=0.0 || HaloSettings.z<=0.0 || AtmosphereSettings.x<=0.0) return;
    bool lunar=HaloSettings.w>0.5;
    vec3 source=lunar?moon:sun;
    float sourceBrightness=lunar?MoonSettings.x*(0.5+0.5*cos(MoonSurface.x)):SunSettings.x;
    float visibility=smoothstep(-0.018,0.02,source.y)*sourceBrightness*
                     HaloSettings.z*AtmosphereSettings.x*(1.0-0.83*weather.y)*
                     smoothstep(-0.025,0.015,d.y);
    // Randomly oriented hexagonal ice prisms have a roughly 22-degree minimum
    // deviation. The reddish inner edge is near 22 deg; short wavelengths lie
    // outside it. Moon halos are mostly white. This is a geometric optical ring,
    // deliberately unrelated to the small local moon glow or a bloom pass.
    float angle=acos(clamp(dot(d,source),-1.0,1.0));
    float aa=clamp(fwidth(angle),0.00004,0.012);
    float width=max(HaloSettings.y,0.00349);
    float inner=21.8*SKY_PI/180.0;
    float edge=smoothstep(inner-aa,inner+aa,angle);
    float halo=edge*skyCompactGaussian(angle-(inner+width*0.50),width*0.85,width*3.0);
    float spectrum=1.0-clamp((angle-inner)/max(width,0.0001),0.0,1.0);
    vec3 tint=mix(vec3(1.0),skyOpticalSpectrum(spectrum),lunar?0.12:0.50);
    float cirrus=0.60+0.40*clamp(ThinCloudSettings.y+VolumeCloudSettings.y*VolumeCloudSettings.x,0.0,1.0);
    color+=tint*halo*HaloSettings.x*visibility*cirrus*(lunar?0.12:0.24);
}
void skyStorm(vec3 d,vec2 weather,inout vec3 color) {
    float storm=clamp(weather.x*0.55+weather.y*0.80,0.0,1.0);
    if(storm<=0.0) return;
    float clock=Time*WeatherSettings.z;
    float rolling=skyFbm(d*3.4+vec3(clock*0.015,Seed*0.008,clock*0.009),Quality+2);
    float darkness=storm*WeatherSettings.w*(0.48+0.32*rolling)*smoothstep(-0.20,0.1,d.y);
    color*=1.0-darkness;
    color+=vec3(0.014,0.020,0.029)*storm*(0.20+0.65*rolling);
    if(weather.y<=0.0 || WeatherMotion.z<=0.0) return;
    float period=7.2/max(WeatherMotion.z,0.001);
    float event=floor(clock/period),within=clock-event*period;
    vec3 pick=skyHash(vec3(event+Seed*0.031,17.21,38.16));
    float start=(0.12+0.48*pick.x)*period;
    float age=within-start,duration=WeatherMotion.w;
    float leading=smoothstep(0.0,min(0.035,duration*0.15),age);
    float envelope=leading*(1.0-smoothstep(duration*0.5,duration,age));
    float pulses=0.72+0.28*pow(0.5+0.5*cos(age*83.0),2.0);
    vec3 center=normalize(vec3(sin(pick.y*2.0*SKY_PI),0.28+0.30*pick.z,cos(pick.y*2.0*SKY_PI)));
    float illumination=0.30+0.70*pow(max(dot(d,center),0.0),3.0);
    color+=LightningColor*weather.y*envelope*pulses*illumination*1.6;
}
void skySpecialWave(vec3 d,inout vec3 color) {
    if(SpecialSettings.x<=0.0 || SpecialSettings.y>2.5) return;
    float clock=Time*SpecialSettings.w;
    vec3 p=d*SpecialSettings.z*4.0+vec3(Seed*0.0019,Seed*0.0031,Seed*0.0013);
    vec3 warp=vec3(skyFbm(p+vec3(clock*0.09,0.0,0.0),Quality+2),
                   skyFbm(p+vec3(10.8,clock*0.07,19.1),Quality+2),
                   skyFbm(p+vec3(17.1,7.2,-clock*0.06),Quality+2));
    p+=(warp-0.5)*SpecialShape.x*2.0;
    float above=smoothstep(-0.18,0.04,d.y);
    if(SpecialSettings.y<1.5) {
        float field=(sin(p.x*1.7+p.z*0.8-clock*0.11)+
                     0.42*sin(p.y*2.7-p.z*1.2+clock*0.17))*0.70;
        float width=SpecialShape.y;
        float aa=clamp(fwidth(field),0.001,0.075);
        float rim=1.0-smoothstep(width-aa,width+aa,abs(field));
        float core=skyCompactGaussian(field,width*0.11+aa,width*0.55+aa);
        float veins=0.45+0.55*pow(skyNoise(p*5.3+clock*0.13),2.0);
        float alpha=(1.0-exp(-rim*SpecialSettings.x*0.34))*above;
        color=mix(color,SpecialColorA*(0.10+0.20*warp.y),alpha);
        color+=(SpecialColorA*rim*0.09+SpecialColorB*core*veins*0.80)*SpecialSettings.x*above;
    } else {
        float turbulence=skyFbm(p*1.7+vec3(clock*0.07,-clock*0.16,0.0),Quality+2);
        float density=smoothstep(0.20,0.74,warp.x*0.40+turbulence*0.60);
        float thread=1.0-abs(2.0*skyNoise(p*5.8+vec3(0.0,clock*0.17,clock*0.04))-1.0);
        float veins=pow(thread,9.0)*smoothstep(0.29,0.59,turbulence);
        float alpha=(1.0-exp(-density*SpecialSettings.x*0.70))*above;
        color=mix(color,SpecialColorA*(0.07+0.20*turbulence),alpha);
        color+=(SpecialColorA*density*0.19+SpecialColorB*veins*(0.60+warp.z))*SpecialSettings.x*above;
    }
}
void skyBirds(vec3 d,inout vec3 color) {
    if(SpecialShape.z<=0.0) return;
    float opacity=0.0;
    for(int i=0;i<24;i++) {
        vec3 random=skyHash(vec3(float(i)+Seed*0.021,55.32,91.71));
        float selected=1.0-step(SpecialShape.z,random.z);
        float clock=Time*SpecialShape.w;
        float yaw=(random.x-0.5)*2.0*SKY_PI+clock*(0.035+random.y*0.025);
        float elevation=0.12+random.y*0.50+sin(clock*0.12+random.x*17.0)*0.025;
        vec3 front=vec3(sin(yaw)*cos(elevation),sin(elevation),cos(yaw)*cos(elevation));
        vec3 side=vec3(cos(yaw),0.0,-sin(yaw)),up=cross(front,side);
        float alignment=dot(d,front),size=0.004+0.007*random.y;
        vec2 uv=vec2(dot(d,side),dot(d,up))/max(alignment,0.2)/size;
        // Bound the differentiated field and cap footprint before rejecting
        // pixels; no diverging plane footprint can illuminate a whole row.
        float aa=clamp(max(fwidth(clamp(uv.x,-2.0,2.0)),fwidth(clamp(uv.y,-2.0,2.0))),0.02,0.25);
        if(alignment<=0.0 || abs(uv.x)>1.3 || abs(uv.y)>1.3) continue;
        float flap=sin(clock*(6.0+random.x*2.0)+random.y*29.0)*0.44;
        float wingHeight=flap*abs(uv.x)+0.13*sin(min(abs(uv.x),1.0)*SKY_PI);
        float wing=(1.0-smoothstep(0.07,0.07+aa,abs(uv.y-wingHeight)))*
                   (1.0-smoothstep(0.80,1.0,abs(uv.x)));
        float body=1.0-smoothstep(1.0,1.0+aa*2.0,length((uv-vec2(0.0,-0.04))/vec2(0.09,0.25)));
        opacity+=selected*max(wing,body)*(SpecialSettings.y>2.5 && SpecialSettings.x>0.0?min(SpecialSettings.x,1.0):0.80);
    }
    color=mix(color,vec3(0.004,0.006,0.012),clamp(opacity,0.0,0.96));
}
void skyExtraEnvironment(vec3 direction,inout vec3 radiance) {
    vec2 weather=skyWeatherAmounts();
    skyStorm(direction,weather,radiance);
    vec3 sun,moon,sunSide,moonSide;
    skyDayDirections(sun,moon,sunSide,moonSide);
    skyRainbow(direction,sun,weather,radiance);
    skyIceHalo(direction,sun,moon,weather,radiance);
    skySpecialWave(direction,radiance);
    skyBirds(direction,radiance);
}
// Original bounded inverse thin-lens-style mapping for the direction-plane
// cinematic black hole. This is an artistic background distortion, not a
// Schwarzschild/Kerr geodesic or a physically calibrated lens mass.
vec3 skyBlackHoleCinematicLensDirection(vec3 d) {
    if(BlackHoleAppearance.x<1.5 || BlackHoleSettings.x<=0.0 || DiskSettings.w<=0.0) return d;
    vec3 center=vec3(sin(BlackHoleSettings.z)*cos(BlackHoleSettings.w),sin(BlackHoleSettings.w),
                     cos(BlackHoleSettings.z)*cos(BlackHoleSettings.w));
    vec3 side=vec3(cos(BlackHoleSettings.z),0.0,-sin(BlackHoleSettings.z)),up=cross(center,side);
    float alignment=dot(d,center);
    if(alignment<=0.0) return d;
    float radius=max(tan(BlackHoleSettings.y),0.001);
    vec2 p=vec2(dot(d,side),dot(d,up))/max(alignment,0.08)/radius;
    float r=length(p),support=3.25+clamp(DiskSettings.y-3.5,0.0,5.5)*0.15;
    if(r>=support) return d;
    float window=1.0-smoothstep(support*0.70,support,r);
    float strength=clamp(DiskSettings.w,0.0,1.5)*min(BlackHoleSettings.x,1.0);
    // beta = theta - a/theta, softened and bounded behind the opaque core.
    // The near-zero source radius outside the core stretches stars and nebula
    // into an Einstein-like arc; negative parity supplies the inner image.
    float deflection=min(2.2*strength*window/(r*r+0.08),4.0);
    vec2 source=p*(1.0-deflection);
    return normalize(center+radius*(side*source.x+up*source.y));
}
vec3 skyBlackHoleCinematicBackground(vec3 direction) {
    vec3 source=skyBlackHoleCinematicLensDirection(direction);
    // Retain the original unwarped derivative footprint outside the lens.
    if(all(equal(source,direction))) return skyEnvironment(direction);
    float clustering;
    vec3 color=BackgroundColor+skyGalaxy(source,clustering);
    if(CloudSettings.x>0.0) color+=skyNebula(source);
    if(StarSettings.x>0.0 && StarSettings.y>0.0) {
        color+=skyStars(source,55.0,11.0,clustering);
        if(Quality>=2) color+=skyStars(source,145.0,97.0,clustering)*0.72;
        if(Quality>=3) color+=skyStars(source,300.0,231.0,clustering)*0.48;
    }
    // Preserve the original direction for decorative sky layers and bodies.
    color+=skyAurora(direction)+skyMeteors(direction);
    color+=GalaxyColor*MeteorSettings.w*exp(-abs(direction.y)*14.0)*0.10;
    skyCelestial(direction,color);
    return color;
}
void skyBlackHoleCinematic(vec3 d,inout vec3 color) {
    if(BlackHoleSettings.x<=0.0) return;
    vec3 center=vec3(sin(BlackHoleSettings.z)*cos(BlackHoleSettings.w),sin(BlackHoleSettings.w),
                     cos(BlackHoleSettings.z)*cos(BlackHoleSettings.w));
    // Yaw basis remains defined at +/-90-degree elevation.
    vec3 side=vec3(cos(BlackHoleSettings.z),0.0,-sin(BlackHoleSettings.z)),up=cross(center,side);
    float alignment=dot(d,center),radius=max(tan(BlackHoleSettings.y),0.001);
    vec2 p=vec2(dot(d,side),dot(d,up))/max(alignment,0.08)/radius;
    float extent=clamp(DiskSettings.y*0.54,2.1,4.8),support=extent+0.8;
    float aa=clamp(max(fwidth(clamp(p.x,-support-1.0,support+1.0)),
                       fwidth(clamp(p.y,-support-1.0,support+1.0))),0.0007,0.035);
    float r=length(p);
    if(alignment<=0.0 || r>=support || abs(p.y)>2.3) return;
    float clock=Time*DiskSettings.z,angle=atan(p.y,p.x);
    // The animated pattern is periodic Cartesian noise, so no longitude seam.
    float spin=clock*0.18,c=cos(spin),s=sin(spin);
    vec2 moving=vec2(c*p.x-s*p.y,s*p.x+c*p.y);
    float gas=skyFbm(vec3(moving*vec2(7.0,4.0),Seed*0.003+clock*0.10-r*0.4),Quality+2);
    float textureAmount=BlackHoleCinematic.w;
    float filament=(0.5+0.5*sin(r*135.0+clock*3.1+gas*textureAmount*5.0));
    filament=0.70+0.30*filament*filament;
    float coreMask=1.0-smoothstep(0.97-aa,0.97+aa,r);
    float absorption=clamp(BlackHoleCinematic.z*0.92,0.0,1.0)*min(BlackHoleSettings.x,1.0);
    color*=1.0-coreMask*absorption;
    float ringWidth=max(BlackHoleAppearance.z,aa*0.70);
    float photon=skyCompactGaussian(r-1.01,max(ringWidth*0.20,aa*0.70),ringWidth*0.80+aa);
    float ring=skyCompactGaussian(r-(1.055+ringWidth*0.75),ringWidth*1.45,ringWidth*4.5+0.05);
    float arcEnvelope=pow(abs(p.y)/max(r,0.001),0.65);
    float bentArc=skyCompactGaussian(r-(1.115+ringWidth*0.85),ringWidth*1.8,ringWidth*5.5+0.09)*arcEnvelope;
    float upperBeaming=0.75+0.25*clamp(p.y/max(r,0.01),-1.0,1.0);
    float ringPattern=mix(1.0,(0.7+gas*0.75)*filament,min(textureAmount,1.0));
    float glow=skyCompactGaussian(r-1.10,0.29+ringWidth,0.90)*(1.0-coreMask);
    float inclination=abs(sin(DiskSettings.x));
    float centralHeight=mix(0.34,0.11,inclination)+0.125*exp(-p.x*p.x*1.2);
    float taper=pow(max(1.0-pow(abs(p.x)/max(extent,0.01),2.0),0.0),0.75);
    float height=max(centralHeight*taper,aa*0.65);
    float disk=skyCompactGaussian(p.y,height,height*3.5+aa)*
               (1.0-smoothstep(extent-0.24,extent,abs(p.x)));
    float diskCore=skyCompactGaussian(p.y,max(height*0.30,aa*0.7),height*1.4+aa)*
                   (1.0-smoothstep(extent*0.70,extent,abs(p.x)));
    float diskGas=skyFbm(vec3(p.x*4.0-clock*0.7,p.y*19.0,Seed*0.013+clock*0.12),Quality+2);
    float diskThreads=0.94+0.06*sin(p.x*24.0-clock*2.2+diskGas*6.0);
    float diskPattern=mix(1.0,(0.45+diskGas*1.1)*diskThreads,min(textureAmount,1.0));
    float diskTaper=0.30+0.70*exp(-pow(p.x/max(extent*0.48,0.1),2.0));
    float beaming=0.87+0.13*sin(angle+clock*0.09);
    vec3 hot=mix(DiskColorB,vec3(1.0),0.68),warm=mix(DiskColorA,DiskColorB,0.35);
    vec3 emission=hot*photon*BlackHoleAppearance.y*3.8;
    emission+=mix(warm,hot,0.50)*ring*BlackHoleAppearance.y*ringPattern*1.4;
    emission+=mix(warm,hot,0.30)*bentArc*BlackHoleCinematic.x*ringPattern*upperBeaming*1.4;
    emission+=warm*glow*BlackHoleAppearance.w*0.35;
    emission+=(warm*disk*1.45+hot*diskCore*2.0)*BlackHoleCinematic.y*diskPattern*beaming*diskTaper;
    // A separate orange gas veil curls outside the sharply focused photon
    // image. Integer angular harmonics are periodic at atan's branch cut.
    float spiral=pow(0.5+0.5*sin(r*24.0+angle*5.0-clock*2.5+gas*6.0),3.0);
    float veilSupport=smoothstep(1.10,1.23,r)*(1.0-smoothstep(1.40,2.07,r));
    float veil=mix(0.22,(0.18+gas*0.85)*spiral,min(textureAmount,1.0))*veilSupport;
    emission+=DiskColorA*veil*BlackHoleAppearance.w*0.36;
    // A finite soft disk glow contributes inside the dark core like the user's
    // reference, while the remainder of the opaque shadow keeps stars hidden.
    float diskGlow=skyCompactGaussian(p.y,0.40,0.90)*(1.0-smoothstep(0.85,extent,abs(p.x)));
    emission+=warm*diskGlow*BlackHoleAppearance.w*BlackHoleCinematic.y*0.14;
    emission+=DiskColorA*coreMask*BlackHoleAppearance.w*0.014;
    float domain=1.0-smoothstep(support-0.28,support,r);
    color+=emission*BlackHoleSettings.x*domain;
}
