/**
 * Three richer Spirit Guard enchantment-light directions.
 * Position is shared by every armor part, with the equipment height normalized
 * to 1.12 units. Impact receives the original, unnormalized local position.
 * These are sparse light layers; they never replace the armor's base material.
 */
export const RICH_PRESETS = [
  {
    id: 'guard-woven-tide', style: 9,
    name: '灵潮织甲', tag: '柔扫 · 细流 · 肩芒',
    description: '水光主流之外，两道不对称细流错峰游动，肩侧短芒偶尔回应；主流退去时仍有细节在活动。',
    color: '#88C8F3', color2: '#CDEEFF',
    intensity: 0.94, speed: 1, scale: 1, width: 0.13,
    angle: -18, secondary: 0.72,
  },
  {
    id: 'guard-wandering-dragon', style: 10,
    name: '游龙护纹', tag: '六缕交织 · 四周上行',
    description: '六缕淡蓝细光从装备四周交叉绕行，亮段带着柔尾向上游走；正面、侧面和背面都有护纹，光纹之间保留原甲片。',
    color: '#7DC6F8', color2: '#D0F1FF',
    intensity: 0.96, speed: 0.90, scale: 1, width: 0.14,
    angle: 12, secondary: 0.65,
  },
  {
    id: 'guard-gathered-light', style: 11,
    name: '凝光护甲', tag: '偏心短弧 · 节点接力',
    description: '三段疏落的偏心护光缓慢游走，两颗微亮节点接力；受击时短弧向受击位置汇聚，再随波纹散去。',
    color: '#91CEF6', color2: '#D2F3FF',
    intensity: 0.96, speed: 1, scale: 1, width: 0.12,
    angle: -12, secondary: 0.75,
  },
];

