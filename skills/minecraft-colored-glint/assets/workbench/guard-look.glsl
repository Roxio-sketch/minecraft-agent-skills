// Generated from guard-look.js; shared equipment coordinates, standard glint uniforms required.

float guardSquared(float x) { return x * x; }
float guardHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float guardNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(guardHash(i), guardHash(i + vec3(1,0,0)), f.x),
            mix(guardHash(i + vec3(0,1,0)), guardHash(i + vec3(1,1,0)), f.x), f.y),
        mix(mix(guardHash(i + vec3(0,0,1)), guardHash(i + vec3(1,0,1)), f.x),
            mix(guardHash(i + vec3(0,1,1)), guardHash(i + vec3(1,1,1)), f.x), f.y), f.z);
}

vec3 guardWaterSheen(vec3 localPosition, float seconds) {
    // All animation derives from this time: speed zero freezes the full look.
    float t = seconds * max(uSpeed, 0.0);
    float a = radians(uAngle);
    vec2 forward = vec2(sin(a), cos(a));
    vec2 across = vec2(cos(a), -sin(a));
    vec2 plane = localPosition.xy + vec2(localPosition.z * 0.12, localPosition.z * 0.18);
    float longitudinal = dot(plane, forward);
    float transverse = dot(plane, across);
    float size = clamp(uScale, 0.25, 3.0);
    float width = clamp(uWidth, 0.025, 0.40);

    // One pass through a 1.1-unit chest takes about 3.2 s at speed one. Use
    // a chest-sized cycle so the intervening quiet gap remains short, rather
    // than spending several seconds traversing empty full-body coordinates.
    float cycle = floor(t / 5.2 + 0.355);
    float phase = fract(t / 5.2 + 0.355);
    float centre = 0.90 - phase * 1.80;
    float sidestep = 0.09 * sin(cycle * 1.71 + 0.4) + 0.055 * sin(t * 0.48);
    float x = transverse - sidestep;
    float headCurve = 0.035 * sin(x * (7.0 * size) + t * 0.52)
                    + 0.014 * sin(x * (19.0 * size) - t * 0.31);
    float q = longitudinal - centre - headCurve;

    float broad = 0.27 + width * 0.72;
    float lengthScale = 0.52 + width * 3.2;
    float bodyNoise = guardNoise(localPosition * (5.0 * size) + vec3(0.0, -t * 0.09, 0.0));
    float breadth = broad * (0.80 + bodyNoise * 0.34);
    float acrossMask = exp(-pow(abs(x) / breadth, 4.0));
    // The light travels toward decreasing q: short leading edge, longer tail.
    float front = smoothstep(-0.045 * lengthScale, 0.018 * lengthScale, q);
    float tail = exp(-max(q, 0.0) / (0.125 * lengthScale));
    float tailEnd = 1.0 - smoothstep(0.26 * lengthScale, 0.40 * lengthScale, q);
    float mainPatch = acrossMask * front * tail * tailEnd;

    // Soft broken internal folds make a patch of moving light, not a tiled
    // bar or water texture. All folds stay inside the sparse primary mask.
    float folds = 0.76 + 0.24 * sin(q * (25.0 * size) + x * (9.0 * size)
                                  + bodyNoise * 4.0 - t * 0.30);
    float blueLight = mainPatch * folds * (0.30 + 0.14 * bodyNoise);
    float shortHead = exp(-guardSquared((q - 0.025 * lengthScale) / (0.028 * lengthScale)));
    float brokenHead = smoothstep(0.34, 0.70, guardNoise(
        vec3(x * 9.0 * size, localPosition.z * 5.0 * size, t * 0.14 + cycle * 7.0)));
    float palePeak = shortHead * acrossMask * brokenHead * 0.216;

    // Three seeds, not a repeating grid. At most a few tiny, dim accents are
    // present; each glides with a short tail and fades in/out over its pass.
    float flecks = 0.0;
    for (int i = 0; i < 3; ++i) {
        float seed = float(i);
        float fp = fract(t / (6.9 + seed * 0.8) + 0.12 + seed * 0.313);
        float fy = 1.50 - fp * 3.0;
        float fx = (seed - 1.0) * 0.34 + 0.065 * sin(fp * 4.0 + seed * 2.3);
        float fq = longitudinal - fy;
        float fr = transverse - fx;
        float radius = (0.010 + width * 0.030) / sqrt(size);
        float head = exp(-guardSquared(fr / radius) - guardSquared(fq / (radius * 1.25)));
        float tinyTail = exp(-guardSquared(fr / (radius * 0.65)))
                        * smoothstep(-radius, radius, fq)
                        * exp(-max(fq, 0.0) / (radius * 4.0));
        float gate = smoothstep(0.10, 0.24, fp) * (1.0 - smoothstep(0.69, 0.85, fp));
        float zShape = 0.75 + 0.25 * cos(localPosition.z * 8.0 + seed);
        flecks += (head * 0.40 + tinyTail * 0.16) * gate * zShape;
    }

    return max(vec3(0.0),
        (uColor * blueLight + uColor2 * palePeak
         + mix(uColor, uColor2, 0.65) * flecks * clamp(uSecondary, 0.0, 1.5))
        * max(uIntensity, 0.0));
}

vec3 guardImpact(vec3 localPosition, vec3 localNormal, vec3 hitPosition, float hitAge) {
    if (hitAge < 0.0 || hitAge > 0.8) return vec3(0.0);
    // This preview's hit is on a front/back chest plate. The position sign
    // selects its side; a future ray picker should also supply the hit normal.
    vec3 hitNormal = vec3(0.0, 0.0, hitPosition.z >= 0.0 ? 1.0 : -1.0);
    float facing = pow(max(dot(normalize(localNormal), hitNormal), 0.0), 6.0);
    vec3 delta = localPosition - hitPosition;
    float planeDistance = dot(delta, hitNormal);
    vec3 tangentDelta = delta - hitNormal * planeDistance;
    float radial = length(tangentDelta);
    float planeMask = exp(-guardSquared(planeDistance / 0.075));
    float localMask = 1.0 - smoothstep(0.34, 0.61, length(delta));
    float phase = clamp(hitAge / 0.8, 0.0, 1.0);
    float radius = 0.022 + hitAge * 0.70;
    float angle = radial > 0.00001 ? atan(tangentDelta.y, tangentDelta.x) : 0.0;
    float irregular = 0.010 * sin(angle * 3.0 + 0.8) + 0.005 * sin(angle * 7.0 - 0.3);
    float ringWidth = 0.014 + phase * 0.009;
    float ring = exp(-guardSquared((radial + irregular - radius) / ringWidth));
    float ageFade = pow(1.0 - phase, 1.45);
    float launch = 1.0 - smoothstep(0.07, 0.16, hitAge);
    float contact = exp(-guardSquared(radial / 0.048)) * launch;
    float mask = facing * planeMask * localMask;
    vec3 waveColor = mix(uColor, uColor2, 0.23);
    return waveColor * (ring * 0.70 + contact * 0.28)
           * ageFade * mask * max(uIntensity, 0.0);
}

// Art-directed surface composition shared by the preview and future adapters.
// Brightness shaping keeps a blue core on light armor without tinting the air.
vec3 guardSurfaceLight(vec3 baseRadiance, vec3 sheen) {
    return baseRadiance + sheen * sheen * 3.0;
}
