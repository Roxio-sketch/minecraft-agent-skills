
const float SG_PI = 3.141592653589793;

vec2 sgRotate(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c) * p;
}

float sgLine(float distanceToLine, float width) {
    float aa = max(fwidth(distanceToLine) * 0.7, 0.0015);
    return 1.0 - smoothstep(max(0.0, width - aa), width + aa, abs(distanceToLine));
}

float sgBand(float v, float period, float width) {
    // Take derivatives before modulo, keeping antialiasing stable at wrap seams.
    float aa = max(fwidth(v) * 0.7, 0.0015);
    float distanceToLine = abs(mod(v + period * 0.5, period) - period * 0.5);
    return 1.0 - smoothstep(max(0.0, width - aa), width + aa, distanceToLine);
}

float sgCarrier(vec2 uv) {
    vec3 texel = texture(uGlint, uv).rgb;
    float luminance = max(texel.r, max(texel.g, texel.b));
    // Neutral source ranges roughly 0.11..0.67. Remove its dark pedestal.
    return pow(smoothstep(0.19, 0.54, luminance), 1.2);
}

float sgHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 sgHash2(vec2 p) {
    return vec2(sgHash(p), sgHash(p + vec2(83.17, 17.53)));
}

float sgSegment(vec2 p, vec2 a, vec2 b, float width) {
    vec2 v = b - a;
    vec2 nearPoint = a + v * clamp(dot(p - a, v) / dot(v, v), 0.0, 1.0);
    return sgLine(length(p - nearPoint), width);
}

float sgSurfaceRing(vec2 p, float phase, float width) {
    float radius = phase * 1.3;
    float life = smoothstep(0.02, 0.14, phase) * (1.0 - smoothstep(0.76, 1.0, phase));
    return sgLine(length(p) - radius, width) * life;
}

