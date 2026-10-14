package dev.lightninglab;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Matrix4f;

/** Bounded depth-tested emissive ribbons with smooth radial profiles. No scene lighting implied. */
@Mod.EventBusSubscriber(modid=LightningLab.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LightningRender extends RenderType {
    private LightningRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static RenderType core, glow;
    @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(LightningLab.MOD_ID,"bolt"), DefaultVertexFormat.POSITION_COLOR_TEX),
            s->{shader=s;core=null;glow=null;});
    }
    private static RenderType type(boolean halo) {
        RenderType out=halo?glow:core;
        if(out!=null)return out;
        out=RenderType.create(halo?"lightninglab_glow":"lightninglab_core",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        if(halo)glow=out;else core=out;
        return out;
    }
    private static void material(LightningRecipe r, boolean halo) {
        shader.safeGetUniform("Tint").set(1f,1f,1f);
        shader.safeGetUniform("Gain").set((float)(halo?r.glowGain:r.coreGain));
        shader.safeGetUniform("Profile").set(halo?1f:0f);
    }
    private record Node(Vec3 p,double width,double along,double alpha) {}
    static void drawOrbit(List<OrbitField.Segment> list,LightningRecipe r,PoseStack pose,Vec3 camera) {
        if(shader==null||list.isEmpty())return;
        Map<Long,List<Node>> paths=new LinkedHashMap<>();
        for(OrbitField.Segment seg:list) {
            var nodes=paths.computeIfAbsent((long)seg.strand(),key->new ArrayList<>());
            if(nodes.isEmpty())nodes.add(new Node(seg.a(),seg.width(),seg.alongA(),seg.alpha()));
            if(seg.a().distanceToSqr(seg.b())>1e-12)nodes.add(new Node(seg.b(),seg.width(),seg.alongB(),seg.alpha()));
        }
        for(boolean halo:new boolean[]{true,false}) {
            material(r,halo);
            double baseWidth=r.mode.equals("orbit")?r.currentWidth:r.strikeWidth;
            for(var path:paths.values())ribbon(path,halo?r.glowWidth/baseWidth:2,pose,camera,halo,r);
            BUFFER.endBatch(type(halo));
        }
    }
    static void drawBolt(BoltGeometry bolt,LightningRecipe r,double ticks,PoseStack pose,Vec3 camera) {
        if(shader==null)return;
        LightningEnvelope env=new LightningEnvelope(r);
        double power=env.brightness(ticks),progress=env.propagation(ticks);
        if(power<=0)return;
        Map<Long,List<Node>> paths=new LinkedHashMap<>();
        for(BoltGeometry.Segment seg:bolt.segments()) {
            if(seg.alongA()>=progress)continue;
            double f=LightningMath.clamp((progress-seg.alongA())/Math.max(1e-9,seg.alongB()-seg.alongA()),0,1);
            Vec3 end=seg.a().lerp(seg.b(),f);
            if(seg.a().distanceToSqr(end)<1e-12)continue;
            var nodes=paths.computeIfAbsent(seg.mask(),key->new ArrayList<>());
            if(nodes.isEmpty())nodes.add(new Node(seg.a(),seg.widthA(),seg.alongA(),power*seg.intensity()));
            nodes.add(new Node(end,seg.widthA()+(seg.widthB()-seg.widthA())*f,
                seg.alongA()+(seg.alongB()-seg.alongA())*f,power*seg.intensity()));
        }
        for(boolean halo:new boolean[]{true,false}) {
            material(r,halo);
            for(var path:paths.values())ribbon(path,halo?r.glowWidth/Math.max(.004,r.strikeWidth):2,pose,camera,halo,r);
            BUFFER.endBatch(type(halo));
        }
    }
    private static void ribbon(List<Node> path,double scale,PoseStack pose,Vec3 cam,boolean halo,LightningRecipe r) {
        if(path.size()<2)return;
        List<Vec3> points=path.stream().map(Node::p).toList();
        List<Vec3> joins=RibbonGeometry.joins(points,cam);
        VertexConsumer out=BUFFER.getBuffer(type(halo));
        Matrix4f matrix=pose.last().pose();
        for(int i=0;i<path.size()-1;i++) {
            Node a=path.get(i),b=path.get(i+1);
            Vec3 pa=a.p().subtract(cam),pb=b.p().subtract(cam);
            Vec3 sa=joins.get(i).scale(a.width()*scale),sb=joins.get(i+1).scale(b.width()*scale);
            float aa=(float)LightningMath.clamp(a.alpha(),0,1),ab=(float)LightningMath.clamp(b.alpha(),0,1);
            float[] ca=colorAt(r,halo,a.along()),cb=colorAt(r,halo,b.along());
            // Adjacent quads reuse precisely the same two positions and colors.
            emit(out,matrix,pa.add(sa),aa,0,0,ca);
            emit(out,matrix,pa.subtract(sa),aa,0,1,ca);
            emit(out,matrix,pb.subtract(sb),ab,1,1,cb);
            emit(out,matrix,pb.add(sb),ab,1,0,cb);
        }
    }
    private static float[] colorAt(LightningRecipe r, boolean halo, double along) {
        int base=parse(halo?r.glowColor:r.coreColor), rgb=base;
        if(r.gradientEnabled) {
            int a=parse(r.gradientStart), b=parse(r.gradientEnd); double t=LightningMath.clamp(along,0,1);
            int gr=(int)Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t);
            int gg=(int)Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t);
            int gb=(int)Math.round((a&255)+((b&255)-(a&255))*t);
            rgb=((int)(((base>>16)&255)*gr/255.0)<<16)|((int)(((base>>8)&255)*gg/255.0)<<8)|((int)((base&255)*gb/255.0));
        }
        return new float[]{((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f};
    }
    private static int parse(String value) { return Integer.parseInt(value.substring(1),16); }
    private static void emit(VertexConsumer out,Matrix4f m,Vec3 p,float a,float u,float v,float[] rgb){
        out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(rgb[0],rgb[1],rgb[2],a).uv(u,v).endVertex();
    }
}
