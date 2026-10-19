from pathlib import Path
root=Path(__file__).resolve().parents[1];core=(root/'shader/rift-core.glsl').read_text(encoding='utf-8-sig')
main='\nvoid main(){vec3 e;FragColor=riftShade(uv,e);BrightColor=vec4(max(e-vec3(.8),vec3(0.)),1.);}\n'
(root/'shader/rift-webgl.frag').write_text('#version 300 es\nprecision highp float;in vec2 uv;layout(location=0) out vec4 FragColor;layout(location=1) out vec4 BrightColor;\n'+core+main,encoding='utf-8',newline='\n')
(root/'shader/rift.fsh').write_text('#version 150\nin vec2 uv;out vec4 FragColor;out vec4 BrightColor;\n'+core+main,encoding='utf-8',newline='\n')
(root/'shader/rift.vsh').write_text('#version 150\nin vec2 Position;out vec2 uv;void main(){uv=Position*.5+.5;gl_Position=vec4(Position,0.,1.);}\n',encoding='utf-8',newline='\n')
print('Generated WebGL2 and GLSL150 from rift-core.glsl')
