void main(){
    vec2 uv=texCoord*2.0-1.0;
    float shape=exp(-3.5*dot(uv,uv))*(1.0-smoothstep(.7,1.0,abs(uv.x)))*(1.0-smoothstep(.65,1.0,abs(uv.y)));
    if(shape*ParticleAlpha<.008)discard;
    // The same bent-ray core and foreground disk extinction used by the world
    // pass. Stop at this particle; a foreground particle is not hidden by a
    // core that lies farther down the ray. Sprites themselves use straight projection.
    BHResult path=bhTrace((CameraOriginRelative-CenterRelative)/WorldScale,normalize(RayPosition-CameraOriginRelative));
    float alpha=shape*ParticleAlpha*path.transmission;
    if(alpha<.005)discard;
    float heat=1.0-clamp((ParticleRadius-uCore)/(ParticleOuter-uCore),0.0,1.0);
    vec3 source=mix(ParticleCold,ParticleHot,heat*heat)*ParticleBrightness*(.4+1.6*heat);
    vec3 light=1.0-exp(-source);
    light=mix(light*12.92,1.055*pow(light,vec3(1.0/2.4))-.055,step(vec3(.0031308),light));
    fragColor=vec4(light*alpha,0.0);
}
