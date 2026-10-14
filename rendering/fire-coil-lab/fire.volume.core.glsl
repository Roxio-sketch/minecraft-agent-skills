// Original three-dimensional fire field, shared verbatim by WebGL2 and Forge.
// Body-local metres. Density and heat are independent of integration quality.
uniform float Time;
uniform float Radius;
uniform float Height;
uniform float Turns;
uniform float Width;
uniform float RotationSpeed;
uniform float RiseSpeed;
uniform float WaveAmplitude;
uniform float WaveSpeed;
uniform float Turbulence;
uniform float TongueLength;
uniform float Erosion;
uniform float VolumeStrength;
uniform int Strands;
uniform int BodyEnabled;
uniform float WeaponRadius;
uniform float WeaponWidth;
uniform float WeaponTurns;
uniform float WeaponStrength;
uniform int WeaponCount;
// body-local metres, orthonormal axes: xyz=base, w=actual model length.
uniform vec4 WeaponOrigins[2];
uniform vec4 WeaponAxes[2];
uniform vec4 WeaponSides[2];
vec3 DensityStep;
uniform int Seed;
uniform vec3 CoreColor;
uniform vec3 MidColor;
uniform vec3 EdgeColor;
uniform int WispCount;
uniform vec4 Wisps[24];
uniform vec4 WispVelocity[24];
uniform vec4 WispMeta[24];

const float FC_PI = 3.14159265359;
const float FC_TAU = 6.28318530718;

