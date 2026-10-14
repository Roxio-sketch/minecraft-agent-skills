package dev.alchemyvfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Full-resolution 3D ray integration, copying depth only and restoring all touched GL state. */
final class VolumeGpu {
    static int program,vao,depthTexture,depthFbo,width,height,generation,draws,lastGl;
    static double coverage,cpuMs;
    static boolean failed;
    private static final Map<String,Integer> UNIFORMS=new HashMap<>();
    private static final float[] MATRIX=new float[16];
    private static final String VERTEX="#version 150\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}";
    private static String resource(String name)throws Exception{try(var in=VolumeGpu.class.getResourceAsStream("/assets/alchemyvfx/shaders/"+name)){if(in==null)throw new IllegalStateException("Missing volume resource: "+name);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String message=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(message);}return s;}
    private static void init()throws Exception{if(program!=0)return;int v=0,f=0,p=GL20.glCreateProgram();try{v=shader(GL20.GL_VERTEX_SHADER,VERTEX);f=shader(GL20.GL_FRAGMENT_SHADER,resource("volume.fsh"));GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL30.glBindFragDataLocation(p,0,"result");GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));program=p;vao=GL30.glGenVertexArrays();generation++;failed=false;}catch(Exception failure){GL20.glDeleteProgram(p);throw failure;}finally{if(v!=0)GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}}
    private static int loc(String name){return UNIFORMS.computeIfAbsent(name,key->GL20.glGetUniformLocation(program,key));}
    private static void scalar(String name,double value){GL20.glUniform1f(loc(name),(float)value);}
    private static void vector(String name,Vec3 value){GL20.glUniform3f(loc(name),(float)value.x,(float)value.y,(float)value.z);}
    private static Vec3 local(Vec3 world,Vec3 anchor,double yaw){Vec3 d=world.subtract(anchor);double c=Math.cos(yaw),s=Math.sin(yaw);return new Vec3(c*d.x-s*d.z,d.y,s*d.x+c*d.z);}
    private static void targets(com.mojang.blaze3d.pipeline.RenderTarget main){
        if(width==main.width&&height==main.height&&depthFbo!=0)return;freeTargets();width=main.width;height=main.height;
        GlStateManager._bindTexture(main.getDepthTextureId());int internal=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
        depthTexture=GlStateManager._genTexture();GlStateManager._bindTexture(depthTexture);
        boolean packed=internal==GL30.GL_DEPTH24_STENCIL8||internal==GL30.GL_DEPTH32F_STENCIL8;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,width,height,0,packed?GL30.GL_DEPTH_STENCIL:GL11.GL_DEPTH_COMPONENT,packed?(internal==GL30.GL_DEPTH32F_STENCIL8?GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV:GL30.GL_UNSIGNED_INT_24_8):GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE);
        depthFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,depthFbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depthTexture,0);GL11.glDrawBuffer(GL11.GL_NONE);GL11.glReadBuffer(GL11.GL_NONE);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete depth-copy framebuffer");
    }
    private record Bounds(Vec3 lo,Vec3 hi) {}
    private record Rect(int x0,int y0,int x1,int y1){boolean empty(){return x1<=x0||y1<=y0;}void apply(){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x0,y0,x1-x0,y1-y0);}static Rect full(int w,int h){return new Rect(0,0,w,h);}}
    private static Rect project(Bounds b,Matrix4f localProjection,int w,int h){
        double lx=Double.POSITIVE_INFINITY,ly=lx,hx=Double.NEGATIVE_INFINITY,hy=hx;
        for(int i=0;i<8;i++){
            Vector4f p=new Vector4f((float)((i&1)==0?b.lo.x:b.hi.x),(float)((i&2)==0?b.lo.y:b.hi.y),(float)((i&4)==0?b.lo.z:b.hi.z),1);localProjection.transform(p);
            if(!Float.isFinite(p.w)||p.w<=.0001f||p.z<=-p.w)return Rect.full(w,h);
            double x=p.x/p.w,y=p.y/p.w;lx=Math.min(lx,x);ly=Math.min(ly,y);hx=Math.max(hx,x);hy=Math.max(hy,y);
        }
        int x0=(int)Math.floor((lx+1)*w*.5)-4,y0=(int)Math.floor((ly+1)*h*.5)-4,x1=(int)Math.ceil((hx+1)*w*.5)+4,y1=(int)Math.ceil((hy+1)*h*.5)+4;
        if(x1<=0||y1<=0||x0>=w||y0>=h)return new Rect(0,0,0,0);
        return new Rect(Math.max(0,x0),Math.max(0,y0),Math.min(w,x1),Math.min(h,y1));
    }
    private static Bounds bounds(Recipe recipe){
        double radial=Math.max(recipe.d("fireRadius")+.8,Math.max(recipe.d("sigilRadius")+.25,Math.max(recipe.d("burstRadius")+.6,recipe.d("vaporRadius")+recipe.d("vaporSpread")*2+1)));
        double top=Math.max(5.75,3.2417925+recipe.d("vaporHeight")*1.7+.6);
        radial=Math.max(radial,Math.max(recipe.d("energyRingRadius")+.65,2.085+recipe.d("faceSparkSpeed")*recipe.d("faceSparkLife")*1.25+.3));
        top=Math.max(top,1.393125+Math.max(recipe.d("aromaHeight")*1.55,recipe.d("faceHeatHeight")*1.5)+.2);
        return new Bounds(new Vec3(-radial,-.15,-radial),new Vec3(radial,top,radial));
    }
    static boolean render(RenderLevelStageEvent event,Recipe recipe,HostAdapter.Furnace furnace)throws Exception{
        long began=System.nanoTime();var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(mc.options.graphicsMode().get()==GraphicsStatus.FABULOUS)throw new IllegalStateException("Fabulous rendering requires a separate adapter");
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)throw new IllegalStateException("Main target replaced by another renderer; adapter required");
        try(State state=new State()){
            init();GlStateManager._activeTexture(GL13.GL_TEXTURE0);targets(main);
            Bounds bounds=bounds(recipe);Vec3 worldCamera=event.getCamera().getPosition(),camera=local(worldCamera,furnace.anchor(),furnace.yaw());
            Matrix4f vp=new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());new Matrix4f(vp).invert().get(MATRIX);
            Vec3 anchorRelative=furnace.anchor().subtract(worldCamera);
            Matrix4f bodyProjection=new Matrix4f(vp).translate((float)anchorRelative.x,(float)anchorRelative.y,(float)anchorRelative.z).rotateY((float)furnace.yaw());
            Rect area=project(bounds,bodyProjection,width,height);coverage=area.empty()?0:(double)(area.x1-area.x0)*(area.y1-area.y0)/(width*height);if(area.empty())return false;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,depthFbo);GL30.glBlitFramebuffer(area.x0,area.y0,area.x1,area.y1,area.x0,area.y0,area.x1,area.y1,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,main.frameBufferId);RenderSystem.viewport(0,0,width,height);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);RenderSystem.colorMask(true,true,true,true);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);area.apply();GlStateManager._glUseProgram(program);GL30.glBindVertexArray(vao);
            GL20.glUniform2f(loc("Viewport"),width,height);GL20.glUniformMatrix4fv(loc("InverseViewProjection"),false,MATRIX);vector("Camera",camera);vector("BoundsMin",bounds.lo);vector("BoundsMax",bounds.hi);scalar("Yaw",furnace.yaw());
            double tick=mc.level.getGameTime()+event.getPartialTick(),progress=furnace.progress(tick),phase;int stage;
            if(furnace.brewing()){if(progress<.15){stage=0;phase=progress/.15;}else if(progress<.70){stage=1;phase=(progress-.15)/.55;}else{stage=2;phase=(progress-.70)/.30;}}
            else{stage=3;phase=Math.max(0,Math.min(1,furnace.completionAge()/60.));}
            scalar("Time",tick*.05);scalar("Phase",phase);GL20.glUniform1i(loc("Stage"),stage);GL20.glUniform1i(loc("Steps"),recipe.i("segments"));GL20.glUniform1i(loc("Seed"),recipe.i("seed"));
            for(String key:recipe.numericKeys())if(!key.equals("segments")&&!key.equals("seed"))scalar(Character.toUpperCase(key.charAt(0))+key.substring(1),recipe.d(key));
            for(String key:Recipe.COLORS)GL20.glUniform3fv(loc(Character.toUpperCase(key.charAt(0))+key.substring(1)),recipe.color(key));
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(depthTexture);GL20.glUniform1i(loc("SceneDepth"),0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);draws++;
            lastGl=GL11.glGetError();if(lastGl!=0)throw new IllegalStateException("Alchemy volume GL error "+lastGl);failed=false;return true;
        }catch(Exception failure){failed=true;throw failure;}finally{cpuMs=(System.nanoTime()-began)/1e6;}
    }
    private static void freeTargets(){if(depthTexture!=0)GlStateManager._deleteTexture(depthTexture);if(depthFbo!=0)GL30.glDeleteFramebuffers(depthFbo);depthTexture=depthFbo=width=height=0;}
    static void release(){freeTargets();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);program=vao=lastGl=draws=0;coverage=cpuMs=0;UNIFORMS.clear();failed=false;}
    private static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vertexArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int eqRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int[] viewport=new int[4],box=new int[4];final boolean[] mask=new boolean[4];final int texture;
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST),srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);java.nio.ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,b);for(int i=0;i<4;i++)mask[i]=b.get(i)!=0;GlStateManager._activeTexture(GL13.GL_TEXTURE0);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GlStateManager._activeTexture(active);}
        private static void flag(int key,boolean value){if(value)GL11.glEnable(key);else GL11.glDisable(key);}
        public void close(){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);GlStateManager._glUseProgram(program);GL30.glBindVertexArray(vertexArray);GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(texture);GlStateManager._activeTexture(active);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);GL20.glBlendEquationSeparate(eqRgb,eqAlpha);if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.colorMask(mask[0],mask[1],mask[2],mask[3]);GL11.glScissor(box[0],box[1],box[2],box[3]);flag(GL11.GL_SCISSOR_TEST,scissor);flag(GL11.GL_STENCIL_TEST,stencil);flag(GL30.GL_FRAMEBUFFER_SRGB,srgb);}
    }
    private VolumeGpu() {}
}
