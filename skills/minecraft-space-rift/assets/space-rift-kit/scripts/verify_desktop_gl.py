"""Real desktop GLSL150 render/depth test in a hidden GLFW window, independent of Minecraft.
pip install moderngl glfw numpy Pillow (or use --deps for an isolated target).
"""
from pathlib import Path
import sys,json,subprocess,hashlib
ROOT=Path(__file__).resolve().parents[1]
if '--deps' in sys.argv:sys.path.insert(0,sys.argv[sys.argv.index('--deps')+1])
import glfw,moderngl,numpy as np
from PIL import Image
OUT=ROOT/'evidence';OUT.mkdir(exist_ok=True)
checks=[]
def check(name,ok,actual=None):
 checks.append(dict(name=name,passed=bool(ok),actual=actual))
 if not ok:print('FAIL',name,actual)
def main():
 assert glfw.init(),'GLFW init failed'
 glfw.window_hint(glfw.VISIBLE,False);glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,3);glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,2);glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE)
 win=glfw.create_window(800,600,'Rift validation',None,None);assert win;glfw.make_context_current(win)
 ctx=moderngl.create_context(require=320);w,h=800,600
 shader=ROOT/'forge/src/main/resources/assets/spacerift/shaders/core'
 prog=ctx.program(vertex_shader=(shader/'rift.vsh').read_text(encoding='utf-8'),fragment_shader=(shader/'rift.fsh').read_text(encoding='utf-8'))
 check('actual desktop GLSL150 compile and link',True)
 background=ctx.program(vertex_shader='#version 150\nvoid main(){vec2 v=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(v*2.-1.,0.,1.);}',fragment_shader='#version 150\nuniform vec2 size;out vec4 c;void main(){vec2 p=gl_FragCoord.xy/size;c=gl_FragCoord.x<size.x*.28?vec4(.8,.12,.05,1.):vec4(.08+p.x*.1,.13+p.y*.12,.23,1.);gl_FragDepth=gl_FragCoord.x<size.x*.28?.25:1.;}')
 background['size'].value=(w,h);bgvao=ctx.vertex_array(background,[])
 texture=ctx.texture((w,h),4);depth=ctx.depth_texture((w,h));target=ctx.framebuffer([texture],depth);target.use();ctx.viewport=(0,0,w,h);ctx.enable(moderngl.DEPTH_TEST);ctx.depth_func='1';bgvao.render(vertices=3)
 before=np.frombuffer(target.read(components=4),np.uint8).reshape(h,w,4).copy();db=np.frombuffer(depth.read(),np.float32).copy()
 scene=ctx.texture((w,h),4,before.tobytes());scene.filter=(moderngl.LINEAR,moderngl.LINEAR);scene.repeat_x=False;scene.repeat_y=False
 external=ctx.texture((w,h),4,bytes([8,230,45,255])*w*h)
 positions=np.array([[-1,-1,.2,-w/h,-1],[3,-1,.2,3*w/h,-1],[-1,3,.2,-w/h,3]],dtype='f4')
 vbo=ctx.buffer(positions.tobytes());vao=ctx.vertex_array(prog,[(vbo,'3f 2f','Position','UV0')])
 js="import{geometry}from'./shader/geometry.js';import{presets}from'./shader/parameters.js';const p=presets.breach;console.log(JSON.stringify({p,g:Object.fromEntries(Object.entries(geometry(p)).map(([k,v])=>[k,[...v]]))}));"
 data=json.loads(subprocess.check_output(['node','--input-type=module','-e',js],cwd=ROOT,text=True,encoding='utf-8'))
 params=data['p']
 for k,v in params.items():
  if k not in prog:continue
  if k.endswith('Color'):prog[k].value=tuple(int(v[i:i+2],16)/255 for i in [1,3,5])
  else:prog[k].value=float(v)
 for uniform,key in [('u_spine','spine'),('u_branchA','branchA'),('u_branchB','branchB')]:prog[uniform].write(np.array(data['g'][key],dtype='f4').tobytes())
 prog['ViewProjection'].write(np.eye(4,dtype='f4').tobytes());prog['ScreenSize'].value=(w,h);prog['u_time'].value=2;prog['SceneColor'].value=0;prog['InteriorColor'].value=1;prog['u_hasInterior'].value=0;scene.use(0);external.use(1)
 ctx.depth_func='<=';target.depth_mask=False;prog['u_depthOnly'].value=0;vao.render(vertices=3)
 color=np.frombuffer(target.read(components=4),np.uint8).reshape(h,w,4).copy();dc=np.frombuffer(depth.read(),np.float32).copy()
 foreground=db.reshape(h,w)<.5
 check('foreground fixture really matches red geometry',np.all(before[foreground][:,0]>200),{'count':int(foreground.sum())})
 check('foreground depth occludes all rift layers',np.array_equal(color[foreground],before[foreground]),{'changed':int(np.count_nonzero(color[foreground]!=before[foreground])),'beforeDepthRange':[float(db.min()),float(db.max())],'foregroundDepthRange':[float(db.reshape(h,w)[foreground].min()),float(db.reshape(h,w)[foreground].max())],'foregroundSample':before[10,10].tolist()})
 check('actual scene capture changes visible pixels',np.count_nonzero(color!=before)>500)
 check('color pass never writes quad depth',np.array_equal(db,dc))
 Image.fromarray(color[::-1]).save(OUT/'desktop-gl150.png')
 target.color_mask=(False,False,False,False);target.depth_mask=True;prog['u_depthOnly'].value=1;vao.render(vertices=3)
 da=np.frombuffer(depth.read(),np.float32).copy();check('opaque opening writes plane depth',np.count_nonzero(da<db)>100)
 check('foreground depth preserved in depth pass',np.array_equal(da.reshape(h,w)[foreground],db.reshape(h,w)[foreground]))
 # Outer corners of finite quad must never acquire an opaque depth wall.
 corners=np.ones((h,w),bool);corners[h//20:h*19//20,:]=False
 check('outside finite opening has no quad-depth wall',np.count_nonzero((da<db).reshape(h,w)&corners)<20)
 target.color_mask=(True,True,True,True);target.depth_mask=False;prog['u_depthOnly'].value=0;prog['u_interiorMode'].value=2;prog['u_hasInterior'].value=1;vao.render(vertices=3)
 externalColor=np.frombuffer(target.read(components=4),np.uint8).reshape(h,w,4).copy()
 check('external interior texture adapter renders',np.count_nonzero(externalColor[:,:,1]>color[:,:,1]+40)>100)
 # The depth-only path must match the exact same styled aperture as the color path.
 fsh=(shader/'rift.fsh').read_text(encoding='utf-8')
 maskSource=fsh.split('void main(){')[0]+'''void main(){
 mat2 rot=mat2(cos(u_angle),-sin(u_angle),sin(u_angle),cos(u_angle));vec2 p=rot*LocalPlane;
 riftFootprint=max(length(dFdx(p)),length(dFdy(p)));fragColor=vec4(vec3(riftAperture(p,u_time*u_speed)),1.);
 }'''
 maskProg=ctx.program(vertex_shader=(shader/'rift.vsh').read_text(encoding='utf-8'),fragment_shader=maskSource)
 maskVao=ctx.vertex_array(maskProg,[(vbo,'3f 2f','Position','UV0')])
 for k,v in params.items():
  if k in maskProg and isinstance(v,(int,float)):maskProg[k].value=float(v)
 for uniform,key in [('u_spine','spine'),('u_branchA','branchA'),('u_branchB','branchB')]:
  if uniform in maskProg:maskProg[uniform].write(np.array(data['g'][key],dtype='f4').tobytes())
 maskProg['ViewProjection'].write(np.eye(4,dtype='f4').tobytes());maskProg['u_time'].value=2
 maskTex=ctx.texture((w,h),4,dtype='f4');maskTarget=ctx.framebuffer([maskTex])
 prog['u_interiorMode'].value=params['u_interiorMode'];prog['u_hasInterior'].value=0
 cases=[(f'style {style}',dict(params,u_style=style),data['g'])for style in range(9)]
 horizonJs="import{geometry}from'./shader/geometry.js';import{horizonPresets}from'./shader/parameters.js';console.log(JSON.stringify(Object.entries(horizonPresets).map(([k,p])=>({k,p,g:Object.fromEntries(Object.entries(geometry(p)).map(([k,v])=>[k,[...v]]))}))));"
 horizonData=json.loads(subprocess.check_output(['node','--input-type=module','-e',horizonJs],cwd=ROOT,text=True,encoding='utf-8'))
 cases.extend((row['k'],row['p'],row['g'])for row in horizonData)
 for label,case,geometry in cases:
  for program in [prog,maskProg]:
   for k,v in case.items():
    if k in program and isinstance(v,(int,float)):program[k].value=float(v)
   for uniform,key in [('u_spine','spine'),('u_branchA','branchA'),('u_branchB','branchB')]:
    if uniform in program:program[uniform].write(np.array(geometry[key],dtype='f4').tobytes())
  maskTarget.use();ctx.disable(moderngl.DEPTH_TEST);maskVao.render(vertices=3)
  opening=np.frombuffer(maskTarget.read(components=4,dtype='f4'),np.float32).reshape(h,w,4)[:,:,0]
  target.use();ctx.enable(moderngl.DEPTH_TEST);ctx.depth_func='1';target.depth_mask=True;bgvao.render(vertices=3)
  ctx.depth_func='<=';target.depth_mask=False;prog['u_depthOnly'].value=0;vao.render(vertices=3)
  pixels=np.frombuffer(target.read(components=4),np.uint8).reshape(h,w,4)
  check(f'{label} foreground depth preserved',np.array_equal(pixels[foreground],before[foreground]))
  target.color_mask=(False,False,False,False);target.depth_mask=True;prog['u_depthOnly'].value=1;vao.render(vertices=3)
  styledDepth=np.frombuffer(depth.read(),np.float32).reshape(h,w);changed=styledDepth<db.reshape(h,w)
  expected=(opening>=.98)&~foreground
  mismatch=np.count_nonzero(changed!=expected)
  check(f'{label} depth matches styled opening',mismatch<=2,{'mismatchedPixels':int(mismatch)})
  target.color_mask=(True,True,True,True)
 check('no OpenGL error',ctx.error=='GL_NO_ERROR',ctx.error)
 report=dict(ok=all(c['passed']for c in checks),checks=checks,hardware=dict(vendor=ctx.info['GL_VENDOR'],renderer=ctx.info['GL_RENDERER'],version=ctx.info['GL_VERSION']),shaderSha256=hashlib.sha256((shader/'rift.fsh').read_bytes()).hexdigest(),scope='Desktop OpenGL offscreen tests; not Minecraft runtime certification')
 (OUT/'desktop-gl-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report));glfw.destroy_window(win);glfw.terminate()
 if not report['ok']:raise SystemExit(1)
if __name__=='__main__':main()