float fvHash(vec3 p) {
    p = fract(p * vec3(.1031, .1030, .0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float fvNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f*f*f*(f*(f*6.0-15.0)+10.0);
    return mix(mix(mix(fvHash(cell), fvHash(cell+vec3(1,0,0)), f.x),
                   mix(fvHash(cell+vec3(0,1,0)), fvHash(cell+vec3(1,1,0)), f.x), f.y),
               mix(mix(fvHash(cell+vec3(0,0,1)), fvHash(cell+vec3(1,0,1)), f.x),
                   mix(fvHash(cell+vec3(0,1,1)), fvHash(cell+vec3(1,1,1)), f.x), f.y), f.z);
}

float fvFbm(vec3 p) {
    return .53*fvNoise(p) + .27*fvNoise(p*2.03+vec3(17.1,5.7,31.3))
         + .13*fvNoise(p*4.11+vec3(9.4,23.2,7.8))
         + .07*fvNoise(p*8.17+vec3(27.7,13.6,41.1));
}

// Average a Gaussian fibre over the actual integration segment. This resolves
// thin cores without screen blur, random jitter or brighter sampling beads.
float fvErf(float x){float a=abs(x),t=1.0/(1.0+.3275911*a);float y=1.0-(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-.284496736)*t+.254829592)*t*exp(-a*a);return sign(x)*y;}
float fvFibre(vec2 delta,vec2 segment,float width){
 float lengthSegment=length(segment),w=max(width,.0015);
 if(lengthSegment<.00005)return exp(-dot(delta,delta)/(w*w));
 vec2 direction=segment/lengthSegment;float along=dot(delta,direction),across=max(0.,dot(delta,delta)-along*along);
 float integral=.886226925*w*(fvErf((along+lengthSegment*.5)/w)-fvErf((along-lengthSegment*.5)/w))/lengthSegment;
 return max(0.,integral)*exp(-across/(w*w));
}

// Analytic cyclic flow. Two spatial scales and midpoint backtracing keep local
// curls alive independently of the body's rotation; this is not a fluid solver.
vec3 fvFlow(vec3 p, float phase) {
    vec3 a = p*3.2 + vec3(phase*.37, -phase*.29, phase*.23);
    vec3 b = p*7.1 + vec3(-phase*.41, phase*.31, phase*.53);
    return vec3(sin(a.y)+cos(a.z), sin(a.z)+cos(a.x), sin(a.x)+cos(a.y))*.37
         + vec3(sin(b.y)+cos(b.z), sin(b.z)+cos(b.x), sin(b.x)+cos(b.y))*.13;
}

vec3 fvWarp(vec3 p, float t, float seed) {
    float k = .045*Turbulence;
    vec3 v = fvFlow(p+vec3(seed*1.3,seed*.7,seed*2.1), t*WaveSpeed);
    return p-k*fvFlow(p-.5*k*v+vec3(seed*1.3,seed*.7,seed*2.1), t*WaveSpeed);
}

vec3 fvCenter(float s, float strand, float t, float seed, float radiusInput, float heightInput, float turnsInput, float waveInput, int strandCount) {
    float phase = strand/float(max(strandCount,1));
    float angle = FC_TAU*(s*turnsInput+phase+t*RotationSpeed);
    float wave = s*9.3+t*WaveSpeed*1.4+seed*3.1+strand*2.9;
    float radius = radiusInput + waveInput*(.62*sin(wave)+.38*sin(wave*1.73-1.1));
    angle += .13*Turbulence*sin(wave*.71+strand);
    return vec3(cos(angle)*radius,
                .06+s*heightInput+waveInput*.45*sin(wave*1.31),
                sin(angle)*radius);
}

vec2 fvCoilField(vec3 p,float t,float seed,float h,float radiusInput,float widthInput,float turnsInput,float waveInput,int strandCount,float strength,vec3 segmentStep) {
    float density=0.0,heatWeight=0.0;
    vec3 q=fvWarp(p,t,seed);
    if (strength>.0001 && q.y>-.45 && q.y<h+.65
        && length(q.xz)<radiusInput+widthInput+waveInput+.24) {
        for (int j=0;j<8;j++) {
            if(j>=strandCount) break;
            float strand=float(j);
            float s=clamp((q.y-.06)/h,0.0,1.0);
            vec3 c=fvCenter(s,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount);
            // Project once along the real 3D centreline, then evaluate again.
            float ds=.003;
            vec3 tangent=(fvCenter(s+ds,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount)-fvCenter(s-ds,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount))/(2.0*ds);
            s=clamp(s+dot(q-c,tangent)/max(dot(tangent,tangent),.01),0.0,1.0);
            c=fvCenter(s,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount);
            tangent=normalize(fvCenter(s+ds,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount)-fvCenter(s-ds,strand,t,seed,radiusInput,h,turnsInput,waveInput,strandCount));
            vec3 radial=normalize(vec3(c.x,0,c.z));
            radial=normalize(radial-tangent*dot(radial,tangent));
            vec3 crossAxis=normalize(cross(tangent,radial));
            vec3 delta=q-c;
            float x=dot(delta,radial), z=dot(delta,crossAxis);
            float endFade=pow(max(0.0,sin(s*FC_PI)),.58);
            float width=widthInput*(.39+.11*sin(s*14.7-strand*1.9-t*RiseSpeed*1.1));
            width*=.34+.66*endFade;
            float distance=length(vec2(x,z));
            if(distance>width*1.8 || length(delta)>width*2.1) continue;

            float along=s*(5.8+turnsInput*2.0)-t*RiseSpeed*.72+strand*17.3;
            vec3 flowP=vec3(x/max(width,.012)*1.8,along/max(TongueLength,.1),z/max(width,.012)*1.8)+seed;
            float broad=fvFbm(flowP);
            float fine=fvFbm(flowP*2.2+vec3(1.7,-t*RiseSpeed*.43,4.1));
            float gasShape=exp(-distance*distance/max(width*width*.54,.0001));
            float erosion=smoothstep(.17+Erosion*.28,.46+Erosion*.24,broad+.14*(1.0-distance/max(width,.01)));
            float packetWeight=.12+.88*smoothstep(.23,.66,fvNoise(vec3(along*.62,seed+strand*3.1,t*.13)));
            float gas=gasShape*erosion*packetWeight*(.48+fine*.63)*endFade;

            // Three narrow, independently curling hot fibres inside the gas.
            // Their changing radii and heat keep the coil from becoming a tube.
            float fibres=0.0, hottest=0.0;
            for(int k=0;k<3;k++) {
                float fk=float(k), fphase=along*2.15+fk*2.094+strand*2.7;
                vec2 fc=width*.36*vec2(sin(fphase+fine*1.3),cos(fphase*.83+broad*2.1));
                float fw=width*(.13+.04*sin(fphase*1.31+fk));
                float fd=length(vec2(x,z)-fc);
                vec2 segment=vec2(dot(segmentStep,radial),dot(segmentStep,crossAxis));
                float filament=fvFibre(vec2(x,z)-fc,segment,fw);
                float pulse=smoothstep(.13+Erosion*.27,.47+Erosion*.23,
                                      fvNoise(vec3(along*1.17+fk*7.1,seed+strand*2.3,t*.21)));
                filament*=pulse*endFade;
                fibres+=filament;
                hottest=max(hottest,filament);
            }
            float d=(gas*1.1+fibres*3.5)*strength;
            float heat=clamp(.20+fine*.17+pow(hottest,.57)*.62,0.0,1.0);
            density+=d;
            heatWeight+=d*heat;
        }
    }

    return vec2(density,heatWeight);
}

vec2 fvWeaponTongues(vec3 p,float t,float seed,float lengthBlade,float bladeRadius){
 float density=0.,heatWeight=0.;
 if(p.y<-.15||p.y>lengthBlade+.55||length(p.xz)>bladeRadius+WeaponRadius+WeaponWidth*3.5)return vec2(0.);
 for(int j=0;j<9;j++){
  float k=float(j),clock=t*.58+k*.131,age=fract(clock),birth=floor(clock);
  float random=fvHash(vec3(k,seed,birth)),a=FC_TAU*random+t*RotationSpeed*.23;
  vec3 radial=vec3(cos(a),0,sin(a)),side=vec3(-sin(a),0,cos(a));
  vec3 root=radial*(bladeRadius+WeaponRadius*.25)+vec3(0,.04+lengthBlade*(.08+.75*fvHash(vec3(k,seed+13.,birth))),0);
  vec3 axis=normalize(radial*.76+vec3(0,.54+random*.35,0));
  float tongueLength=WeaponWidth*(1.2+1.1*random)*(.35+.65*age);
  vec3 delta=fvWarp(p-root,t*RiseSpeed,seed+k*2.7);
  float s=dot(delta,axis)/max(tongueLength,.02);
  if(s<0.||s>1.)continue;
  vec3 bend=side*sin(s*4.1-t*WaveSpeed+random*13.)*WeaponWidth*.38*s+radial*sin(s*3.7+t*.7)*WeaponWidth*.18*s;
  vec3 crossDelta=delta-axis*(s*tongueLength)-bend;
  float width=WeaponWidth*(.16+.27*(1.-s)),d=length(crossDelta);
  float noise=fvFbm(delta*vec3(17.,8.,17.)+vec3(seed,-t*RiseSpeed*1.3,k));
  float life=sin(age*FC_PI),ends=smoothstep(0.,.08,s)*(1.-smoothstep(.58,1.,s));
  float gas=exp(-d*d/(width*width))*smoothstep(.20+Erosion*.17,.62,noise)*life*ends;
  float hot=exp(-d*d/max(width*width*.12,.000012))*life*ends;
  float den=(gas*1.2+hot*1.8)*VolumeStrength*WeaponStrength;
  density+=den;heatWeight+=den*clamp(.23+.20*noise+.36*hot,0.,1.);
 }
 return vec2(density,heatWeight);
}

vec2 sampleFireVolume(vec3 p, float t) {
    float seed = float(Seed%997)*.071;
    float density = 0.0, heatWeight = 0.0;
    vec3 q = fvWarp(p,t,seed);
    float h = max(Height,.2);

    if(BodyEnabled!=0){
        vec2 body=fvCoilField(p,t,seed,h,Radius,Width,Turns,WaveAmplitude,Strands,VolumeStrength,DensityStep);
        density+=body.x;heatWeight+=body.y;
    }
    for(int j=0;j<2;j++){
        if(j>=WeaponCount)break;
        vec3 delta=p-WeaponOrigins[j].xyz;
        vec3 axis=WeaponAxes[j].xyz,side=WeaponSides[j].xyz,other=cross(side,axis);
        vec3 localPoint=vec3(dot(delta,side),dot(delta,axis),dot(delta,other));
        float modelRadius=WeaponAxes[j].w;
        vec2 weapon=fvCoilField(localPoint,t,seed+float(j)*2.13,max(.2,WeaponOrigins[j].w),
            modelRadius+WeaponRadius,WeaponWidth,WeaponTurns,WaveAmplitude*.25,Strands,VolumeStrength*WeaponStrength,vec3(dot(DensityStep,side),dot(DensityStep,axis),dot(DensityStep,other)));
        density+=weapon.x;heatWeight+=weapon.y;
        vec2 tongues=fvWeaponTongues(localPoint,t,seed+float(j)*2.13,WeaponOrigins[j].w,modelRadius);
        density+=tongues.x;heatWeight+=tongues.y;
    }

    for(int j=0;j<24;j++) {
        if(j>=WispCount) break;
        float size=max(Wisps[j].w,.01);
        vec3 d=p-Wisps[j].xyz;
        if(dot(d,d)>size*size*11.5) continue;
        float age=clamp(WispMeta[j].x,0.0,1.0), strength=WispMeta[j].y;
        vec3 axis=normalize(WispVelocity[j].xyz*.85+vec3(0,.65,0));
        vec3 side=normalize(cross(axis,abs(axis.y)>.9?vec3(1,0,0):vec3(0,1,0)));
        vec3 other=cross(axis,side);
        float ws=WispVelocity[j].w;
        vec3 v=vec3(dot(d,side),dot(d,axis),dot(d,other))/size;
        float flow=t*RiseSpeed+ws*19.7;
        v=fvWarp(v*.34,flow,ws*7.1)/.34;
        float stretch=.85+TongueLength*.71;
        float y=v.y/stretch;
        vec2 bend=vec2(sin(y*2.7-flow*.8+ws*7.0),cos(y*3.1+flow*.67))*Turbulence*.24;
        vec2 lateral=v.xz-bend*(.7+.3*y);
        float profile=exp(-y*y*1.5);
        float r=.21+.25*profile;
        float n=fvFbm(vec3(lateral*2.9,y*2.0-flow*.7)+ws*37.0);
        float gas=exp(-dot(lateral,lateral)/(r*r))*profile;
        float holes=smoothstep(.18+Erosion*.28,.49+Erosion*.18,n);
        float hot=exp(-dot(lateral,lateral)/(.026+.019*profile))*profile*holes;
        float life=smoothstep(0.0,.11,age)*pow(max(0.0,1.0-age),1.05);
        float den=(gas*.9*holes+hot*5.3)*life*strength;
        float heat=clamp(.22+n*.24+pow(hot,.61)*.70,0.0,1.0);
        density+=den;
        heatWeight+=den*heat;
    }
    return vec2(density,heatWeight/max(density,.00001));
}

vec3 fireVolumeColor(float heat) {
    vec3 color=mix(EdgeColor,MidColor,smoothstep(.15,.59,heat));
    return mix(color,CoreColor,smoothstep(.63,.94,heat));
}
