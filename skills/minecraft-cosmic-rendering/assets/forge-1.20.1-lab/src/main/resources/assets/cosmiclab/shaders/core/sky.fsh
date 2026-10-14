#version 150
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform int Mode;
uniform int Quality;
uniform float Time;
in vec2 screenUV;
out vec4 fragColor;

// All random fields are defined in world direction space, without camera translation.
vec3 hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i).x, hash3(i + vec3(1,0,0)).x, f.x),
                   mix(hash3(i + vec3(0,1,0)).x, hash3(i + vec3(1,1,0)).x, f.x), f.y),
               mix(mix(hash3(i + vec3(0,0,1)).x, hash3(i + vec3(1,0,1)).x, f.x),
                   mix(hash3(i + vec3(0,1,1)).x, hash3(i + vec3(1,1,1)).x, f.x), f.y), f.z);
}
float fbm(vec3 p, int octaves) {
    float value = 0.0, amplitude = 0.5;
    for (int i = 0; i < 6; ++i) {
        if (i >= octaves) break;
        value += noise3(p) * amplitude;
        p = p * 2.03 + vec3(13.1, 7.7, 19.3);
        amplitude *= 0.5;
    }
    return value;
}
vec3 starLayer(vec3 direction, float frequency, float seed) {
    vec3 p = direction * frequency;
    vec3 cell = floor(p);
    float footprint = max(length(dFdx(p)), length(dFdy(p)));
    vec3 color = vec3(0.0);
    // The 3D neighborhood avoids longitude seams and polar UV stretching.
    for (int x = -1; x <= 1; ++x)
    for (int y = -1; y <= 1; ++y)
    for (int z = -1; z <= 1; ++z) {
        vec3 id = cell + vec3(x,y,z);
        vec3 random = hash3(id + seed);
        vec3 center = id + 0.15 + random * 0.7;
        float radius = mix(0.025, 0.095, random.z * random.z);
        float width = max(radius, footprint * 0.55);
        float distanceToStar = length(p - center);
        float core = exp(-distanceToStar * distanceToStar / (width * width));
        float energy = radius * radius / (width * width);
        vec3 tint = mix(vec3(0.58,0.73,1.0), vec3(1.0,0.80,0.56), random.x);
        float twinkle = 0.86 + 0.14 * sin(Time * (0.7 + random.x) + random.y * 30.0);
        color += core * energy * tint * mix(3.0, 10.0, random.y) * twinkle;
    }
    return color;
}
void main() {
    vec4 point = InverseProjection * vec4(screenUV * 2.0 - 1.0, -1.0, 1.0);
    vec3 direction = normalize(InverseViewRotation * (point.xyz / point.w));
    if (Mode == 0) {
        fragColor = vec4(direction * 0.5 + 0.5, 1.0);
        return;
    }
    vec3 color = vec3(0.002, 0.004, 0.012);
    if (Mode == 2) {
        vec3 p = direction * 3.0;
        float warp = fbm(p + vec3(0.0,5.3,1.7), Quality + 2);
        float dust = fbm(p * 2.3 + warp * 1.3, Quality + 3);
        float band = exp(-pow(dot(direction, normalize(vec3(0.3,0.8,-0.45))) * 3.7, 2.0));
        float density = smoothstep(0.25, 0.76, dust) * band;
        vec3 tint = mix(vec3(0.04,0.12,0.28), vec3(0.40,0.08,0.31), warp);
        color += tint * density * 1.8;
        color += vec3(0.29,0.40,0.51) * pow(density, 3.0) * 1.5;
    }
    color += starLayer(direction, 75.0, 11.0);
    if (Quality >= 2) color += starLayer(direction, 170.0, 97.0) * 0.8;
    if (Quality >= 3) color += starLayer(direction, 350.0, 231.0) * 0.55;
    // Simple bounded display transform; this lab does not claim a physical HDR pipeline.
    color = vec3(1.0) - exp(-color * 1.35);
    fragColor = vec4(color, 1.0);
}
