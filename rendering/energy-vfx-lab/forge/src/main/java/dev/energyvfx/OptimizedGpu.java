package dev.energyvfx;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.*;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;

/** Conservative screen bounds, partial scene copy, cached uniforms; unchanged density/sampling. */
final class OptimizedGpu {
 static int volumeProgram,blurProgram,compositeProgram,vao,width,height;
 static Gpu.Target volume,blurA,blurB;static TextureTarget scene;
 static double lastCoverage=1,lastCopyCoverage=1;
 private static final float[] CLEAR={0,0,0,1},matrix=new float[16];
 static int loc(int p,String n){return Uniforms.location(p,n);}
 static void init()throws Exception{if(volumeProgram!=0)return;try{
  String noise=Recipe.resource("shaders/noise.glsl"),field=Recipe.resource("shaders/field.glsl"),world=Recipe.resource("shaders/scene.glsl");
  volumeProgram=Gpu.program("#define ENERGY_PACKET_BOUNDS\n"+(!Boolean.getBoolean("energyvfx.disableDirectParcels")?"#define ENERGY_DIRECT_PARCELS\n":"")+(FlowCache.supported()?"#define ENERGY_FLOW_CACHE\n":"")+noise+field+world+Recipe.resource("shaders/volume.glsl"),true);
  blurProgram=Gpu.program(Recipe.resource("shaders/blur.glsl"),false);
  compositeProgram=Gpu.program(world+Recipe.resource("shaders/composite.glsl"),false);vao=GL30.glGenVertexArrays();
 }catch(Exception x){release();throw x;}}
 static void targets(RenderTarget main,float scale){int w=Math.max(1,Math.round(main.width*scale)),h=Math.max(1,Math.round(main.height*scale));if(scene!=null&&scene.width==main.width&&scene.height==main.height&&w==width&&h==height)return;freeTargets();width=w;height=h;scene=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_LINEAR);volume=new Gpu.Target(w,h,2);blurA=new Gpu.Target(Math.max(1,w/2),Math.max(1,h/2),1);blurB=new Gpu.Target(Math.max(1,w/2),Math.max(1,h/2),1);}
 static void pass(int p,int f,int w,int h){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);RenderSystem.viewport(0,0,w,h);GlStateManager._glUseProgram(p);GL20.glUniform2f(loc(p,"viewport"),w,h);GL30.glBindVertexArray(vao);}
 static void texture(int p,String n,int t,int unit){GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);GlStateManager._bindTexture(t);GL20.glUniform1i(loc(p,n),unit);}
 static void vec(int p,String n,Vector3f v){GL20.glUniform3f(loc(p,n),v.x,v.y,v.z);}
 static void camera(int p,Vector3f camera,Vector3f right,Vector3f up,Vector3f forward,float fov){vec(p,"camera",camera);vec(p,"rightVec",right);vec(p,"upVec",up);vec(p,"forwardVec",forward);GL20.glUniform1f(loc(p,"tanHalfFov"),fov);GL20.glUniform1i(loc(p,"gameScene"),1);GL20.glUniform1i(loc(p,"environment"),0);GL20.glUniformMatrix4fv(loc(p,"inverseViewProjection"),false,matrix);}
 record Rect(int x0,int y0,int x1,int y1){
  static Rect full(int w,int h){return new Rect(0,0,w,h);}
  Rect expand(int x,int y,int w,int h){return new Rect(Math.max(0,x0-x),Math.max(0,y0-y),Math.min(w,x1+x),Math.min(h,y1+y));}
  Rect scale(int w,int h,int originalW,int originalH){return new Rect(Math.max(0,(int)Math.floor((double)x0*w/originalW)),Math.max(0,(int)Math.floor((double)y0*h/originalH)),Math.min(w,(int)Math.ceil((double)x1*w/originalW)),Math.min(h,(int)Math.ceil((double)y1*h/originalH)));}
  boolean empty(){return x1<=x0||y1<=y0;}
  void apply(){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x0,y0,Math.max(0,x1-x0),Math.max(0,y1-y0));}
  double coverage(int w,int h){return empty()?0:(double)(x1-x0)*(y1-y0)/(w*h);}
 }
 static Rect project(Matrix4f viewProjection,Vector3f camera,int w,int h){
  double loX=Double.POSITIVE_INFINITY,loY=loX,hiX=Double.NEGATIVE_INFINITY,hiY=hiX;
  for(int i=0;i<8;i++){
   Vector4f p=new Vector4f((i&1)==0?AuraEmitter.uploadMin[0]:AuraEmitter.uploadMax[0],(i&2)==0?AuraEmitter.uploadMin[1]:AuraEmitter.uploadMax[1],(i&4)==0?AuraEmitter.uploadMin[2]:AuraEmitter.uploadMax[2],1);
   p.x-=camera.x;p.y-=camera.y;p.z-=camera.z;viewProjection.transform(p);
   // Near-plane crossings and a camera inside the emitter use the safe full-screen path.
   if(!Float.isFinite(p.w)||p.w<=.0001f||p.z<=-p.w)return Rect.full(w,h);
   double x=p.x/p.w,y=p.y/p.w;loX=Math.min(loX,x);loY=Math.min(loY,y);hiX=Math.max(hiX,x);hiY=Math.max(hiY,y);
  }
  int x0=(int)Math.floor((loX+1)*w*.5)-4,y0=(int)Math.floor((loY+1)*h*.5)-4,x1=(int)Math.ceil((hiX+1)*w*.5)+4,y1=(int)Math.ceil((hiY+1)*h*.5)+4;
  if(x1<=0||y1<=0||x0>=w||y0>=h)return new Rect(0,0,0,0);
  return new Rect(Math.max(0,x0),Math.max(0,y0),Math.min(w,x1),Math.min(h,y1));
 }
 static void clear(Gpu.Target target){GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target.f);for(int i=0;i<target.textures.length;i++)GL30.glClearBufferfv(GL11.GL_COLOR,i,CLEAR);}
 static void render(RenderLevelStageEvent event)throws Exception{
  long cpuStarted=System.nanoTime();var mc=Minecraft.getInstance();if(PerformanceQa.ZOOM){ZoomMotionQa.forceCamera(event.getCamera());ZoomMotionQa.forceCamera(mc.gameRenderer.getMainCamera());}var main=mc.getMainRenderTarget();
  if(mc.options.graphicsMode().get()==GraphicsStatus.FABULOUS)throw new IllegalStateException("Fabulous targets require a separate adapter");
  if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)throw new IllegalStateException("Shader pipeline replaced the main target; adapter required");
  try(Gpu.State state=new Gpu.State()){
   init();float[] u=EnergyVfx.Client.recipe.values;targets(main,u[37]);float partial=PerformanceQa.partial(event.getPartialTick()),time=(EnergyVfx.Client.ticks+partial)/20f;
   RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
   int p=volumeProgram;GlStateManager._glUseProgram(p);
   if(u[39]>.5){EnergyVfx.Client.anchor=AuraEmitter.anchor(partial);AuraEmitter.uploadCached(p,partial);}else{GL20.glUniform1i(loc(p,"puffCount"),0);GL20.glUniform1f(loc(p,"auraOpacity"),1);}
   var position=event.getCamera().getPosition().subtract(EnergyVfx.Client.anchor);Vector3f camera=new Vector3f((float)position.x,(float)position.y,(float)position.z);
   Matrix4f view=new Matrix4f(event.getPoseStack().last().pose()),inverseView=new Matrix4f(view).invert(),projection=new Matrix4f(event.getProjectionMatrix());
   Vector3f right=inverseView.transformDirection(new Vector3f(1,0,0)),up=inverseView.transformDirection(new Vector3f(0,1,0)),forward=inverseView.transformDirection(new Vector3f(0,0,-1));
   Matrix4f viewProjection=new Matrix4f(projection).mul(view);new Matrix4f(viewProjection).invert().get(matrix);float fov=1f/projection.m11();
   Rect area=u[39]>.5?PacketBounds.prepare(p,viewProjection,camera,main.width,main.height,u[1]):Rect.full(main.width,main.height);
   if(u[39]>.5&&AuraEmitter.uploadCount==0)area=new Rect(0,0,0,0);
   lastCoverage=area.coverage(main.width,main.height);PerfTrace.mark(1);
   if(area.empty()){for(int i=2;i<=6;i++)PerfTrace.mark(i);PerfTrace.mark(8);FlowCache.active=false;lastCopyCoverage=0;return;}
   // Reserve the scaled bilinear footprints of volume, two blur passes and composition.
   boolean bloom=u[21]>0;int padX=bloom?(int)Math.ceil((3.231*u[22]+3)*main.width/width)+2:2,padY=bloom?(int)Math.ceil((3.231*u[22]+3)*main.height/height)+2:2;
   Rect compose=area.expand(padX,padY,main.width,main.height);
   Rect copied=compose.expand((int)Math.ceil(Math.abs(u[24])*main.width)+2,(int)Math.ceil(Math.abs(u[24])*main.height)+2,main.width,main.height);lastCopyCoverage=copied.coverage(main.width,main.height);
   GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);GL30.glBlitFramebuffer(copied.x0,copied.y0,copied.x1,copied.y1,copied.x0,copied.y0,copied.x1,copied.y1,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);PerfTrace.mark(2);
   if(u[39]>.5)FlowCache.prepare(time,u[7],p);else{FlowCache.active=false;GL20.glUniform1i(loc(p,"smokeCacheEnabled"),0);GL20.glUniform1i(loc(p,"smokeVortexCache"),4);}PerfTrace.mark(8);
   // Fast full clears prevent old smoke/halo from surviving a moving or empty bound.
   clear(volume);pass(p,volume.f,width,height);area.scale(width,height,main.width,main.height).apply();
   GL20.glUniform1fv(loc(p,"u[0]"),u);GL20.glUniform1f(loc(p,"clockTime"),time);GL20.glUniform3fv(loc(p,"colorA"),EnergyVfx.Client.recipe.a);GL20.glUniform3fv(loc(p,"colorB"),EnergyVfx.Client.recipe.b);camera(p,camera,right,up,forward,fov);texture(p,"sceneColor",scene.getColorTextureId(),0);texture(p,"sceneDepth",scene.getDepthTextureId(),1);Gpu.draw();PerfTrace.mark(3);
   if(bloom){
    clear(blurA);p=blurProgram;pass(p,blurA.f,blurA.w,blurA.h);area.expand(padX,(int)Math.ceil(3.0*main.height/height)+2,main.width,main.height).scale(blurA.w,blurA.h,main.width,main.height).apply();texture(p,"image",volume.textures[1],0);GL20.glUniform2f(loc(p,"direction"),u[22]/width,0);Gpu.draw();PerfTrace.mark(4);
    clear(blurB);pass(p,blurB.f,blurB.w,blurB.h);compose.scale(blurB.w,blurB.h,main.width,main.height).apply();texture(p,"image",blurA.textures[0],0);GL20.glUniform2f(loc(p,"direction"),0,u[22]/height);Gpu.draw();PerfTrace.mark(5);
   }else{PerfTrace.mark(4);PerfTrace.mark(5);}
   p=compositeProgram;pass(p,main.frameBufferId,main.width,main.height);compose.apply();texture(p,"image",volume.textures[0],0);texture(p,"glow",blurB.textures[0],1);texture(p,"sceneColor",scene.getColorTextureId(),2);texture(p,"sceneDepth",scene.getDepthTextureId(),3);camera(p,camera,right,up,forward,fov);GL20.glUniform1f(loc(p,"distortion"),u[24]);GL20.glUniform1f(loc(p,"clockTime"),time);GL20.glUniform1f(loc(p,"bloom"),u[21]);Gpu.draw();PerfTrace.mark(6);
   EnergyVfx.Client.draws++;EnergyVfx.Client.lastGl=GL11.glGetError();if(EnergyVfx.Client.lastGl!=0)throw new IllegalStateException("GL error "+EnergyVfx.Client.lastGl);
  }finally{PerfTrace.cpu("vfxSubmitCpuMs",System.nanoTime()-cpuStarted);}
 }
 static void freeTargets(){if(scene!=null)scene.destroyBuffers();scene=null;if(volume!=null)volume.destroy();if(blurA!=null)blurA.destroy();if(blurB!=null)blurB.destroy();volume=blurA=blurB=null;}
 static void release(){FlowCache.release();freeTargets();for(int p:new int[]{volumeProgram,blurProgram,compositeProgram})if(p!=0)GL20.glDeleteProgram(p);if(vao!=0)GL30.glDeleteVertexArrays(vao);volumeProgram=blurProgram=compositeProgram=vao=0;Uniforms.clear();}
}
