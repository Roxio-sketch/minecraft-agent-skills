// Original local-environment research kernel. MIT. Units: blocks; negative SDF inside.
// Caller transforms camera-relative positions into stable region/material coordinates.
float leBox(vec3 p, vec3 h) {
    vec3 q=abs(p)-h;
    return length(max(q,0.0))+min(max(q.x,max(q.y,q.z)),0.0);
}
float leCapsule(vec3 p, vec3 a, vec3 b, float radius) {
    vec3 ab=b-a;
    float t=clamp(dot(p-a,ab)/max(dot(ab,ab),1e-8),0.0,1.0);
    return length(p-a-ab*t)-radius;
}
float leHash(vec3 p) {
    p=fract(p*vec3(.1031,.1030,.0973));p+=dot(p,p.yxz+33.33);
    return fract((p.x+p.y)*p.z);
}
float leNoise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(leHash(i),leHash(i+vec3(1,0,0)),f.x),
                   mix(leHash(i+vec3(0,1,0)),leHash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(leHash(i+vec3(0,0,1)),leHash(i+vec3(1,0,1)),f.x),
                   mix(leHash(i+vec3(0,1,1)),leHash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float leSdf(vec3 p, int shape, vec3 extent, vec3 endpoint) {
    if(shape==0) return length(p)-extent.x;
    if(shape==1) {vec2 d=vec2(length(p.xz)-extent.x,abs(p.y)-extent.y);
        return length(max(d,0.0))+min(max(d.x,d.y),0.0);}
    if(shape==2) return leBox(p,extent);
    if(shape==3) return abs(p.y)-extent.y; // finite-thickness plane, caller bounds its support
    if(shape==4) return leCapsule(p,vec3(0),endpoint,extent.x);
    return length(p)-extent.x + (leNoise(p*.65)-.5)*extent.y; // bounded SDF perturbation, not exact distance
}
float leCoverage(float d,float feather){return 1.0-smoothstep(-max(feather,1e-5),0.0,d);}
vec3 leStars(vec2 p,float time) {
    vec3 c=vec3(.008,.014,.04);
    for(int l=0;l<6;l++) {
        float k=1.0+float(l)*.61;
        vec2 v=p*k+vec2(float(l)*31.7,float(l)*17.3);
        vec2 cell=floor(v), f=fract(v)-.5;
        float h=leHash(vec3(cell,float(l)));
        float star=(1.0-smoothstep(.015,.095,length(f)))*step(.88,h);
        c+=star*mix(vec3(.3,.55,1),vec3(1,.83,.56),h)*(.7+.3*sin(time*.5+h*32.0));
    }
    return c;
}
vec3 leMaterial(vec3 p,vec3 n,int material,float time) {
    vec3 w=pow(abs(n),vec3(4));w/=max(w.x+w.y+w.z,1e-5);
    float noise=leNoise(p*.62);
    if(material==0) {
        vec3 stars=leStars(p.yz,time)*w.x+leStars(p.xz,time)*w.y+leStars(p.xy,time)*w.z;
        return stars+mix(vec3(.015,.01,.07),vec3(.055,.11,.20),noise)*noise;
    }
    if(material==1) { // original ink proof: paper/ink islands, no claim of full art direction
        float ink=smoothstep(.43,.59,noise+.12*sin(p.x*2.0+p.z));
        return mix(vec3(.86,.84,.76),vec3(.025,.03,.04),ink)*(.72+.28*abs(n.y));
    }
    float fissure=1.0-smoothstep(.03,.12,abs(noise-.5));
    return mix(vec3(.025,.008,.007),vec3(1,.21,.015),fissure)*(.9+.1*sin(time));
}
