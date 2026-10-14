// Original thin-sheet, three-channel refraction kernel. No third-party shader code.
// p: aspect-correct local material coordinate. Cell centers are time-independent.
vec2 crystalHash(vec2 p, float seed) {
    vec3 q = fract(vec3(p.x, p.y, p.x) * vec3(0.1031, 0.1030, 0.0973) + seed * 0.0017);
    q += dot(q, q.yzx + 19.19);
    return fract((q.xx + q.yz) * q.zy);
}
struct CrystalCell { vec2 id; float edge; };
CrystalCell crystalCell(vec2 p, float seed, float jitter) {
    vec2 base = floor(p);
    float first = 1e10, second = 1e10;
    vec2 id = base;
    for (int y=-2; y<=2; ++y) for (int x=-2; x<=2; ++x) {
        vec2 cell = base + vec2(float(x),float(y));
        vec2 site = cell + 0.5 + (crystalHash(cell,seed)-0.5)*jitter;
        float d = dot(p-site,p-site);
        if (d < first) { second=first; first=d; id=cell; }
        else second=min(second,d);
    }
    CrystalCell result;
    result.id=id;
    // F2-F1 is an artistic seam measure, not an exact signed edge distance.
    result.edge=sqrt(second)-sqrt(first);
    return result;
}
vec2 crystalBend(vec3 incoming, vec3 normal, float index) {
    vec3 transmitted = refract(incoming,normal,1.0/max(index,1.0));
    vec2 before = incoming.xy / max(-incoming.z,0.12);
    vec2 after = transmitted.xy / max(-transmitted.z,0.12);
    return after-before;
}
vec2 crystalBoundUV(vec2 uv, vec2 size) {
    vec2 halfTexel=0.5/max(size,vec2(1));
    return clamp(uv,halfTexel,1.0-halfTexel);
}
vec3 crystalSample(sampler2D sourceTex, vec2 uv, vec2 size, float blur) {
    vec3 center=texture(sourceTex,crystalBoundUV(uv,size)).rgb;
    if (blur<=0.001) return center;
    vec2 d=vec2(blur)/size;
    // Five-tap cross; deliberately bounded, not a large 2D Gaussian kernel.
    return center*0.4 + 0.15*(
        texture(sourceTex,crystalBoundUV(uv+vec2(d.x,0),size)).rgb+
        texture(sourceTex,crystalBoundUV(uv-vec2(d.x,0),size)).rgb+
        texture(sourceTex,crystalBoundUV(uv+vec2(0,d.y),size)).rgb+
        texture(sourceTex,crystalBoundUV(uv-vec2(0,d.y),size)).rgb);
}
