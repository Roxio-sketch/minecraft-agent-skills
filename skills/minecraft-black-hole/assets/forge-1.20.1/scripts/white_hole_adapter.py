"""Deterministic outward-flow extension of the installed artistic kernel.

Black branches retain the original expressions. White ribbons/cinematic fields
use overlapping 64-unit material lifetimes to avoid radial phase wrap jumps.
"""


def kernel(source):
    old = "    return mix(uCoreColor,uCoreEdgeColor,edge*uCoreGradientStrength);"
    assert source.count(old) == 1
    header = "#ifndef BH_CORE_GAIN\n#define BH_CORE_GAIN 1.0\n#endif\n#ifndef BH_CORE_CAPTURE\n#define BH_CORE_CAPTURE(c)\n#endif\n"
    source = source.replace(old, "    return mix(uCoreColor,uCoreEdgeColor,edge*uCoreGradientStrength)*BH_CORE_GAIN;")
    # Observe only the core's own radiance, after foreground disk transmission.
    # The default hook is empty, so item/particle/black compositing is identical.
    import re
    capture = r'(result\.light\+=result\.transmission\*bhCoreEmission\(([^\n]+)\);)'
    source, count = re.subn(capture, lambda match: match.group(1) + '\n            BH_CORE_CAPTURE(result.transmission*bhCoreEmission(' + match.group(2) + '));', source)
    assert count == 4, count
    return header + source


def functions(source):
    start = source.index("vec2 cinematicPattern(float radius,float angle){")
    end = source.index("vec2 lensedDustPattern(float radius,float angle){", start)
    section = source[start:end]
    cinema, ribbon = section.split("vec2 ribbonPattern(float radius,float angle){", 1)
    cinema = cinema.replace("vec2 cinematicPattern(float radius,float angle){", "vec2 cinematicField(float radius,float angle,float transport,float flowSeed){")
    cinema = cinema.replace("mod(uRadialPhase,64.0)", "transport").replace("uSeed", "flowSeed")
    ribbon = "vec2 ribbonField(float radius,float angle,float transport,float flowSeed){" + ribbon
    ribbon = ribbon.replace("uRadialPhase*.16", "transport*.16").replace("uSeed", "flowSeed")
    wrappers = """
// Both source fields advect outward. Crossfading their material lifetimes
// exchanges old/new seeds continuously at 64 and 128 world-local flow units.
vec2 cinematicPattern(float radius,float angle){
    if(uObjectMode<.5)return cinematicField(radius,angle,mod(uRadialPhase,64.0),uSeed);
    float clock=abs(uRadialPhase),local=mod(clock,64.0),parity=mod(floor(clock/64.0),2.0);
    vec2 older=cinematicField(radius,angle,-local,uSeed+37.0*parity);
    vec2 younger=cinematicField(radius,angle,64.0-local,uSeed+37.0*(1.0-parity));
    return mix(older,younger,smoothstep(0.0,1.0,local/64.0));
}
vec2 ribbonPattern(float radius,float angle){
    if(uObjectMode<.5)return ribbonField(radius,angle,uRadialPhase,uSeed);
    float clock=abs(uRadialPhase),local=mod(clock,64.0),parity=mod(floor(clock/64.0),2.0);
    vec2 older=ribbonField(radius,angle,-local,uSeed+37.0*parity);
    vec2 younger=ribbonField(radius,angle,64.0-local,uSeed+37.0*(1.0-parity));
    return mix(older,younger,smoothstep(0.0,1.0,local/64.0));
}
"""
    source = source[:start] + cinema + ribbon + wrappers + source[end:]
    # The original black-hole jet keeps its exact expression and bent-ray host.
    # A white jet is a separate, depth-clipped, straight-ray material below.
    source = source.replace("    if(JetStrength<=0.0001)return vec3(0.0);",
                            "    if(uObjectMode>.5||JetStrength<=0.0001)return vec3(0.0);")
    return source


