package dev.energyvfx;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
/** HDR emission MRT, separable bloom, scene-copy distortion; shared editor kernels. */
final class Gpu {
 static int volumeProgram,blurProgram,compositeProgram,vao,width,height;static Target volume,blurA,blurB;static TextureTarget scene;
 static final String VERTEX="#version 150\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}";
 static String source(String name)throws Exception{return Recipe.resource("benchmark/shaders/"+name+".glsl");}
 static int shader(int type,String text){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,text);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(error);}return s;}
 static int program(String body,boolean mrt){int p=GL20.glCreateProgram(),v=0,f=0;try{v=shader(GL20.GL_VERTEX_SHADER,VERTEX);f=shader(GL20.GL_FRAGMENT_SHADER,"#version 150\n"+body);GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);if(mrt){GL30.glBindFragDataLocation(p,0,"volumeColor");GL30.glBindFragDataLocation(p,1,"emissionColor");}else GL30.glBindFragDataLocation(p,0,"result");GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));return p;}catch(RuntimeException ex){GL20.glDeleteProgram(p);throw ex;}finally{if(v!=0)GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}}
 static void init()throws Exception{if(volumeProgram!=0)return;try{volumeProgram=program(source("noise")+source("field")+source("scene")+source("volume"),true);blurProgram=program(source("blur"),false);compositeProgram=program(source("scene")+source("composite"),false);vao=GL30.glGenVertexArrays();}catch(Exception ex){release();throw ex;}}
 static final class Target {
  final int f,w,h;final int[] textures;
  Target(int w,int h,int count){this.w=w;this.h=h;f=GL30.glGenFramebuffers();textures=new int[count];GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);int[] attachments=new int[count];for(int i=0;i<count;i++){int t=GlStateManager._genTexture();textures[i]=t;GlStateManager._bindTexture(t);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);attachments[i]=GL30.GL_COLOR_ATTACHMENT0+i;GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,attachments[i],GL11.GL_TEXTURE_2D,t,0);}GL20.glDrawBuffers(attachments);if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE){destroy();throw new IllegalStateException("Incomplete HDR target");}}
  void destroy(){for(int t:textures)if(t!=0)GlStateManager._deleteTexture(t);GL30.glDeleteFramebuffers(f);}
 }
 static void targets(RenderTarget main,float scale){int w=Math.max(1,Math.round(main.width*scale)),h=Math.max(1,Math.round(main.height*scale));if(scene!=null&&scene.width==main.width&&scene.height==main.height&&w==width&&h==height)return;freeTargets();width=w;height=h;scene=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);scene.setFilterMode(GL11.GL_LINEAR);volume=new Target(w,h,2);blurA=new Target(Math.max(1,w/2),Math.max(1,h/2),1);blurB=new Target(Math.max(1,w/2),Math.max(1,h/2),1);}
 static int loc(int p,String n){return GL20.glGetUniformLocation(p,n);}
 static void vec(int p,String n,Vector3f v){GL20.glUniform3f(loc(p,n),v.x,v.y,v.z);}
 static void texture(int p,String name,int t,int unit){GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);GlStateManager._bindTexture(t);GL20.glUniform1i(loc(p,name),unit);}
 static void pass(int p,int f,int w,int h){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);RenderSystem.viewport(0,0,w,h);GlStateManager._glUseProgram(p);GL20.glUniform2f(loc(p,"viewport"),w,h);GL30.glBindVertexArray(vao);}
 static void draw(){GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
 static void render(RenderLevelStageEvent event)throws Exception{
  long cpuStarted=System.nanoTime();PerfTrace.mark(1);
  var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
  if(mc.options.graphicsMode().get()==GraphicsStatus.FABULOUS)throw new IllegalStateException("Fabulous targets require a separate adapter");
  if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)throw new IllegalStateException("Shader pipeline replaced the main target; adapter required");
  try(State state=new State()){
   init();Recipe recipe=EnergyVfx.Client.recipe;float[] u=recipe.values;targets(main,u[37]);
   GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);PerfTrace.mark(2);PerfTrace.mark(8);
   RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
   int p=volumeProgram;pass(p,volume.f,width,height);GL20.glUniform1fv(loc(p,"u[0]"),u);GL20.glUniform1f(loc(p,"clockTime"),(EnergyVfx.Client.ticks+PerformanceQa.partial(event.getPartialTick()))/20f);GL20.glUniform3fv(loc(p,"colorA"),recipe.a);GL20.glUniform3fv(loc(p,"colorB"),recipe.b);
   if(u[39]>.5){EnergyVfx.Client.anchor=AuraEmitter.anchor(PerformanceQa.partial(event.getPartialTick()));AuraEmitter.upload(p,PerformanceQa.partial(event.getPartialTick()));}else{GL20.glUniform1i(loc(p,"puffCount"),0);GL20.glUniform1f(loc(p,"auraOpacity"),1);}
   var position=event.getCamera().getPosition().subtract(EnergyVfx.Client.anchor);vec(p,"camera",new Vector3f((float)position.x,(float)position.y,(float)position.z));
   Matrix4f view=new Matrix4f(event.getPoseStack().last().pose()),invView=new Matrix4f(view).invert();
   vec(p,"rightVec",invView.transformDirection(new Vector3f(1,0,0)));vec(p,"upVec",invView.transformDirection(new Vector3f(0,1,0)));vec(p,"forwardVec",invView.transformDirection(new Vector3f(0,0,-1)));
   Matrix4f projection=event.getProjectionMatrix();GL20.glUniform1f(loc(p,"tanHalfFov"),1f/projection.m11());Matrix4f inverse=new Matrix4f(projection).mul(view).invert();float[] matrix=new float[16];inverse.get(matrix);GL20.glUniformMatrix4fv(loc(p,"inverseViewProjection"),false,matrix);GL20.glUniform1i(loc(p,"gameScene"),1);GL20.glUniform1i(loc(p,"environment"),0);
   texture(p,"sceneColor",scene.getColorTextureId(),0);texture(p,"sceneDepth",scene.getDepthTextureId(),1);draw();PerfTrace.mark(3);
   p=blurProgram;pass(p,blurA.f,blurA.w,blurA.h);texture(p,"image",volume.textures[1],0);GL20.glUniform2f(loc(p,"direction"),u[22]/width,0);draw();PerfTrace.mark(4);
   pass(p,blurB.f,blurB.w,blurB.h);texture(p,"image",blurA.textures[0],0);GL20.glUniform2f(loc(p,"direction"),0,u[22]/height);draw();PerfTrace.mark(5);
   p=compositeProgram;pass(p,main.frameBufferId,main.width,main.height);texture(p,"image",volume.textures[0],0);texture(p,"glow",blurB.textures[0],1);texture(p,"sceneColor",scene.getColorTextureId(),2);texture(p,"sceneDepth",scene.getDepthTextureId(),3);
   vec(p,"camera",new Vector3f((float)position.x,(float)position.y,(float)position.z));vec(p,"rightVec",invView.transformDirection(new Vector3f(1,0,0)));vec(p,"upVec",invView.transformDirection(new Vector3f(0,1,0)));vec(p,"forwardVec",invView.transformDirection(new Vector3f(0,0,-1)));GL20.glUniform1f(loc(p,"tanHalfFov"),1f/projection.m11());GL20.glUniform1i(loc(p,"gameScene"),1);GL20.glUniform1i(loc(p,"environment"),0);GL20.glUniformMatrix4fv(loc(p,"inverseViewProjection"),false,matrix);GL20.glUniform1f(loc(p,"distortion"),u[24]);GL20.glUniform1f(loc(p,"clockTime"),(EnergyVfx.Client.ticks+PerformanceQa.partial(event.getPartialTick()))/20f);GL20.glUniform1f(loc(p,"bloom"),u[21]);draw();PerfTrace.mark(6);EnergyVfx.Client.draws++;EnergyVfx.Client.lastGl=GL11.glGetError();if(EnergyVfx.Client.lastGl!=0)throw new IllegalStateException("GL error "+EnergyVfx.Client.lastGl);
  }finally{PerfTrace.cpu("vfxSubmitCpuMs",System.nanoTime()-cpuStarted);}
 }
 static void freeTargets(){if(scene!=null)scene.destroyBuffers();scene=null;if(volume!=null)volume.destroy();if(blurA!=null)blurA.destroy();if(blurB!=null)blurB.destroy();volume=blurA=blurB=null;}
 static void release(){freeTargets();for(int p:new int[]{volumeProgram,blurProgram,compositeProgram})if(p!=0)GL20.glDeleteProgram(p);if(vao!=0)GL30.glDeleteVertexArrays(vao);volumeProgram=blurProgram=compositeProgram=vao=0;}
 static final class State implements AutoCloseable {
  final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vertexArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
  final int[] viewport=new int[4],textures=new int[5],scissorBox=new int[4];final boolean[] mask=new boolean[4];
  int cacheArray;
  final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST),srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
  State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);java.nio.ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,b);for(int i=0;i<4;i++)mask[i]=b.get(i)!=0;for(int i=0;i<5;i++){GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(i==4)cacheArray=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);}GlStateManager._activeTexture(active);}
  static void flag(int flag,boolean value){if(value)GL11.glEnable(flag);else GL11.glDisable(flag);}
  public void close(){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);GlStateManager._glUseProgram(program);GL30.glBindVertexArray(vertexArray);for(int i=0;i<5;i++){GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);if(i==4)GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,cacheArray);}GlStateManager._activeTexture(active);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.colorMask(mask[0],mask[1],mask[2],mask[3]);GL11.glScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);flag(GL11.GL_SCISSOR_TEST,scissor);flag(GL11.GL_STENCIL_TEST,stencil);flag(GL30.GL_FRAMEBUFFER_SRGB,srgb);}
 }
}
