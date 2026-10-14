#version 150
uniform sampler2D Sampler0,Sampler1,Sampler2,Sampler3;uniform float Intensity,Exposure,DiskSource;
uniform vec2 GlareCenter,GlareScale;
in vec2 texCoord;out vec4 fragColor;
vec3 linear(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(vec3(.04045),c));}
vec3 display(vec3 c){return mix(c*12.92,1.055*pow(max(c,vec3(0)),vec3(1.0/2.4))-.055,step(vec3(.0031308),c));}
void main(){
    vec3 base=texture(Sampler0,texCoord).rgb;
    // Both inputs are linear HDR radiance selected exclusively from the core.
    vec3 light=max(vec3(0),.85*texture(Sampler1,texCoord).rgb+.70*texture(Sampler2,texCoord).rgb);
    // A faint aperture response adds short rays around the luminous source.
    // It is driven by the BLURRED SELECTED source at its actual projection;
    // a fully hidden/dark core cannot create an independent lens flare.
    vec2 delta=(texCoord-GlareCenter)/max(GlareScale,vec2(.00001));
    float radius=length(delta),angle=radius>.00001?atan(delta.y,delta.x):0.0;
    float spikes=pow(max(0.0,cos(6.0*angle+.32)),24.0);
    float rays=.18*spikes*exp(-2.6*radius)*smoothstep(.25,.55,radius)*(1.0-smoothstep(.8,1.5,radius));
    if(DiskSource<.5&&all(greaterThanEqual(GlareCenter,vec2(0)))&&all(lessThanEqual(GlareCenter,vec2(1))))
        light+=texture(Sampler1,GlareCenter).rgb*rays;
    light*=Intensity;
    if(DiskSource>.5)light*=texture(Sampler3,texCoord).a*.65;
    if(all(equal(light,vec3(0)))){fragColor=vec4(base,1.0);return;}
    if(DiskSource>.5){
        // Exposure operates on HDR radiance. Avoid additive LDR clipping, which
        // erased the disk's noise bands into an opaque white plate.
        vec3 encoded=display(vec3(1.0)-(vec3(1.0)-linear(base))*exp(-light*Exposure*.20));
        fragColor=vec4(encoded,1.0);return;
    }
    vec3 glow=1.0-exp(-light*Exposure);
    fragColor=vec4(display(min(vec3(1),linear(base)+glow)),1.0);
}