// Host dependencies: guardNoise, guardWaterSheen, guardImpact and the standard
// uColor/uColor2/uIntensity/uSpeed/uScale/uWidth/uAngle/uSecondary uniforms.
export const GUARD_VARIANTS_GLSL = /* glsl */ `
float guardRichAngle(float a) {
    return atan(sin(a), cos(a));
}

float guardRichGaussian(float q, float width) {
    float w = max(width, 0.0001);
    float d = q / w;
    return exp(-d * d);
}

vec2 guardRichPlane(vec3 p) {
    float a = radians(uAngle);
    vec2 plane = p.xy + p.z * vec2(0.10, 0.13);
    return vec2(dot(plane, vec2(cos(a), -sin(a))),
                dot(plane, vec2(sin(a), cos(a))));
}

float guardRichPass(float y, float centre, float tailLength) {
    float q = y - centre;
    float lead = smoothstep(-0.036, 0.012, q);
    float tail = exp(-max(q, 0.0) / max(tailLength, 0.01));
    return lead * tail * (1.0 - smoothstep(tailLength * 2.0, tailLength * 3.2, q));
}

vec3 guardWovenTide(vec3 p, float seconds) {
    float t = seconds * max(uSpeed, 0.0);
    float scale = clamp(uScale, 0.25, 3.0);
    float width = clamp(uWidth, 0.025, 0.40);
    float auxiliary = clamp(uSecondary, 0.0, 1.5);
    vec2 plane = guardRichPlane(p);
    // Preserve the broad water sweep, with a little room for the new details.
    vec3 light = guardWaterSheen(p, seconds) * 0.87;
    float fineFlow = 0.0;
    float fineHeads = 0.0;
    for (int i = 0; i < 2; ++i) {
        float seed = float(i);
        float period = mix(1.70, 2.30, seed);
        float phase = fract(t / period + mix(0.17, 0.64, seed));
        float centre = 0.98 - phase * 1.96;
        float path = mix(-0.31, 0.35, seed)
                   + 0.085 * sin(plane.y * 7.8 * scale + t * 0.61 + seed * 2.3)
                   + 0.025 * sin(plane.y * 17.0 * scale - t * 0.38 + seed);
        float curve = guardRichGaussian(plane.x - path, 0.013 + width * 0.105);
        float q = plane.y - centre;
        float gate = smoothstep(0.02, 0.10, phase)
                   * (1.0 - smoothstep(0.91, 0.99, phase));
        float segment = guardRichPass(plane.y, centre, 0.11 + width * 0.28);
        float folds = 0.80 + 0.20 * sin(q * 26.0 * scale + seed * 2.1);
        fineFlow += curve * segment * folds * gate;
        fineHeads += curve * guardRichGaussian(q - 0.018, 0.027) * gate;
    }

    float shoulder = 0.0;
    for (int i = 0; i < 2; ++i) {
        float side = float(i) * 2.0 - 1.0;
        float sx = side * (0.49 + 0.018 * sin(t * 0.68 + side));
        float sy = 0.37 + 0.028 * sin(t * 0.83 + side * 1.7);
        float dx = plane.x - sx;
        float dy = plane.y - sy + dx * side * 0.27;
        float pulse = smoothstep(0.34, 0.96, sin(t * 1.66 + side * 2.2));
        shoulder += guardRichGaussian(dx, 0.13 + width * 0.10)
                  * guardRichGaussian(dy, 0.011 + width * 0.050) * pulse;
    }
    vec3 detail = uColor * (fineFlow * 0.46 + shoulder * 0.42)
                + mix(uColor, uColor2, 0.28) * fineHeads * 0.24;
    return max(light + detail * auxiliary * max(uIntensity, 0.0), vec3(0.0));
}

vec3 guardWanderingDragon(vec3 p, float seconds) {
    float t = seconds * max(uSpeed, 0.0);
    float scale = clamp(uScale, 0.25, 3.0);
    float width = clamp(uWidth, 0.025, 0.40);
    float auxiliary = clamp(uSecondary, 0.0, 1.5);
    float radius = length(p.xz);
    float theta = radius > 0.00001 ? atan(p.z, p.x) : 0.0;
    float rotate = radians(uAngle);
    // Translating the shared height coordinate moves BOTH helix families up.
    // Opposite windings cross each other, with three paths in each family
    // spread over a full turn. No camera-facing or front-only projection.
    float flowY = p.y - t * 0.20;
    float pitch = 5.4 * sqrt(scale);
    float flow = 0.0;
    float heads = 0.0;
    float fineTail = 0.0;
    for (int i = 0; i < 6; ++i) {
        float seed = float(i);
        float family = i < 3 ? 0.0 : 1.0;
        float strand = mod(seed, 3.0);
        float winding = mix(1.0, -1.0, family);
        float offset = strand * 2.0943951 + family * 0.71;
        float curveAngle = rotate + offset + winding * pitch * flowY
                         + 0.15 * sin(flowY * 7.2 * scale + seed * 1.7)
                         + 0.055 * sin(flowY * 14.1 * scale + seed * 2.3);
        float arc = guardRichAngle(theta - curveAngle);
        float angularWidth = (0.010 + width * 0.11) / max(radius, 0.18);
        float ribbon = guardRichGaussian(arc, angularWidth);
        // Each upper-moving head has a softer tail BELOW it. Periodic packets
        // fade to zero before wrapping; staggered offsets avoid a global blink.
        float packet = (fract((flowY + seed * 0.267) / 1.65 + 0.5) - 0.5) * 1.65;
        float behind = -packet;
        float segment = smoothstep(-0.040, 0.012, behind)
                      * exp(-max(behind, 0.0) / 0.23)
                      * (1.0 - smoothstep(0.48, 0.72, behind));
        float head = guardRichGaussian(packet, 0.035 + width * 0.055);
        float weight = mix(1.0, 0.48 + auxiliary * 0.52, family);
        float thread = ribbon * (0.43 + segment * 0.22) * weight;
        // A maximum keeps crossings luminous without summing into white blobs.
        flow = max(flow, thread);
        heads = max(heads, ribbon * head * weight);
        fineTail = max(fineTail,
                      guardRichGaussian(arc + angularWidth * 0.30, angularWidth * 0.30)
                      * (0.38 + segment * 0.62) * weight);
    }
    vec3 light = uColor * (flow + fineTail * 0.10)
               + mix(uColor, uColor2, 0.31) * heads * 0.13;
    return max(light * max(uIntensity, 0.0), vec3(0.0));
}

vec2 guardGatheredCentre(float seed, float t) {
    vec2 centre = seed < 0.5 ? vec2(-0.21, 0.22)
                : seed < 1.5 ? vec2(0.23, -0.015) : vec2(-0.04, -0.29);
    return centre + vec2(0.018 * sin(t * 0.52 + seed * 2.3),
                         0.015 * cos(t * 0.61 + seed * 1.7));
}

vec3 guardGatheredLight(vec3 p, float seconds) {
    float t = seconds * max(uSpeed, 0.0);
    float scale = clamp(uScale, 0.25, 3.0);
    float width = clamp(uWidth, 0.025, 0.40);
    float auxiliary = clamp(uSecondary, 0.0, 1.5);
    vec2 plane = guardRichPlane(p);
    float arcs = 0.0;
    float inner = 0.0;
    float nodes = 0.0;
    for (int i = 0; i < 3; ++i) {
        float seed = float(i);
        vec2 centre = guardGatheredCentre(seed, t);
        vec2 delta = plane - centre;
        float radial = length(delta);
        float polar = radial > 0.00001 ? atan(delta.y, delta.x) : 0.0;
        float radius = (0.135 + seed * 0.012 + width * 0.10) / sqrt(scale);
        float bearing = 0.92 + seed * 2.25 + t * mix(0.37, -0.28, mod(seed, 2.0));
        float da = abs(guardRichAngle(polar - bearing));
        // Forty to seventy degrees of an arc, never a complete ring or grid.
        float span = 0.38 + seed * 0.08;
        float arcGate = 1.0 - smoothstep(span * 0.68, span, da);
        float band = guardRichGaussian(radial - radius, 0.006 + width * 0.065);
        float relay = 0.78 + 0.22 * sin(t * 1.6 - seed * 2.0943951);
        arcs += band * arcGate * relay;
        inner += guardRichGaussian(radial - radius + 0.012, 0.005)
               * arcGate * relay * 0.22;
    }
    // Exactly two node paths, staggered so their brightenings hand off.
    for (int i = 0; i < 2; ++i) {
        float seed = float(i);
        float target = seed * 2.0;
        vec2 centre = guardGatheredCentre(target, t);
        float radius = (0.135 + target * 0.012 + width * 0.10) / sqrt(scale);
        float bearing = 0.92 + target * 2.25 + t * 0.37;
        float swing = 0.28 * sin(t * 0.88 + seed * 2.1);
        vec2 node = centre + vec2(cos(bearing + swing), sin(bearing + swing)) * radius;
        float pulse = 0.26 + 0.74 * pow(0.5 + 0.5 * sin(t * 2.0 - seed * 3.1415927), 2.0);
        nodes += guardRichGaussian(length(plane - node), 0.014 + width * 0.026) * pulse;
    }
    vec3 light = uColor * arcs * 0.68
               + mix(uColor, uColor2, 0.18) * inner * 0.48
               + mix(uColor, uColor2, 0.33) * nodes * auxiliary * 0.76;
    return max(light * max(uIntensity, 0.0), vec3(0.0));
}

vec3 guardRichSheen(int style, vec3 localPosition, float seconds) {
    if (style == 9) return guardWovenTide(localPosition, seconds);
    if (style == 10) return guardWanderingDragon(localPosition, seconds);
    if (style == 11) return guardGatheredLight(localPosition, seconds);
    return vec3(0.0);
}

vec3 guardRichResponse(int style, vec3 p, vec3 normal, vec3 hit, float age) {
    if (age < 0.0 || age > 0.8) return vec3(0.0);
    vec3 wave = guardImpact(p, normal, hit, age);
    vec3 hitNormal = vec3(0.0, 0.0, hit.z >= 0.0 ? 1.0 : -1.0);
    float facing = pow(max(dot(normalize(normal), hitNormal), 0.0), 6.0);
    vec3 delta = p - hit;
    float planeDistance = dot(delta, hitNormal);
    vec3 tangent = delta - planeDistance * hitNormal;
    float radial = length(tangent);
    float polar = radial > 0.00001 ? atan(tangent.y, tangent.x) : 0.0;
    float mask = facing * guardRichGaussian(planeDistance, 0.075)
               * (1.0 - smoothstep(0.29, 0.48, length(delta)));
    float phase = clamp(age / 0.8, 0.0, 1.0);
    float fade = sin(phase * 3.1415927) * pow(1.0 - phase, 0.75);
    float auxiliary = clamp(uSecondary, 0.0, 1.5);
    float response = 0.0;
    if (style == 11) {
        // Three short inward arc fragments gather once toward the impact.
        float radius = mix(0.28, 0.035, smoothstep(0.0, 0.72, phase));
        float fragments = pow(max(0.0, cos(polar * 3.0 + phase * 1.1)), 5.0);
        response = guardRichGaussian(radial - radius, 0.012 + phase * 0.006)
                 * fragments * 0.47;
    } else if (style == 10) {
        float radius = 0.055 + phase * 0.24;
        float fragments = pow(max(0.0, cos(polar * 2.0 - phase * 2.3)), 7.0);
        response = guardRichGaussian(radial - radius, 0.011) * fragments * 0.34;
    } else if (style == 9) {
        float radius = mix(0.22, 0.035, smoothstep(0.0, 0.65, phase));
        float fragments = pow(max(0.0, cos(polar * 3.0 - 0.8)), 9.0);
        response = guardRichGaussian(radial - radius, 0.017) * fragments * 0.30;
    }
    return max(wave + mix(uColor, uColor2, 0.25) * response * mask * fade
                     * auxiliary * max(uIntensity, 0.0), vec3(0.0));
}
`;
