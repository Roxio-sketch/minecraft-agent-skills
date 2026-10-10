package dev.vfxworkbench.effects.blackhole;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Fixed particle identities; all trajectory and lifecycle updates run in the shader. */
final class BlackHoleParticles {
    static ShaderInstance shader;
    static boolean enabled=true;
    static int count=640,debug=0;
    static float speed=1f,size=.022f,outer=5.4f,brightness=1.25f;
    static long draws;
    private static VertexBuffer buffer;
    private static int allocated;
    private static final int[][] CORNERS={{-1,-1},{1,-1},{1,1},{-1,1}};
    static void release(){if(buffer!=null){buffer.close();buffer=null;}allocated=0;}
    static void render(Matrix4f vp,Matrix4f view,Vec3 relative,Vec3 origin){
        if(!enabled||count==0||shader==null)return;
        int active=debug==0?count:1;
        if(buffer==null||allocated!=active){
            release();buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
            BufferBuilder b=new BufferBuilder(active*4*20);b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            for(int i=0;i<active;i++)for(int[] c:CORNERS)
                b.vertex(i,c[0],c[1]).uv((c[0]+1)*.5f,(c[1]+1)*.5f).endVertex();
            buffer.bind();buffer.upload(b.end());VertexBuffer.unbind();allocated=active;
        }
        ShaderInstance s=shader;BlackHoleRender.material(s,BlackHoleClient.coreColor);
        s.safeGetUniform("uTilt").set(BlackHoleWorld.tilt);
        s.safeGetUniform("uSpeed").set(BlackHoleWorld.rotation);
        s.safeGetUniform("ViewProjection").set(vp);
        s.safeGetUniform("CenterRelative").set((float)relative.x,(float)relative.y,(float)relative.z);
        s.safeGetUniform("CameraOriginRelative").set((float)origin.x,(float)origin.y,(float)origin.z);
        s.safeGetUniform("WorldScale").set(BlackHoleWorld.scale);
        Vector3f right=new Vector3f(1,0,0),up=new Vector3f(0,1,0);
        Matrix4f inverseView=new Matrix4f(view).invert();
        inverseView.transformDirection(right).normalize();inverseView.transformDirection(up).normalize();
        s.safeGetUniform("CameraRight").set(right.x,right.y,right.z);s.safeGetUniform("CameraUp").set(up.x,up.y,up.z);
        s.safeGetUniform("ParticleSpeed").set(speed);s.safeGetUniform("ParticleSize").set(size);
        s.safeGetUniform("ParticleOuter").set(outer);s.safeGetUniform("ParticleBrightness").set(brightness);s.safeGetUniform("ParticleDebug").set(debug);
        var p=dev.vfxworkbench.core.ParamRegistry.state(BlackHoleSkillSchema.ID); BlackHoleRender.color(s,"ParticleCold",p.getColor("particleCold"));BlackHoleRender.color(s,"ParticleHot",p.getColor("particleHot"));
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);RenderSystem.disableCull();
        RenderSystem.setShader(()->s);
        buffer.bind();
        try{
            // Keep ShaderInstance's cached BlendMode at the world's default.
            // Apply the additive pass after shader binding, then the caller restores GL state.
            s.apply();RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            buffer.draw();draws++;
        }finally{s.clear();VertexBuffer.unbind();}
    }
}
