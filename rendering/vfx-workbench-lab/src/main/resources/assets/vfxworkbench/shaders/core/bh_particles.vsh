#version 150
in vec3 Position;in vec2 UV0;
uniform mat4 ViewProjection;
uniform vec3 CenterRelative,CameraRight,CameraUp;
uniform float WorldScale,uTime,uTilt,uCore,uOuter,ParticleSpeed,ParticleSize,ParticleOuter;
uniform float uInner,uObjectMode,uParticleClock;
uniform int ParticleDebug;
out vec2 texCoord;
out vec3 RayPosition;
flat out vec3 ParticleLocal;
flat out float ParticleLife,ParticleAlpha,ParticleRadius,ParticlePeriod;

float dustHash(uint n){n^=n>>16u;n*=0x7feb352du;n^=n>>15u;n*=0x846ca68bu;n^=n>>16u;return float(n>>8u)/16777216.0;}
vec3 diskToWorld(vec3 p){float a=radians(uTilt),c=cos(a),s=sin(a);return vec3(p.x,c*p.y-s*p.z,s*p.y+c*p.z);}
void main(){
    uint id=uint(Position.x);
    uint group=id/8u;
    float jitter=dustHash(id*7u+31u),height=dustHash(id*7u+53u);
    float seed=fract(dustHash(group*7u+13u)+(jitter-.5)*.035);
    // Dense, thin, sheared clumps in the disk plus a sparse outer feeder population.
    float outerGroup=dustHash(group*7u+91u);
    float spawn=outerGroup<.78?uOuter*(.88+.16*dustHash(group*7u+37u)):ParticleOuter;
    float start=spawn*(.94+.06*jitter),end=uCore*.93;
    bool outward=uObjectMode>.5;
    if(outward){
        // A separate source/sink lifecycle: emit just outside the inner rim,
        // travel to the outer domain, then fade and respawn at the source.
        start=max(uInner,uCore*1.12)*(1.0+.055*jitter);
        end=max(start+.5,ParticleOuter*(.86+.14*dustHash(group*7u+37u)));
    }
    float period=7.0+7.0*dustHash(group*7u+79u);
    if(outward)period=4.0+3.0*dustHash(group*7u+79u);
    float life=fract(uTime*ParticleSpeed/period+seed);
    if(outward)life=fract(uParticleClock/period+seed);
    // dr/dt is proportional to -1/sqrt(r); integrate angular speed 1/r^1.5
    // over that same trajectory. The orbit accelerates continuously inward.
    float q0=pow(start,1.5),q1=pow(end,1.5),q=mix(q0,q1,life);
    float radius=pow(q,2.0/3.0);
    float angle=dustHash(group*7u+113u)*6.2831853+(jitter-.5)*.16+2.8*period/(q0-q1)*log(q0/q);
    if(outward)angle=dustHash(group*7u+113u)*6.2831853+(jitter-.5)*.16
        +1.15*period/(q1-q0)*log(q/q0);
    float spread=(height-.5)*(outerGroup<.78?.08:.20);
    if(outward)spread=(height-.5)*(.16+.10*life);
    vec3 p=vec3(cos(angle)*radius,spread*radius,sin(angle)*radius);
    float dr=-(q0-q1)/(1.5*period*sqrt(radius)),omega=2.8/q;
    if(outward)omega=1.15/q;
    vec3 velocity=vec3(cos(angle)*dr-sin(angle)*radius*omega,spread*dr,sin(angle)*dr+cos(angle)*radius*omega);
    if(outward)velocity.y+=radius*(height-.5)*.10/period;
    if(ParticleDebug>0){p=ParticleDebug==1?vec3(0,.4,1.8):vec3(0,.4,-1.8);life=.5;radius=1.8;velocity=vec3(1,0,0);}
    ParticleLocal=diskToWorld(p);ParticleLife=life;ParticleRadius=radius;ParticlePeriod=period;
    ParticleAlpha=smoothstep(0.0,.09,life)*(1.0-smoothstep(.94,1.0,life));
    if(outward){
        ParticleAlpha=smoothstep(0.0,.12,life)*(1.0-smoothstep(.80,1.0,life));
        ParticleAlpha*=1.0-smoothstep(mix(start,end,.84),end,radius);
    }else ParticleAlpha*=smoothstep(uCore*.96,uCore*1.08,radius);
    if(ParticleDebug>0)ParticleAlpha=1.0;
    vec3 tangent=diskToWorld(velocity);
    vec2 screenVelocity=vec2(dot(tangent,CameraRight),dot(tangent,CameraUp));
    screenVelocity=length(screenVelocity)>.00001?normalize(screenVelocity):vec2(1,0);
    vec3 along=CameraRight*screenVelocity.x+CameraUp*screenVelocity.y;
    vec3 across=-CameraRight*screenVelocity.y+CameraUp*screenVelocity.x;
    float size=ParticleSize*(.7+.6*seed);
    vec3 offset=along*Position.y*size*(2.2+2.8*life)+across*Position.z*size;
    if(outward)offset=along*Position.y*size*(4.5+7.0*life)+across*Position.z*size;
    RayPosition=CenterRelative+(ParticleLocal+offset)*WorldScale;
    gl_Position=ViewProjection*vec4(RayPosition,1.0);texCoord=UV0;
}
