#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform vec2 TexelSize;
uniform vec4 Stop0;
uniform vec4 Stop1;
uniform vec4 Stop2;
uniform vec4 Stop3;
uniform vec4 Stop4;
uniform vec2 GradientAxis;
uniform float GradientScale;
uniform float GradientOffset;
uniform float EdgeSensitivity;
uniform float DetailSuppression;
uniform float DepthStrength;
uniform float ShadeAmount;
uniform float LineWeight;
uniform float DebugMode;
in vec2 uv;
out vec4 fragColor;

vec3 colorAt(vec2 p) {
    return texture(Sampler0, clamp(p, vec2(0.0), vec2(1.0))).rgb;
}

float grayAt(vec2 p) {
    return dot(colorAt(p), vec3(0.2126, 0.7152, 0.0722));
}

float sceneDepth(vec2 p) {
    p = clamp(p, vec2(0.0), vec2(1.0));
    float hand = texture(Sampler2, p).r;
    return hand < 0.999999 ? hand : texture(Sampler1, p).r;
}

float visibleCutout(vec2 p) {
    if (texture(Sampler2, p).r < 0.999999) return 0.0;
    float solid = texture(Sampler3, p).r;
    float cutout = texture(Sampler4, p).r;
    float world = texture(Sampler1, p).r;
    // A later entity or block entity that overwrites depth is no longer cutout.
    return float(cutout + 0.000001 < solid && abs(world - cutout) < 0.0000001);
}

// Two separable-looking nine-tap kernels at different radii suppress block texel noise
// before comparing tonal structure. This is not a single Sobel outline pass.
vec3 blur9(vec2 p, vec2 stepSize) {
    vec3 v = 4.0 * colorAt(p);
    v += 2.0 * (colorAt(p + vec2(stepSize.x, 0.0)) + colorAt(p - vec2(stepSize.x, 0.0)));
    v += 2.0 * (colorAt(p + vec2(0.0, stepSize.y)) + colorAt(p - vec2(0.0, stepSize.y)));
    v += colorAt(p + stepSize) + colorAt(p - stepSize);
    v += colorAt(p + vec2(stepSize.x, -stepSize.y)) + colorAt(p + vec2(-stepSize.x, stepSize.y));
    return v / 16.0;
}

vec3 gradientAt(float t) {
    if (t < Stop1.a) return mix(Stop0.rgb, Stop1.rgb, smoothstep(Stop0.a, Stop1.a, t));
    if (t < Stop2.a) return mix(Stop1.rgb, Stop2.rgb, smoothstep(Stop1.a, Stop2.a, t));
    if (t < Stop3.a) return mix(Stop2.rgb, Stop3.rgb, smoothstep(Stop2.a, Stop3.a, t));
    return mix(Stop3.rgb, Stop4.rgb, smoothstep(Stop3.a, Stop4.a, t));
}

float chromaAt(vec2 p) {
    return texture(Sampler5,clamp(p,vec2(0.0),vec2(1.0))).g;
}

float geometryBand(vec2 p,vec2 width) {
    return max(texture(Sampler5,p).b,
               max(texture(Sampler5,p-width).b,texture(Sampler5,p+width).b));
}
float geometrySupport(vec2 stepSize) {
    vec2 direction=normalize(stepSize/TexelSize);
    vec2 width=vec2(-direction.y,direction.x)*TexelSize*2.0*LineWeight;
    return min(min(geometryBand(uv-stepSize,width),geometryBand(uv+stepSize,width)),
               min(geometryBand(uv-stepSize*2.0,width),geometryBand(uv+stepSize*2.0,width)));
}

