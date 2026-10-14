package dev.energyvfx;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.*;

/** Exact per-lattice coefficients, RGBA32F, every rendered frame; no spatial/time interpolation. */
final class FlowCache {
 static int program,texture;static boolean active;
 private static final float[] ages=new float[32];
 static boolean supported(){return GL.getCapabilities().OpenGL43;}
 static void init()throws Exception{
  if(program!=0)return;
  int shader=0;try{
   shader=Gpu.shader(GL43.GL_COMPUTE_SHADER,"#version 430\n"+Recipe.resource("shaders/noise.glsl")+Recipe.resource("shaders/field.glsl")+Recipe.resource("shaders/flow-cache.glsl"));
   program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
   if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
   texture=GL11.glGenTextures();GlStateManager._activeTexture(GL13.GL_TEXTURE4);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,texture);
   GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY,1,GL30.GL_RGBA32F,169,13,32*6);
   GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
  }catch(Exception x){release();throw x;}finally{if(shader!=0)GL20.glDeleteShader(shader);}
 }
 static void prepare(float clock,float speed,int volumeProgram)throws Exception{
  active=false;
  boolean enabled=supported()&&!Boolean.getBoolean("energyvfx.disableFlowCache")&&!(PerformanceQa.ENABLED&&PerformanceQa.FLOW&&!PerformanceQa.optimized);
  if(enabled&&AuraEmitter.uploadCount>0){
   init();int oldTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
   int image=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME,0),level=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL,0),layer=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER,0),access=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS,0),format=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT,0);boolean layered=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED,0)!=0;
   try{
    GlStateManager._glUseProgram(program);for(int i=0;i<AuraEmitter.uploadCount;i++)ages[i]=AuraEmitter.uploadFlows[i*4+3];
    GL20.glUniform1i(Uniforms.location(program,"cachePackets"),AuraEmitter.uploadCount);
    GL20.glUniform1f(Uniforms.location(program,"cacheClock"),clock);GL20.glUniform1f(Uniforms.location(program,"cacheSpeed"),speed);
    GL20.glUniform1fv(Uniforms.location(program,"cacheSeeds[0]"),AuraEmitter.uploadSeeds);GL20.glUniform1fv(Uniforms.location(program,"cacheAges[0]"),ages);
    GL42.glBindImageTexture(0,texture,0,true,0,GL15.GL_WRITE_ONLY,GL30.GL_RGBA32F);
    // Reuse the image only after the previous frame's texture reads complete.
    GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);GL43.glDispatchCompute(22,2,AuraEmitter.uploadCount*6);
    GL42.glMemoryBarrier(GL42.GL_TEXTURE_FETCH_BARRIER_BIT|GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);active=true;
   }finally{GL42.glBindImageTexture(0,image,level,layered,layer,access,format);GlStateManager._glUseProgram(oldProgram);GlStateManager._activeTexture(oldTexture);}
  }
  GlStateManager._glUseProgram(volumeProgram);GL20.glUniform1i(Uniforms.location(volumeProgram,"smokeCacheEnabled"),active?1:0);GL20.glUniform1i(Uniforms.location(volumeProgram,"smokeVortexCache"),4);
  GlStateManager._activeTexture(GL13.GL_TEXTURE4);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,texture);
 }
 static void release(){if(program!=0)GL20.glDeleteProgram(program);if(texture!=0)GL11.glDeleteTextures(texture);program=texture=0;active=false;}
}
