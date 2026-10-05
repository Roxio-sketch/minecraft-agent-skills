package dev.lightninglab;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/** Bounded depth-tested emissive ribbons with smooth radial profiles. No scene lighting implied. */
@Mod.EventBusSubscriber(modid=LightningLab.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LightningRender extends RenderType {
    private LightningRender(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static RenderType core, glow;
    @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(LightningLab.MOD_ID,"bolt"), DefaultVertexFormat.POSITION_COLOR_TEX),
            s->{shader=s;core=null;glow=null;System.out.println("[LIGHTNINGLAB] lightning shader compiled");});
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
    private record CoverageView(Matrix4f clip,int width,int height) {}
    private static CoverageView coverageView(PoseStack pose) {
        try(var stack=MemoryStack.stackPush()) {
            var viewport=stack.mallocInt(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            // Matches ProjMat * ModelViewMat * the CPU vertex pose in bolt.vsh.
            return new CoverageView(new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()),viewport.get(2),viewport.get(3));
        }
    }
    static void drawOrbit(List<OrbitField.Segment> list,LightningRecipe r,PoseStack pose,Vec3 camera) {
        if(shader==null||list.isEmpty())return;
        CoverageView view=coverageView(pose);
        Map<Long,List<Node>> paths=new LinkedHashMap<>();
        for(OrbitField.Segment seg:list) {
            var nodes=paths.computeIfAbsent((long)seg.strand(),key->new ArrayList<>());
            if(nodes.isEmpty())nodes.add(new Node(seg.a(),seg.width(),seg.alongA(),seg.alpha()));
            if(seg.a().distanceToSqr(seg.b())>1e-12)nodes.add(new Node(seg.b(),seg.width(),seg.alongB(),seg.alpha()));
        }
        double baseWidth=r.mode.equals("orbit")?r.currentWidth:r.strikeWidth;
        halos(paths.values(),r,baseWidth,pose,camera,view);
        material(r,false);
        for(var path:paths.values())ribbon(path,2,pose,camera,false,r,view);
        BUFFER.endBatch(type(false));
    }
    static void drawBolt(BoltGeometry bolt,LightningRecipe r,double ticks,PoseStack pose,Vec3 camera) {
        if(shader==null)return;
        LightningEnvelope env=new LightningEnvelope(r);
        double power=env.brightness(ticks),progress=env.propagation(ticks);
        if(power<=0)return;
        CoverageView view=coverageView(pose);
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
        halos(paths.values(),r,Math.max(.004,r.strikeWidth),pose,camera,view);
        material(r,false);
        for(var path:paths.values())ribbon(path,2,pose,camera,false,r,view);
        BUFFER.endBatch(type(false));
    }
    private static void halos(java.util.Collection<List<Node>> paths,LightningRecipe r,double baseWidth,PoseStack pose,Vec3 camera,CoverageView view){
        if(r.glowGain<=0)return;
        double scale=LightningAppearance.ribbonScale(r,true,baseWidth);
        List<List<HaloGeometry.Point>> strokes=new ArrayList<>();
        for(var path:paths)strokes.add(path.stream().map(node->new HaloGeometry.Point(node.p().subtract(camera),node.width()*scale,node.along(),node.alpha())).toList());
        LightningHalo.draw(strokes,r,view.clip(),pose.last().pose(),view.width(),view.height());
    }
    private static void ribbon(List<Node> path,double scale,PoseStack pose,Vec3 cam,boolean halo,LightningRecipe r,CoverageView view) {
        if(path.size()<2)return;
        List<Vec3> points=path.stream().map(Node::p).toList();
        List<Vec3> joins=RibbonGeometry.joins(points,cam);
        VertexConsumer out=BUFFER.getBuffer(type(halo));
        Matrix4f matrix=pose.last().pose();
        // One coverage result per shared endpoint, reused by both adjacent quads.
        var coverage=new ArrayList<RibbonCoverage.Join>(path.size());
        for(int i=0;i<path.size();i++) {
            Node node=path.get(i);
            Vec3 previous=i==0?null:path.get(i-1).p().subtract(cam),next=i+1==path.size()?null:path.get(i+1).p().subtract(cam);
            coverage.add(RibbonCoverage.cover(node.p().subtract(cam),joins.get(i).scale(node.width()*scale),previous,next,view.clip(),view.width(),view.height()));
        }
        for(int i=0;i<path.size()-1;i++) {
            Node a=path.get(i),b=path.get(i+1);
            Vec3 pa=a.p().subtract(cam),pb=b.p().subtract(cam);
            Vec3 sa=coverage.get(i).side(),sb=coverage.get(i+1).side();
            float aa=(float)LightningMath.clamp(a.alpha(),0,1),ab=(float)LightningMath.clamp(b.alpha(),0,1);
            float ua=(float)coverage.get(i).alphaScale(),ub=(float)coverage.get(i+1).alphaScale();
            float[] ca=LightningAppearance.colorAt(r,halo,a.along()),cb=LightningAppearance.colorAt(r,halo,b.along());
            // Adjacent quads reuse precisely the same two positions and colors.
            RibbonCoverage.emit(out,matrix,pa.add(sa),aa,ua,0,ca);
            RibbonCoverage.emit(out,matrix,pa.subtract(sa),aa,ua,1,ca);
            RibbonCoverage.emit(out,matrix,pb.subtract(sb),ab,ub,1,cb);
            RibbonCoverage.emit(out,matrix,pb.add(sb),ab,ub,0,cb);
        }
    }
}