void main() {
    vec2 pixel = TexelSize * LineWeight;
    vec3 fineRgb = blur9(uv, pixel * mix(1.15, 3.8, DetailSuppression));
    vec3 coarseRgb = blur9(uv, pixel * mix(3.0, 9.0, DetailSuppression));
    float fine = dot(fineRgb, vec3(0.2126, 0.7152, 0.0722));
    float coarse = dot(coarseRgb, vec3(0.2126, 0.7152, 0.0722));
    float darkRidge = max(0.0, coarse - fine);
    float horizontal = abs(grayAt(uv + vec2(pixel.x * 2.0, 0.0)) - grayAt(uv - vec2(pixel.x * 2.0, 0.0)));
    float vertical = abs(grayAt(uv + vec2(0.0, pixel.y * 2.0)) - grayAt(uv - vec2(0.0, pixel.y * 2.0)));
    float colorStructure = darkRidge * 2.1 + (horizontal + vertical) * mix(0.32, 0.01, DetailSuppression);
    float threshold = mix(0.18, 0.045, EdgeSensitivity);
    // A cutout render layer is not a foliage material ID: vanilla grass blocks
    // also use cutoutMipped. Suppress only fragments with sparse depth support.
    float d = sceneDepth(uv);
    vec2 farStep = pixel * 8.0;
    float localScale = max(1.0 - d, 0.002);
    float discontinuities =
        smoothstep(0.025, 0.10, abs(d - sceneDepth(uv + vec2(farStep.x, 0.0))) / localScale) +
        smoothstep(0.025, 0.10, abs(d - sceneDepth(uv - vec2(farStep.x, 0.0))) / localScale) +
        smoothstep(0.025, 0.10, abs(d - sceneDepth(uv + vec2(0.0, farStep.y))) / localScale) +
        smoothstep(0.025, 0.10, abs(d - sceneDepth(uv - vec2(0.0, farStep.y))) / localScale);
    float support = 1.0 - smoothstep(2.15, 3.35, discontinuities);
    float cutout = visibleCutout(uv) * (1.0 - support);
    // Opaque blocks need surface structure where their depth alone is planar;
    // alpha-tested leaves need the opposite treatment to avoid pixel confetti.
    float thinRidge = max(0.0, coarse - grayAt(uv));
    // Colour-only fallback for sky/clouds and the screenshot workbench. On
    // real surfaces keep tonal texture below the hard-ink threshold.
    float drawing = smoothstep(threshold,threshold+0.09,colorStructure)
                    * (d<0.999999?0.10:1.0);

    // A grass cap and its dirt side may share nearly the same luminance. The
    // half-resolution chroma buffer preserves that colour boundary. Require
    // support along the line before inking it, so dirt texels stay sparse.
    vec2 horizontalStep=vec2(pixel.x*12.0,0.0);
    vec2 verticalStep=vec2(0.0,pixel.y*12.0);
    vec2 diagonalStep=pixel*8.0;
    vec4 chromaDirections=vec4(
        min(chromaAt(uv-horizontalStep),chromaAt(uv+horizontalStep)),
        min(chromaAt(uv-verticalStep),chromaAt(uv+verticalStep)),
        min(chromaAt(uv-diagonalStep),chromaAt(uv+diagonalStep)),
        min(chromaAt(uv+vec2(diagonalStep.x,-diagonalStep.y)),
            chromaAt(uv+vec2(-diagonalStep.x,diagonalStep.y))));
    float coherentChroma=max(max(chromaDirections.x,chromaDirections.y),max(chromaDirections.z,chromaDirections.w))
        - min(min(chromaDirections.x,chromaDirections.y),min(chromaDirections.z,chromaDirections.w));
    float canopy=texture(Sampler5,uv).r;
    float chromaInk=smoothstep(0.10,0.28,chromaAt(uv))
                   *smoothstep(0.10,0.25,coherentChroma);
    chromaInk*=1.0-0.8*cutout;
    drawing=max(drawing,0.20*chromaInk*(1.0-cutout));

    if (d < 0.999999) {
        float left = sceneDepth(uv - vec2(pixel.x, 0.0));
        float right = sceneDepth(uv + vec2(pixel.x, 0.0));
        float down = sceneDepth(uv - vec2(0.0, pixel.y));
        float up = sceneDepth(uv + vec2(0.0, pixel.y));
        // A crease between two similarly coloured faces can have little
        // first-order contrast. A change of depth slope retains that corner.
        float slopeChange = max(abs(left + right - 2.0 * d),
                                abs(down + up - 2.0 * d)) / localScale;
        float creaseEdge = pow(smoothstep(0.0002, 0.004, slopeChange), 0.7);
        // Only cutout fragments use the sparse-neighborhood gate. Applying it
        // to opaque architecture broke long edges at corners and diagonals.
        vec4 directions=vec4(geometrySupport(vec2(pixel.x*8.0,0.0)),
                             geometrySupport(vec2(0.0,pixel.y*8.0)),
                             geometrySupport(pixel*6.0),
                             geometrySupport(vec2(pixel.x,-pixel.y)*6.0));
        float lineSupport=max(max(directions.x,directions.y),max(directions.z,directions.w))
                          -min(min(directions.x,directions.y),min(directions.z,directions.w));
        float clutter=0.0;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){
            if(x!=0||y!=0)clutter+=texture(Sampler5,uv+vec2(x,y)*pixel*8.0).b;
        }
        float isolated=1.0-smoothstep(0.25,0.65,clutter/8.0);
        drawing = max(drawing, creaseEdge * DepthStrength
                      * mix(1.0, mix(0.10*support,0.85,smoothstep(0.2,0.7,lineSupport)*isolated),visibleCutout(uv)));
        // Keep the actual geometry silhouette; a blurred coverage isocontour
        // shifts corners and creates a second, rounded outline.
        // Silhouettes use the same full-resolution crease response as other geometry.
    }
    // Sparse, low-contrast tonal pencil shading keeps large paper-white areas.
    float shade = pow(max(0.0, 1.0 - coarse), 2.6) * ShadeAmount;
    if(DebugMode>0.5){
        float diagnostic=DebugMode<1.5?chromaAt(uv):
                         (DebugMode<2.5?coherentChroma:
                         (DebugMode<3.5?chromaInk:
                         (DebugMode<4.5?cutout:
                         (DebugMode<5.5?canopy:chromaInk*(1.0-cutout)))));
        fragColor=vec4(vec3(diagnostic),1.0);
        return;
    }
    // Sharpen coverage without blurring/dilating final line geometry. Keep the
    // suppressed foliage floor below the ink threshold; strong lines reach 1.
    float ink = clamp(max(smoothstep(0.16,0.55,drawing),shade*0.35),0.0,1.0);
    vec2 axis = normalize(GradientAxis);
    float t = clamp(0.5 + dot(uv - 0.5, axis) * GradientScale + GradientOffset, 0.0, 1.0);
    fragColor = vec4(mix(vec3(1.0), pow(gradientAt(t),vec3(1.25)), ink), 1.0);
}
