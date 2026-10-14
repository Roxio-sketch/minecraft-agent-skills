#version 150
in vec3 Position;in vec4 Color;in vec2 UV0;in ivec2 UV1;in ivec2 UV2;in vec3 Normal;
uniform mat4 ModelViewMat;uniform mat4 ProjMat;uniform mat3 IViewRotMat;uniform sampler2D Sampler1;uniform sampler2D Sampler2;
out vec3 local;out vec4 color;out vec4 overlay;out vec3 normal;
void main(){local=Position;normal=normalize(mat3(ModelViewMat)*Normal);color=Color*texelFetch(Sampler2,UV2/16,0);overlay=texelFetch(Sampler1,UV1,0);gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);}
