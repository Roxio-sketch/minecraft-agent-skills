#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position; in vec4 Color; in vec2 UV0; in ivec2 UV1; in ivec2 UV2;
uniform mat4 ModelViewMat,ProjMat; uniform int FogShape;
uniform sampler2D Sampler1,Sampler2;
out vec2 texCoord;out vec4 baseColor,overlayColor;out float vertexDistance;
void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1);texCoord=UV0;
 baseColor=Color*minecraft_sample_lightmap(Sampler2,UV2);overlayColor=texelFetch(Sampler1,UV1,0);
 vertexDistance=fog_distance(ModelViewMat,Position,FogShape);}
