#version 150
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec3 InverseViewTranslation;
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
in vec2 screenUV;
out vec4 fragColor;
// Private opt-in GPU acceptance probe; default zero, outside recipe contract.
uniform int ValidationProbe;
