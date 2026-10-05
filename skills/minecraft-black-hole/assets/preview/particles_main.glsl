void main(){
    vec2 uv=texCoord*2.0-1.0;
    // Procedural fragment silhouette.  A compact signed-distance body with a
    // slightly rough perimeter reads as a real dust grain/fragment; the old
    // broad Gaussian made the quad look like a pre-blurred texture.
    float seed=ParticleSeed*6.2831853;
    float theta=atan(uv.y,uv.x);
    float edgeJitter=.055*sin(theta*5.0+seed)+.028*sin(theta*9.0-seed*1.7);
    vec2 shapeUv=uv*vec2(1.0,1.08);
    float radius=length(shapeUv);
    float edge=.72+edgeJitter;
    float body=1.0-smoothstep(edge,edge+.095,radius);
    float core=exp(-18.0*dot(uv,uv));
    float facet=.90+.10*sin(theta*3.0+seed*.73);
    float shape=body*facet*(.62+.38*core);
    if(shape*ParticleAlpha<.008)discard;
    // The same bent-ray core and foreground disk extinction used by the world
    // pass. Stop at this particle; a foreground particle is not hidden by a
    // core that lies farther down the ray. Sprites themselves use straight projection.
    BHResult path=bhTrace(-CenterRelative/WorldScale,normalize(RayPosition));
    float alpha=shape*ParticleAlpha*path.transmission;
    if(alpha<.005)discard;
    float heat=1.0-clamp((ParticleRadius-uCore)/(ParticleOuter-uCore),0.0,1.0);
    float gradientHeat=heat*heat;
    vec3 particleColor=mix(ParticleCold,ParticleHot,gradientHeat);
    if(ParticleGradientPosition>.001){
        float knot=clamp(ParticleGradientPosition,.001,.999);
        particleColor=gradientHeat<knot
            ?mix(ParticleCold,ParticleMid,smoothstep(0.0,knot,gradientHeat))
            :mix(ParticleMid,ParticleHot,smoothstep(knot,1.0,gradientHeat));
    }
    // Keep the tight core bright while limiting the broad halo contribution;
    // the global post-process bloom can still lift intentionally bright dust.
    vec3 source=particleColor*ParticleBrightness*(.32+1.35*heat);
    // Add light in the same linear HDR buffer as the disk. Exposure and bloom
    // are applied once, after every material layer has been composed.
    fragColor=vec4(source*alpha,0.0);
}
