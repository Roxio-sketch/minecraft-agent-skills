package dev.alchemyvfx;

import com.google.gson.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;
import java.nio.charset.StandardCharsets;

/** QA-only direct evaluation of the deployed shared 3D field. Never replaces scene-depth screenshots. */
final class FieldProbe {
    private static final String VERTEX="#version 150\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}";
    private static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(error);}return s;}
    static JsonObject run(Recipe recipe,double time)throws Exception{
        String deployed;try(var in=FieldProbe.class.getResourceAsStream("/assets/alchemyvfx/shaders/volume.fsh")){if(in==null)throw new IllegalStateException("Missing deployed field");deployed=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String core=deployed.split("// SHARED_VOLUME_CORE_BEGIN\\R",2)[1].split("\\R// SHARED_VOLUME_CORE_END",2)[0];
        int framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),programBefore=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vaoBefore=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);int textureBefore=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int v=0,f=0,p=0,vao=0,target=0,texture=0;
        try{
            v=shader(GL20.GL_VERTEX_SHADER,VERTEX);f=shader(GL20.GL_FRAGMENT_SHADER,"#version 150\nuniform vec3 Probe;out vec4 result;\n"+core+"\nvoid main(){result=sampleAlchemy(Probe,Time);}\n");
            p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL30.glBindFragDataLocation(p,0,"result");GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));
            texture=GlStateManager._genTexture();GlStateManager._bindTexture(texture);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            target=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete float probe FBO");
            RenderSystem.viewport(0,0,1,1);RenderSystem.disableDepthTest();RenderSystem.disableBlend();RenderSystem.disableCull();GL11.glDisable(GL11.GL_SCISSOR_TEST);GlStateManager._glUseProgram(p);vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            GL20.glUniform1f(GL20.glGetUniformLocation(p,"Time"),(float)time);GL20.glUniform1f(GL20.glGetUniformLocation(p,"Phase"),.45f);GL20.glUniform1i(GL20.glGetUniformLocation(p,"Stage"),1);GL20.glUniform1i(GL20.glGetUniformLocation(p,"Seed"),recipe.i("seed"));
            for(String key:recipe.numericKeys())if(!key.equals("segments")&&!key.equals("seed"))GL20.glUniform1f(GL20.glGetUniformLocation(p,Character.toUpperCase(key.charAt(0))+key.substring(1)),(float)recipe.d(key));
            for(String key:Recipe.COLORS)GL20.glUniform3fv(GL20.glGetUniformLocation(p,Character.toUpperCase(key.charAt(0))+key.substring(1)),recipe.color(key));
            JsonArray samples=new JsonArray();double centerMaximum=0,centerLowDensity=0,centerHighMaximum=0;float[] pixel=new float[4];
            for(double radius:new double[]{0,.25,.65,1.1,1.45,1.8})for(double height:new double[]{.10,.18,.32,.52,.74}){
                GL20.glUniform3f(GL20.glGetUniformLocation(p,"Probe"),(float)radius,(float)height,0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
                JsonObject sample=new JsonObject();sample.addProperty("x",radius);sample.addProperty("y",height);sample.addProperty("z",0);sample.addProperty("density",pixel[3]);JsonArray rgb=new JsonArray();rgb.add(pixel[0]);rgb.add(pixel[1]);rgb.add(pixel[2]);sample.add("rgb",rgb);samples.add(sample);if(radius==0){centerMaximum=Math.max(centerMaximum,pixel[3]);if(height==.10)centerLowDensity=pixel[3];if(height>=.510)centerHighMaximum=Math.max(centerHighMaximum,pixel[3]);}
            }
            int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("Field probe GL error "+error);
            JsonObject result=new JsonObject();result.addProperty("method","deployed shared sampleAlchemy evaluated in RGBA32F 1x1 QA FBO");result.addProperty("coreSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(core.getBytes(StandardCharsets.UTF_8))));result.addProperty("sceneScreenshotsRetainHostDepth",true);result.addProperty("fieldProbeUsesSceneGeometry",false);result.addProperty("probeIsSceneScreenshot",false);result.addProperty("stage",1);result.addProperty("phase",.45);result.addProperty("time",time);result.addProperty("centerMaximumDensity",centerMaximum);result.addProperty("centerLowDensity",centerLowDensity);result.addProperty("centerHighMaximumDensity",centerHighMaximum);result.addProperty("glError",error);result.add("samples",samples);return result;
        }finally{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);GlStateManager._glUseProgram(programBefore);GL30.glBindVertexArray(vaoBefore);GlStateManager._bindTexture(textureBefore);GlStateManager._activeTexture(active);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if(v!=0)GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);if(p!=0)GL20.glDeleteProgram(p);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(texture!=0)GlStateManager._deleteTexture(texture);if(target!=0)GL30.glDeleteFramebuffers(target);
        }
    }
    private FieldProbe() {}
}