vec3 glintColor(vec2 uv, float timeSeconds) {
    float t = timeSeconds * max(0.0, uSpeed);
    float scale = clamp(uScale, 0.4, 4.0);
    float width = clamp(uWidth, 0.025, 0.4);
    float second = clamp(uSecondary, 0.0, 1.0);
    vec3 color1 = max(uColor, vec3(0.0));
    vec3 color2 = max(uColor2, vec3(0.0));
    vec2 p = sgRotate(uv - vec2(0.5), radians(uAngle)) * scale;
    vec3 light = vec3(0.0);

    if (uStyle == 0) {
        // Two scrolling neutral texture passes, constrained to moving glint lanes.
        float lane1 = sgBand(p.y - t * 0.36, 1.05, width * 0.75);
        float lane2 = sgBand(p.x + t * 0.25 + 0.32, 1.15, width * 0.55);
        float grain1 = sgCarrier(p * 1.45 + vec2(t * 0.14, -t * 0.29));
        float grain2 = sgCarrier(sgRotate(p, 1.12) * 1.25 - vec2(t * 0.2, t * 0.11));
        light = color1 * lane1 * (0.28 + grain1 * 0.9)
              + color2 * lane2 * (0.2 + grain2 * 0.75) * second;
    } else if (uStyle == 1) {
        // A bright scan front with a localized soft shoulder; no constant tint.
        float v = p.y - t * 0.34;
        float front = sgBand(v, 1.3, width * 0.32);
        float shoulder = sgBand(v + width * 0.3, 1.3, width * 1.15);
        float softDistance = abs(mod(v + 0.65, 1.3) - 0.65);
        float soft = exp(-pow(softDistance / max(width * 0.72, 0.025), 2.0)) * shoulder;
        float grain = 0.45 + 0.55 * sgCarrier(p * 1.2 + vec2(0.0, -t * 0.23));
        light = color1 * soft * grain * 0.6 + color2 * front * second * 1.7;
    } else if (uStyle == 2) {
        // Curved fine strands; bright knots travel along their length.
        float curve = 0.23 * sin(p.y * 7.0 - t * 1.8)
                    + 0.08 * sin(p.y * 15.0 + t * 0.9);
        float strand = sgBand(p.x * 4.7 + curve - t * 0.31, 1.0, width * 0.52);
        float knot = sgBand(p.y * 2.8 - t * 0.62 + p.x * 0.45, 1.0, 0.14);
        float grain = 0.5 + 0.5 * sgCarrier(p * 1.8 + vec2(-t * 0.09, t * 0.3));
        light = color1 * strand * grain * 0.56
              + color2 * strand * knot * second * 1.74;
    } else if (uStyle == 3) {
        float a = sgBand(p.x * 2.5 - t * 0.52, 1.0, width * 0.65);
        float b = sgBand(p.y * 2.5 + t * 0.44, 1.0, width * 0.65);
        float grain = 0.5 + sgCarrier(p * 1.7 + vec2(t * 0.17, -t * 0.13)) * 0.5;
        light = color1 * a * grain * 0.65
              + color2 * b * grain * second * 0.75
              + color2 * a * b * second * 0.45;
    } else if (uStyle == 4) {
        // UV-surface rings only: this does not create a surrounding mesh or dome.
        float phase1 = fract(t * 0.24);
        float phase2 = fract(t * 0.24 + 0.5);
        float ring1 = sgSurfaceRing(p, phase1, width * 0.55);
        float ring2 = sgSurfaceRing(p, phase2, width * 0.4);
        float grain = 0.45 + sgCarrier(p * 1.65 + vec2(t * 0.07, -t * 0.08)) * 0.55;
        light = (color1 * ring1 + color2 * ring2 * second) * grain;
    } else if (uStyle == 5) {
        // Small travelling angular runes. Their motion and inner light are independent.
        vec2 cells = p * 3.6 + vec2(t * 0.23, -t * 0.16);
        vec2 cell = floor(cells);
        vec2 local = fract(cells) - vec2(0.5);
        float seed = sgHash(cell);
        if (seed > 0.48) local.x = -local.x;
        float strokeWidth = width * 0.4;
        float rune = sgSegment(local, vec2(-0.22, -0.27), vec2(-0.22, 0.25), strokeWidth);
        rune = max(rune, sgSegment(local, vec2(-0.22, 0.25), vec2(0.18, 0.25), strokeWidth));
        rune = max(rune, sgSegment(local, vec2(-0.22, -0.05), vec2(0.13, -0.05), strokeWidth));
        // Evaluate derivatives in reconverged control flow, not a per-cell branch.
        float lastStroke = sgSegment(local, vec2(0.13, -0.05), vec2(0.13, -0.27), strokeWidth);
        rune = max(rune, lastStroke * step(0.35, seed));
        float live = step(0.2, seed);
        float runner = sgBand(local.y + local.x * 0.65 - t * 0.72 + seed, 1.0, 0.14);
        light = live * rune * (color1 * 0.58 + color2 * runner * second * 0.85);
    } else if (uStyle == 6) {
        // Restrained ice-blue twin waves, not a full-surface aurora replacement.
        float wave = p.y + 0.13 * sin(p.x * 6.0 + t * 1.0)
                   + 0.055 * sin(p.x * 13.0 - t * 1.3) - t * 0.29;
        float a = sgBand(wave, 1.18, width * 0.65);
        float b = sgBand(wave + 0.21 + 0.04 * sin(p.x * 5.0 - t), 1.18, width * 0.4);
        float grain = 0.3 + sgCarrier(p * 1.5 + vec2(t * 0.13, -t * 0.17)) * 0.7;
        light = color1 * a * grain * 0.95 + color2 * b * grain * second;
    } else {
        // A sparse advected field of tiny surface sparks and short tails.
        vec2 cells = p * vec2(7.0, 6.0) + vec2(t * 0.31, -t * 1.12);
        vec2 cell = floor(cells);
        vec2 randomOffset = (sgHash2(cell) - vec2(0.5)) * 0.4;
        vec2 local = fract(cells) - vec2(0.5) - randomOffset;
        float selected = step(0.68, sgHash(cell + vec2(14.3, 97.2)));
        float radius = 0.03 + width * 0.12;
        float core = sgLine(length(local), radius);
        float tail = sgLine(local.x, radius * 0.55)
                   * smoothstep(-0.3, -0.03, local.y)
                   * (1.0 - smoothstep(-0.025, 0.025, local.y));
        float cross = sgSegment(local, vec2(-0.1, 0.0), vec2(0.1, 0.0), radius * 0.24)
                    + sgSegment(local, vec2(0.0, -0.1), vec2(0.0, 0.1), radius * 0.24);
        light = selected * (color1 * (core * 0.75 + tail * 0.52)
                          + color2 * (core * 0.6 + cross * 0.32) * second);
    }

    return max(light, vec3(0.0)) * max(0.0, uIntensity);
}
