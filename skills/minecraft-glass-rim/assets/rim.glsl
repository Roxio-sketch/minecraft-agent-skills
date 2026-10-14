// Original artistic glass rim. Linear RGB, straight alpha, time in seconds.
vec4 glassRim(vec3 n, vec3 v, vec3 base, vec3 edge,
    float power, float gain, float baseAlpha, float rimAlpha,
    float ior, bool fresnelMode, bool twoSided,
    float seconds, float pulseAmount, float pulseHz) {
    n *= inversesqrt(max(dot(n,n), 1e-12));
    v *= inversesqrt(max(dot(v,v), 1e-12));
    float ndv = dot(n,v);
    float facing = clamp(twoSided ? abs(ndv) : ndv, 0.0, 1.0);
    float f0 = pow((max(ior,1.0)-1.0)/(max(ior,1.0)+1.0),2.0);
    float rim = fresnelMode ? f0+(1.0-f0)*pow(1.0-facing,5.0)
        : pow(1.0-facing,max(power,0.05));
    float pulse = 1.0+clamp(pulseAmount,0.0,1.0)*sin(seconds*6.28318530718*max(pulseHz,0.0));
    return vec4(base+edge*rim*max(gain,0.0)*pulse,
        clamp(baseAlpha+rimAlpha*rim,0.0,1.0));
}
