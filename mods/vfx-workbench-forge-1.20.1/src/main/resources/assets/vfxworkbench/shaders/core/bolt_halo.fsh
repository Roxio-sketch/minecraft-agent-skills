#version 150
in vec4 vertexColor;
in vec2 uv;
flat in float roundCoverage;
uniform float Gain;
uniform float RoundJoint;
uniform sampler2D DepthSnapshot;
uniform float LateDepthEnabled;
uniform vec2 OutputSize;
out vec4 fragColor;
void main(){
 if(LateDepthEnabled>.5 && gl_FragCoord.z>texture(DepthSnapshot,gl_FragCoord.xy/OutputSize).r+0.000002)discard;
 float coverage=RoundJoint>0.5?roundCoverage:uv.x;
 float d=RoundJoint>0.5?length(vec2(uv.x/max(roundCoverage,1e-20),uv.y)):abs(uv.y*2.0-1.0);
 if(d>=1.0)discard;
 float falloff=max(0.0,exp(-5.0*d*d)-exp(-5.0));
 // MAX ignores blend factors. Store the actual fixed-point additive contribution.
 vec3 emission=clamp(vertexColor.rgb*Gain,0.0,1.0)*vertexColor.a*coverage*falloff;
 fragColor=vec4(emission,0.0);
}