def world_functions():
    """World-only source: never inject these uniforms/functions into particles.

    JetClock and ShellClock are outward displacement in material-space units.
    Shells repeat at a full radial span, with two half-period-staggered births.
    """
    return r'''

// External white-hole material follows straight camera rays. It must NOT use
// the mass compositor's artistic terrain remapping or its bent-ray depth policy.
// Parameters to these fields are already in the tilted disk's orthonormal space.
float whiteJetStart(){return uCore*.90;}
float whiteJetEnd(){return max(JetLength,whiteJetStart()+.35);}
float whiteShellStart(){return uCore*1.08;}
float whiteShellSpan(){return max(uShellRadius-whiteShellStart(),.35);}
float whiteShellLife(int layer){
    return mod(uShellClock+float(layer)*whiteShellSpan()*.5,whiteShellSpan())/whiteShellSpan();
}
float whiteShellRadius(int layer){return whiteShellStart()+whiteShellLife(layer)*whiteShellSpan();}

vec3 whiteJetField(vec3 q,float footprint){
    if(uObjectMode<.5||JetStrength<=.0001)return vec3(0.0);
    float height=abs(q.y),start=whiteJetStart(),end=whiteJetEnd();
    if(height<start||height>end)return vec3(0.0);
    float progress=clamp((height-start)/(end-start),0.0,1.0);
    float actual=max(JetWidth*(.48+.70*pow(progress,.72)),.005);
    float effective=sqrt(actual*actual+.35*footprint*footprint);
    float radial=length(q.xz);
    float sheath=exp(-pow(radial/effective,2.0));
    float spine=exp(-pow(radial/max(effective*.34,.002),2.0));
    // Both poles use |height|: a material knot moves away from the source on
    // each side. Translating phase by one local length translates the knot by
    // one length; a stationary point never substitutes global flashing.
    float transported=height-start-uJetClock;
    float knots=pow(.5+.5*cos(6.28318530718*transported/.90),4.0);
    float angle=radial>.00001?atan(q.z,q.x):0.0;
    float strands=pow(.5+.5*cos(3.0*angle-3.8*transported),4.0);
    float envelope=smoothstep(0.0,.055,progress)*(1.0-smoothstep(.78,1.0,progress));
    float material=.18+.82*knots;
    vec3 tint=mix(uJetBaseColor,uJetTipColor,smoothstep(.02,.92,progress));
    return 3.2*JetStrength*tint*envelope*(actual/effective)
        *(spine*material+.26*sheath*(.30+.70*strands)*(.40+.60*material));
}
vec3 whiteJetEmission(vec3 q){return whiteJetField(q,0.0);}

vec3 whiteShellLayer(vec3 q,int layer,float footprint){
    if(uObjectMode<.5||uShellStrength<=.0001)return vec3(0.0);
    float radius=length(q),life=whiteShellLife(layer),center=whiteShellRadius(layer);
    float actual=max(uShellWidth,.002),effective=sqrt(actual*actual+.35*footprint*footprint);
    float profile=exp(-pow((radius-center)/effective,2.0))*(actual/effective);
    float envelope=smoothstep(0.0,.075,life)*(1.0-smoothstep(.80,1.0,life));
    // Angular structure belongs to the moving spherical layer. The birth and
    // death are both dark, so wrapping never teleports a visible bright shell.
    vec3 normal=q/max(radius,.0001);
    float grain=.72+.28*pow(.5+.5*sin(13.0*normal.y+7.0*normal.x-5.0*normal.z),2.0);
    return uShellColor*(4.6*uShellStrength*envelope*profile*grain);
}
vec3 whiteShellEmission(vec3 q){
    return whiteShellLayer(q,0,0.0)+whiteShellLayer(q,1,0.0);
}

float whiteCoreGlowBound(){return uCore*max(uCoreGlowRadius,1.05);}
vec3 whiteCoreGlowField(vec3 q,float footprint){
    // This is the core's own stationary corona, independent of disk halos,
    // jets and shell color. A dark source cannot radiate a colored corona.
    float source=max(max(uCoreColor.r,uCoreColor.g),uCoreColor.b);
    if(uObjectMode<.5||uCoreGlow<=.0001||uCoreEmission<=.0001||source<=.000001)return vec3(0.0);
    float radius=length(q),bound=whiteCoreGlowBound();
    if(radius<uCore||radius>=bound)return vec3(0.0);
    float actual=max(bound-uCore,.001),effective=sqrt(actual*actual+.35*footprint*footprint);
    float offset=max(radius-uCore,0.0);
    float profile=exp(-4.5*pow(offset/effective,2.0))*(actual/effective);
    // The configured radius is a strict support bound, with a smooth zero at
    // its edge. Footprint reconstruction cannot extend it into another ring.
    float fade=1.0-smoothstep(max(uCore,bound-max(.25*actual,footprint)),bound,radius);
    vec3 tint=mix(uCoreColor,uCoreEdgeColor,clamp(uCoreGradientStrength,0.0,1.0)*.30);
    return tint*(.62*uCoreGlow*uCoreEmission*profile*fade);
}
vec3 whiteCoreGlowEmission(vec3 q){return whiteCoreGlowField(q,0.0);}
vec3 whiteIntegrateCoreGlow(vec3 ro,vec3 rd,float limit,float footprint){
    if(uCoreGlow<=.0001||uCoreEmission<=.0001||max(max(uCoreColor.r,uCoreColor.g),uCoreColor.b)<=.000001)return vec3(0.0);
    vec2 interval=bhSphere(ro,rd,whiteCoreGlowBound());
    float begin=max(0.0,interval.x),end=min(limit,interval.y);
    if(end<=begin)return vec3(0.0);
    // Intersect the finite soft corona before quadrature; a thin radius
    // multiplier must not vanish between steps of the larger external field.
    float ds=(end-begin)/16.0;vec3 sum=vec3(0.0);
    vec3 previous=whiteCoreGlowField(ro+rd*begin,footprint);
    for(int i=0;i<16;i++){
        float t=begin+float(i)*ds;
        vec3 mid=whiteCoreGlowField(ro+rd*(t+.5*ds),footprint);
        vec3 next=whiteCoreGlowField(ro+rd*(t+ds),footprint);
        sum+=(previous+4.0*mid+next)*(ds/6.0);previous=next;
    }
    return sum;
}

// Analytic finite cylinder support means a narrow jet cannot fall between
// samples of a much larger mass/shell bounding sphere, even at grazing angles.
vec2 whiteJetInterval(vec3 ro,vec3 rd,float footprint){
    float bound=max(JetWidth,.005)*3.60+2.0*footprint;
    float a=dot(rd.xz,rd.xz),b=dot(ro.xz,rd.xz),c=dot(ro.xz,ro.xz)-bound*bound;
    vec2 radial=vec2(-1e8,1e8);
    if(a<.0000001){if(c>0.0)return vec2(-1.0);}
    else{
        float discriminant=b*b-a*c;
        if(discriminant<0.0)return vec2(-1.0);
        float h=sqrt(max(discriminant,0.0));radial=vec2((-b-h)/a,(-b+h)/a);
    }
    float end=whiteJetEnd();vec2 axial=vec2(-1e8,1e8);
    if(abs(rd.y)<.0000001){if(abs(ro.y)>end)return vec2(-1.0);}
    else{axial=vec2((-end-ro.y)/rd.y,(end-ro.y)/rd.y);axial=vec2(min(axial.x,axial.y),max(axial.x,axial.y));}
    return vec2(max(radial.x,axial.x),min(radial.y,axial.y));
}
vec3 whiteIntegrateJet(vec3 ro,vec3 rd,float limit,float footprint){
    if(JetStrength<=.0001)return vec3(0.0);
    vec2 interval=whiteJetInterval(ro,rd,footprint);
    float begin=max(0.0,interval.x),end=min(limit,interval.y);
    if(end<=begin)return vec3(0.0);
    // Segment length follows the thin cross-section OR the axial .90 knot
    // wavelength. This resolves travel at side view and down either pole.
    float wanted=min(.11,max(.012,(max(JetWidth,.005)+footprint)*.44));
    int count=int(clamp(ceil((end-begin)/wanted),8.0,160.0));
    float ds=(end-begin)/float(count);vec3 sum=vec3(0.0);
    vec3 previous=whiteJetField(ro+rd*begin,footprint);
    for(int i=0;i<160;i++){
        if(i>=count)break;
        float t=begin+float(i)*ds;
        vec3 mid=whiteJetField(ro+rd*(t+.5*ds),footprint);
        vec3 next=whiteJetField(ro+rd*(t+ds),footprint);
        sum+=(previous+4.0*mid+next)*(ds/6.0);previous=next;
    }
    return sum;
}
vec3 whiteIntegrateShellSegment(vec3 ro,vec3 rd,float begin,float end,int layer,float footprint){
    if(end<=begin)return vec3(0.0);
    float ds=(end-begin)/12.0;vec3 sum=vec3(0.0);
    vec3 previous=whiteShellLayer(ro+rd*begin,layer,footprint);
    for(int i=0;i<12;i++){
        float t=begin+float(i)*ds;
        vec3 mid=whiteShellLayer(ro+rd*(t+.5*ds),layer,footprint);
        vec3 next=whiteShellLayer(ro+rd*(t+ds),layer,footprint);
        sum+=(previous+4.0*mid+next)*(ds/6.0);previous=next;
    }
    return sum;
}
vec3 whiteIntegrateShell(vec3 ro,vec3 rd,float limit,int layer,float footprint){
    float center=whiteShellRadius(layer);
    float width=sqrt(max(uShellWidth,.002)*max(uShellWidth,.002)+.35*footprint*footprint);
    // Intersect each thin shell separately and integrate BOTH front and back
    // bands. No giant uniform march can skip an expanding thin layer here.
    vec2 outer=bhSphere(ro,rd,center+3.0*width);
    float begin=max(0.0,outer.x),end=min(limit,outer.y);
    if(end<=begin)return vec3(0.0);
    vec2 inner=bhSphere(ro,rd,max(0.0,center-3.0*width));
    if(inner.y<inner.x||inner.y<0.0||inner.x==inner.y)
        return whiteIntegrateShellSegment(ro,rd,begin,end,layer,footprint);
    return whiteIntegrateShellSegment(ro,rd,begin,min(end,inner.x),layer,footprint)
        +whiteIntegrateShellSegment(ro,rd,max(begin,inner.y),end,layer,footprint);
}

vec3 whiteOutflowTrace(vec3 ro,vec3 rd,float limit,float pixelAngle){
    if(uObjectMode<.5||(JetStrength<=.0001&&uShellStrength<=.0001&&uCoreGlow<=.0001)||limit<=0.0)return vec3(0.0);
    // Emission behind the luminous solid core does not leak through it.
    if(length(ro)<uCore*.999)return vec3(0.0);
    vec2 core=bhSphere(ro,rd,uCore);
    if(core.x>=0.0)limit=min(limit,core.x);
    if(limit<=0.0)return vec3(0.0);
    float angle=radians(-uTilt);
    vec3 origin=bhRotateX(ro,angle),direction=bhRotateX(rd,angle);
    float extent=max(max(whiteJetEnd(),uShellRadius+3.0*uShellWidth),whiteCoreGlowBound());
    // Screen footprint widens reconstruction only, with conserved radiance.
    float footprint=max(.001,min(.40,pixelAngle*max(length(ro)+extent,1.0)));
    vec3 light=whiteIntegrateJet(origin,direction,limit,footprint);
    if(uShellStrength>.0001)for(int layer=0;layer<2;layer++)
        light+=whiteIntegrateShell(origin,direction,limit,layer,footprint);
    if(uCoreGlow>.0001)light+=whiteIntegrateCoreGlow(origin,direction,limit,footprint);
    return light;
}
'''
