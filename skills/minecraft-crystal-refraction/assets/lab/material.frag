uniform sampler2D Source;
uniform vec2 Resolution;
uniform float Time, Seed, Cells, Jitter, BendPx, Dispersion, Ior, BevelPx;
uniform float EdgeLight, TintMix, Speed, BlurPx, Amount;
uniform vec3 Tint;
uniform vec2 View;
uniform int Shape, Debug;
in vec2 texCoord;
out vec4 fragColor;
/*KERNEL*/
float crystalWindow(vec2 p) {
    if(Shape==0) return 1.0;
    if(Shape==1) return 1.0-smoothstep(0.70,0.72,length(p));
    float width = 0.12+0.12*clamp((0.78-p.y)/0.25,0.0,1.0);
    float sd=max(abs(p.x)-width,max(p.y-0.78,-0.82-p.y));
    float aa=2.0/Resolution.y;
    return 1.0-smoothstep(-aa,aa,sd);
}
void main() {
    vec2 uv=texCoord;
    vec2 local=(uv-0.5)*vec2(Resolution.x/Resolution.y,1.0)*2.0;
    CrystalCell cell=crystalCell(local*Cells,Seed,Jitter);
    float footprint=max(fwidth(cell.edge),0.0001);
    float seam=1.0-smoothstep(0.0,footprint*max(BevelPx,0.25),cell.edge);
    vec2 h=crystalHash(cell.id+23.0,Seed);
    float angle=h.x*6.2831853 + 0.22*sin(Time*Speed+h.y*6.2831853);
    vec2 tilt=vec2(cos(angle),sin(angle))*(0.32+0.60*h.y);
    vec3 normal=normalize(vec3(tilt,1.0));
    vec3 incoming=normalize(vec3(View,-1.0));
    vec2 baseOffset=crystalBend(incoming,normal,Ior)*BendPx/Resolution;
    vec3 plain=texture(Source,crystalBoundUV(uv,Resolution)).rgb;
    vec3 refracted;
    if(Dispersion<=0.00001) {
        refracted=crystalSample(Source,uv+baseOffset,Resolution,BlurPx);
    } else {
        vec2 red=crystalBend(incoming,normal,max(1.0,Ior-Dispersion))*BendPx/Resolution;
        vec2 blue=crystalBend(incoming,normal,Ior+Dispersion)*BendPx/Resolution;
        refracted=vec3(crystalSample(Source,uv+red,Resolution,BlurPx).r,
                       crystalSample(Source,uv+baseOffset,Resolution,BlurPx).g,
                       crystalSample(Source,uv+blue,Resolution,BlurPx).b);
    }
    refracted *= mix(vec3(1.0),Tint,TintMix);
    refracted += seam*EdgeLight*mix(vec3(1.0),Tint,0.45);
    float mask=crystalWindow(local);
    if(Debug==1) refracted=vec3(h,0.35+0.4*h.x);
    if(Debug==2) refracted=vec3(seam);
    if(Debug==3) refracted=vec3(baseOffset*Resolution/32.0+0.5,0.5);
    fragColor=vec4(mix(plain,refracted,mask*Amount),1.0);
}
