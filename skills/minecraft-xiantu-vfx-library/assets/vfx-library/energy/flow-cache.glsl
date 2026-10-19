// Exact lattice vortex coefficients for all six RK2 evaluation times.
// This is prepended by the shared noise + field functions; it does not sample/interpolate density.
layout(local_size_x=8,local_size_y=8,local_size_z=1) in;
layout(rgba32f,binding=0) writeonly uniform image2DArray cacheOutput;
uniform int cachePackets;
uniform float cacheClock,cacheSpeed;
uniform float cacheSeeds[32],cacheAges[32];
void main(){
 ivec3 index=ivec3(gl_GlobalInvocationID);
 if(index.x>=169||index.y>=13||index.z>=cachePackets*6)return;
 int parcel=index.z/6,stage=index.z%6;
 float dt=min(cacheAges[parcel],3.)*cacheSpeed*.8/3.,t=cacheClock*cacheSpeed;
 for(int j=0;j<3;j++){if(j>=stage/2)break;t-=dt;}
 if((stage&1)==1)t-=dt*.5;
 vec3 corner=vec3(index.x%13,index.y,index.x/13)-vec3(6);
 imageStore(cacheOutput,index,smokeVortex(corner,t,cacheSeeds[parcel]));
}
