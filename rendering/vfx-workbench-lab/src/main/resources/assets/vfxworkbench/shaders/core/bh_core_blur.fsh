#version 150
uniform sampler2D Sampler0;uniform vec2 Direction;
in vec2 texCoord;out vec4 fragColor;
vec3 source(vec2 uv){return all(greaterThanEqual(uv,vec2(0)))&&all(lessThanEqual(uv,vec2(1)))?texture(Sampler0,uv).rgb:vec3(0);}
void main(){
    // A broad world emitter must not be copied into five separated images by
    // scaling the sparse bilinear optimization intended for small texel steps.
    // Dense normalized quadrature retains a single continuous optical envelope.
    vec3 c=vec3(0.0);float total=0.0;
    for(int i=-16;i<=16;i++){
        float x=float(i)*.2,weight=exp(-.5*x*x);
        c+=source(texCoord+Direction*x)*weight;total+=weight;
    }
    fragColor=vec4(c/total,1.0);
}
