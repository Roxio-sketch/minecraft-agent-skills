package dev.shieldfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import dev.shieldfx.ShieldGeometry.*;

@Mod.EventBusSubscriber(modid="shieldfx",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ShieldRender extends RenderType {
    private ShieldRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.TRIANGLES,256,false,false,()->{},()->{});}
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(1048576));
    private static final RenderType TYPE=create("shield_fx",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.TRIANGLES,1048576,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final RenderType SHELL=create("shield_shell",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.TRIANGLES,1048576,false,false,CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException {e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("shieldfx","water"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);}

    static void draw(ShieldRecipe r,Vec3 origin,PoseStack pose,Vec3 camera,float time,ShieldLifecycle life,List<Triangle> mesh,List<Patch> fragments,V[] hits,float[] hitAge,List<ShieldSparks.Spark> sparks,float partial) {
        if(shader==null)return;
        configure(r,time,life.life(),hits,hitAge,partial);
        V offset=new V(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);Matrix4f matrix=pose.last().pose();
        double age=life.age()+(life.state()==ShieldLifecycle.State.SHATTERING?partial/20.0:0);
        if(life.state()==ShieldLifecycle.State.INTACT){
            set("uMode",0);set("uShatterAge",age);set("uAlphaScale",1);
            RenderType shell=r.shape.equals("sphere")||r.shape.equals("dome")?SHELL:TYPE;
            VertexConsumer out=BUFFER.getBuffer(shell);
            // Far surfaces first, then nearer surfaces. Preserve transparency on the closed shell.
            mesh.stream().sorted(Comparator.comparingDouble((Triangle t)->t.center().add(offset).dot(t.center().add(offset))).reversed()).forEach(t->{for(Vertex v:t.vertices())emit(out,matrix,v.pos().add(offset),v.normal(),v,1);});
            BUFFER.endBatch(shell);
        }
        if(life.state()==ShieldLifecycle.State.SHATTERING){
        double fade=1-smooth(r.shatterDuration*.45,r.shatterDuration,age);
        set("uMode",1);set("uShatterAge",age);set("uAlphaScale",fade);set3("uEdgeColor","#ff4220");
        List<Patch> sorted=fragments.stream().sorted(Comparator.comparingDouble((Patch p)->p.point(p.center(),age,r).add(offset).dot(p.point(p.center(),age,r).add(offset))).reversed()).toList();
        VertexConsumer out=BUFFER.getBuffer(TYPE);
        for(Patch p:sorted)for(Triangle t:p.triangles())for(Vertex v:t.vertices())emit(out,matrix,p.point(v.pos(),age,r).add(offset),p.normal(v.normal(),age,r),v,1);
        BUFFER.endBatch(TYPE);
        set("uMode",2);set("uAlphaScale",fade);
        out=BUFFER.getBuffer(TYPE);
        for(Patch p:sorted){double crack=smooth(p.delay()-.045,p.delay(),age);if(crack<=0)continue;for(Edge edge:p.edges()){
            V a=p.point(edge.a().pos(),age,r).add(offset),b=p.point(edge.b().pos(),age,r).add(offset);V side=b.sub(a).cross(a.add(b).mul(.5)).unit().mul(.005);
            V n=p.normal(edge.a().normal(),age,r);emit(out,matrix,a.add(side),n,edge.a(),crack);emit(out,matrix,b.add(side),n,edge.b(),crack);emit(out,matrix,a.sub(side),n,edge.a(),crack);
            emit(out,matrix,a.sub(side),n,edge.a(),crack);emit(out,matrix,b.add(side),n,edge.b(),crack);emit(out,matrix,b.sub(side),n,edge.b(),crack);
        }}BUFFER.endBatch(TYPE);
        }
        drawSparks(r,sparks,time,offset,matrix);
    }
    private static void drawSparks(ShieldRecipe r,List<ShieldSparks.Spark> sparks,double time,V offset,Matrix4f matrix){
        if(sparks.isEmpty())return;set("uMode",3);set("uAlphaScale",1);set3("uSparkColor",r.sparkColor);VertexConsumer out=BUFFER.getBuffer(TYPE);
        for(ShieldSparks.Spark spark:sparks){double alpha=spark.alpha(time);if(alpha<=0)continue;V a=spark.position(time).add(offset),b=spark.trail(time).add(offset);
            V direction=a.sub(b);if(direction.length()<1e-5)b=a.sub(spark.velocity().unit().mul(.035));
            V side=a.sub(b).cross(a.add(b).mul(.5)).unit().mul(.005+alpha*.01);Vertex source=new Vertex(new V(0,0,0),new V(0,0,1),.5,.5);V n=new V(0,0,1);
            emit(out,matrix,a.add(side),n,source,alpha);emit(out,matrix,b.add(side),n,source,alpha);emit(out,matrix,a.sub(side),n,source,alpha);
            emit(out,matrix,a.sub(side),n,source,alpha);emit(out,matrix,b.add(side),n,source,alpha);emit(out,matrix,b.sub(side),n,source,alpha);
        }BUFFER.endBatch(TYPE);
    }
    private static double smooth(double a,double b,double x){double t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    private static void emit(VertexConsumer out,Matrix4f matrix,V p,V normal,Vertex source,double alpha){normal=normal.unit();out.vertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).color((float)(normal.x()*.5+.5),(float)(normal.y()*.5+.5),(float)(normal.z()*.5+.5),(float)alpha).uv((float)source.u(),(float)source.v()).endVertex();}
    private static void configure(ShieldRecipe r,float time,double life,V[] hits,float[] age,float partial){
        set3("uColor",r.color);set3("uEdgeColor",r.edgeColor);set("uTime",time);set("uLife",life);set("uShape",switch(r.shape){case "sphere"->0;case "dome"->1;case "convex"->2;default->3;});set("uPattern",switch(r.pattern){case "hex"->0;case "diamond"->1;case "dots"->2;case "grid"->3;default->4;});set("uDepth",r.depth);
        set("uOpacity",r.opacity);set("uCenterOpacity",r.centerOpacity);set("uEdgeOpacity",r.edgeOpacity);set("uFresnelPower",r.fresnelPower);set("uFresnelStrength",r.fresnelStrength);
        set("uHexScale",r.hexScale);set("uHexOpacity",r.hexOpacity);set("uLineWidth",r.lineWidth);set("uFlashSpeed",r.flashSpeed);set("uFlashIntensity",r.flashIntensity);set("uShowHex",r.showHex?1:0);
        set("uFlowScale",r.flowScale);set("uFlowSpeed",r.flowSpeed);set("uFlowIntensity",r.flowIntensity);set("uShowFlow",r.showFlow?1:0);
        set("uReveal",r.reveal);set("uNoiseScale",r.noiseScale);set("uNoiseEdgeWidth",r.noiseEdgeWidth);set("uNoiseEdgeIntensity",r.noiseEdgeIntensity);set("uShowDissolve",r.showDissolve?1:0);
        set("uScanSpeed",r.scanSpeed);set("uScanStrength",r.scanStrength);set("uScanWidth",r.scanWidth);set("uShowScan",r.showScan?1:0);
        set("uDistortion",r.distortion);set("uDistortionScale",r.distortionScale);set("uDistortionSpeed",r.distortionSpeed);set("uPulseAmplitude",r.pulseAmplitude);set("uPulseFrequency",r.pulseFrequency);
        set("uHitSpeed",r.hitSpeed);set("uHitWidth",r.hitWidth);set("uHitDuration",r.hitDuration);set("uHitIntensity",r.hitIntensity);set("uHitRadius",r.hitRadius);set("uShatterFlash",r.shatterFlash);
        for(int i=0;i<6;i++){shader.safeGetUniform("uHit"+i).set((float)hits[i].x(),(float)hits[i].y(),(float)hits[i].z());set("uHitAge"+i,age[i]+partial/20f);}
    }
    private static void set(String name,double value){shader.safeGetUniform(name).set((float)value);}
    private static void set3(String name,String hex){int c=Integer.parseInt(hex.substring(1),16);shader.safeGetUniform(name).set((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f);}
}
