from pathlib import Path
import sys,re,json,subprocess
import argparse,shutil
parser=argparse.ArgumentParser();parser.add_argument("--output",type=Path,default=Path("rift-validation"));parser.add_argument("--video",action="store_true");args=parser.parse_args()
import glfw,moderngl,numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1]/'assets/vfx-library/cataclysm-rift';out=args.output;out.mkdir(exist_ok=True)
assert glfw.init();glfw.window_hint(glfw.VISIBLE,False);glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,3);glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,3);glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE)
window=glfw.create_window(960,600,'Rift material QA',None,None);glfw.make_context_current(window);ctx=moderngl.create_context(require=330)
v150=(root/'shader/rift.vsh').read_text(encoding='utf-8');f150=(root/'shader/rift.fsh').read_text(encoding='utf-8');actual=ctx.program(vertex_shader=v150,fragment_shader=f150)
js=(root/'app.js').read_text(encoding='utf-8-sig');v='#version 330\nin vec2 position;out vec2 uv;void main(){uv=position*.5+.5;gl_Position=vec4(position,0,1);}'
header='#version 330\nin vec2 uv;out vec4 FragColor;\n'
programs={}
for name in ['scene','blur','composite']:
 src=re.search(r'const '+name+r'=program\(header\+`(.*?)`\);',js,re.S).group(1);programs[name]=ctx.program(vertex_shader=v,fragment_shader=header+src)
quad=ctx.buffer(np.array([-1,-1,3,-1,-1,3],dtype='f4').tobytes());vaos={k:ctx.vertex_array(p,[(quad,'2f','position')]) for k,p in programs.items()};vao=ctx.vertex_array(actual,[(quad,'2f','Position')])
w,h=960,600
bg=ctx.texture((w,h),4,dtype='f2');hdr=ctx.texture((w,h),4,dtype='f2');bright=ctx.texture((w,h),4,dtype='f2');a=ctx.texture((w//4,h//4),4,dtype='f2');b=ctx.texture((w//4,h//4),4,dtype='f2');final=ctx.texture((w,h),4)
for tx in [bg,hdr,bright,a,b,final]:tx.filter=(moderngl.LINEAR,moderngl.LINEAR);tx.repeat_x=tx.repeat_y=False
fbs=[ctx.framebuffer([tx]) for tx in [bg,a,b,final]];fx=ctx.framebuffer([hdr,bright])
textures=[]
for file in ['flow-noise.png','filaments.png']:
 im=Image.open(root/'textures'/file).convert('RGBA');tx=ctx.texture(im.size,4,im.tobytes());tx.filter=(moderngl.LINEAR,moderngl.LINEAR);tx.repeat_x=tx.repeat_y=True;textures.append(tx)
def put(p,k,value):
 if k in p:p[k].value=value
def render(t,opening=1.,wave=.7,background=0,breath=1.,speed=.7):
 p=programs['scene'];fbs[0].use();ctx.viewport=(0,0,w,h);textures[0].use(0)
 for k,val in {'FlowNoise':0,'Resolution':(w,h),'Time':t,'Background':background}.items():put(p,k,val)
 vaos['scene'].render(vertices=3)
 fx.use();ctx.viewport=(0,0,w,h);bg.use(0);textures[0].use(1);textures[1].use(2)
 for k,val in {'SceneColor':0,'FlowNoise':1,'Filaments':2,'Resolution':(w,h),'Pan':(0.,0.),'Time':t,'Wave':wave,'Breath':breath,'BreathPeriod':4.8,'Height':1.22,'Speed':speed,'Glow':1.,'Distortion':1.,'Open':opening,'Zoom':.85*min(1,w/h/1.7),'Yaw':0.,'Slice':1.,'Bloom':1.,'Background':background}.items():put(actual,k,val)
 vao.render(vertices=3)
 p=programs['blur'];ctx.viewport=(0,0,w//4,h//4)
 for fb,tx,d in [(fbs[1],bright,(3/w,0)),(fbs[2],a,(0,4/h)),(fbs[1],b,(4/w,0)),(fbs[2],a,(0,4/h))]:
  fb.use();ctx.viewport=(0,0,w//4,h//4);tx.use(0);put(p,'InputColor',0);put(p,'Direction',d);vaos['blur'].render(vertices=3)
 fbs[3].use();ctx.viewport=(0,0,w,h);hdr.use(0);b.use(1);p=programs['composite'];put(p,'MainColor',0);put(p,'BloomColor',1);put(p,'Bloom',1.);vaos['composite'].render(vertices=3)
 ctx.finish();return np.frombuffer(fbs[3].read(components=3),dtype=np.uint8).reshape(h,w,3)[::-1].copy()
frame=render(6);Image.fromarray(frame).save(out/'裂隙预览.png');second=render(6.4);motion=np.mean(np.abs(frame.astype(float)-second.astype(float)));assert motion>.1,motion
same=render(6);pauseDiff=np.abs(same.astype(float)-frame.astype(float));print('Pause difference:',pauseDiff.max(),pauseDiff.mean());assert pauseDiff.mean()<.1,'Paused time changes visibly'
zero=render(6,0,background=2);assert zero.max()<=1,'Closed rift must contribute no light'
flat=render(6,wave=0);assert not np.array_equal(flat,frame),'Wave control has no effect'
for t in [0,1,4,12,50]:
 check=render(t,background=1);assert np.isfinite(check).all()
inhaled=render(2.4,wave=0,speed=0);exhaled=render(0,wave=0,speed=0);breathDiff=float(np.mean(np.abs(inhaled.astype(float)-exhaled.astype(float))));assert breathDiff>1.,'Breathing must move independently of flow';assert ctx.error=='GL_NO_ERROR',ctx.error
report={'GLSL150_compile':True,'render_size':[w,h],'renderer':ctx.info['GL_RENDERER'],'motion_mean_channel_change_0_4s':float(motion),'frozen_time_mean_change':float(pauseDiff.mean()),'wave_zero_changes_output':True,'closed_on_black_max':int(zero.max()),'breathing_only_mean_channel_change':breathDiff,'GL_error':ctx.error,'scope':'Desktop GPU shader/pipeline validation, not Minecraft runtime'}
(out/'验证报告.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
if args.video:
 f=shutil.which('ffmpeg')
 if not f:raise SystemExit('ffmpeg is required for --video')
 proc=subprocess.Popen([f,'-hide_banner','-loglevel','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r','24','-i','-','-an','-c:v','libx264','-preset','fast','-crf','19','-pix_fmt','yuv420p',str(out/'preview.mp4')],stdin=subprocess.PIPE)
 for k in range(192):proc.stdin.write(render(3+k/24).tobytes())
 proc.stdin.close();assert proc.wait()==0
 print('Video rendered: 8s / 24 fps')
glfw.destroy_window(window);glfw.terminate()
