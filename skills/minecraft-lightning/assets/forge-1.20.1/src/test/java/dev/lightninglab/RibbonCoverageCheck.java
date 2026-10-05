package dev.lightninglab;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteOrder;
import java.util.List;
final class RibbonCoverageCheck {
 static void run(){ checkRasterCoverage(); System.out.println("PASS: actual vertex packing, shared core corners, perspective/orthographic subpixel coverage without changing integrated energy"); }
    private static void checkRasterCoverage() {
        for(int[] viewport:List.of(new int[]{854,480},new int[]{1920,1080},new int[]{3840,2160}))
            for(double degrees:new double[]{40,70,110})for(double distance:new double[]{2,10,48,120,480})
                for(double physicalHalfWidth:new double[]{.001,.012,.0623,.076,.14,.4}) {
                    var projection=new Matrix4f().perspective((float)Math.toRadians(degrees),viewport[0]/(float)viewport[1],.05f,1024);
                    var center=new Vec3(0,0,-distance);
                    var side=new Vec3(physicalHalfWidth,0,0);
                    var covered=RibbonCoverage.cover(center,side,projection,viewport[0],viewport[1]);
                    double focal=viewport[1]/(2*Math.tan(Math.toRadians(degrees)*.5));
                    double nativePixels=physicalHalfWidth*focal/distance;
                    double renderedPixels=covered.side().x*focal/distance;
                    if(nativePixels<RibbonCoverage.MIN_HALF_PIXELS-1e-5) {
                        if(Math.abs(renderedPixels-RibbonCoverage.MIN_HALF_PIXELS)>2e-5||covered.alphaScale()<=0||covered.alphaScale()>1)throw new AssertionError("projected minimum coverage");
                        if(Math.abs(renderedPixels*covered.alphaScale()-nativePixels)>2e-5)throw new AssertionError("coverage changed integrated energy");
                    } else if(!covered.side().equals(side)||covered.alphaScale()!=1)throw new AssertionError("normal near width was changed");
                }
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),854f/480,.05f,1024);
        var points=List.of(new Vec3(-.8,-1.5,-48),new Vec3(0,0,-48),new Vec3(1.2,1.2,-48),new Vec3(.8,2.5,-48));
        var joins=RibbonGeometry.joins(points,Vec3.ZERO);
        double focal=480/(2*Math.tan(Math.toRadians(70)*.5));
        for(int i=0;i<points.size();i++) {
            Vec3 previous=i==0?null:points.get(i-1),next=i+1==points.size()?null:points.get(i+1);
            var covered=RibbonCoverage.cover(points.get(i),joins.get(i).scale(.006),previous,next,projection,854,480);
            for(Vec3 neighbor:new Vec3[]{previous,next}) {
                if(neighbor==null)continue;
                Vec3 tangent=neighbor.subtract(points.get(i));
                Vec3 p=points.get(i),s=covered.side();
                // Independent pinhole oracle, including the miter's depth component.
                double x=focal*((p.x+s.x)/(48-s.z)-(p.x-s.x)/(48+s.z))*.5;
                double y=focal*((p.y+s.y)/(48-s.z)-(p.y-s.y)/(48+s.z))*.5;
                double normalHalfPixels=Math.abs(x*tangent.y-y*tangent.x)/Math.hypot(tangent.x,tangent.y);
                if(normalHalfPixels<RibbonCoverage.MIN_HALF_PIXELS-2e-4)throw new AssertionError("shared corner loses adjacent segment coverage");
            }
        }
        Vec3 side=new Vec3(.01,0,0);
        for(Vec3 center:List.of(new Vec3(0,0,.01),new Vec3(0,0,0))) {
            var clipped=RibbonCoverage.cover(center,side,projection,854,480);
            if(!clipped.side().equals(side)||clipped.alphaScale()!=1)throw new AssertionError("near/behind camera expansion");
        }
        Vec3 facing=new Vec3(-.89432,0,-.44743);
        for(double[] sample:new double[][]{{.04997,.00005},{.05002,.00005},{.05002,.000005},{1024,.05},{1024.1,.05}}) {
            Vec3 center=new Vec3(sample[0]>1000?512:.025,0,-sample[0]),nativeSide=facing.scale(sample[1]);
            var clipped=RibbonCoverage.cover(center,nativeSide,projection,854,480);
            if(!clipped.side().equals(nativeSide)||clipped.alphaScale()!=1)throw new AssertionError("coverage crossed original/expanded near or far depth clipping: "+sample[0]);
        }
        var safeNear=RibbonCoverage.cover(new Vec3(.025,0,-.0505),facing.scale(.000005),projection,854,480);
        if(safeNear.alphaScale()>=1||safeNear.renderedHalfPixels()<RibbonCoverage.MIN_HALF_PIXELS-1e-4)throw new AssertionError("valid near-plane coverage disabled");
        var invalid=RibbonCoverage.cover(new Vec3(0,0,-48),side,projection,0,480);
        if(!invalid.side().equals(side)||invalid.alphaScale()!=1)throw new AssertionError("invalid viewport expansion");
        var orthographic=new Matrix4f().ortho(-4,4,-3,3,.05f,1024);
        var covered=RibbonCoverage.cover(new Vec3(0,0,-48),new Vec3(.003,0,0),orthographic,800,600);
        if(Math.abs(covered.side().x*100-RibbonCoverage.MIN_HALF_PIXELS)>1e-6||Math.abs(covered.alphaScale()-.4)>1e-6)throw new AssertionError("actual orthographic viewport coverage");
        var tiny=RibbonCoverage.cover(new Vec3(0,0,-480),new Vec3(.001,0,0),projection,854,480);
        if(tiny.alphaScale()>=1/255.0)throw new AssertionError("quantization regression sample is not thin enough");
        float envelope=.8f,ratio=(float)tiny.alphaScale();
        if((int)(envelope*ratio*255)!=0)throw new AssertionError("old alpha-byte transport would not reproduce this failure");
        var builder=new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
        for(int i=0;i<4;i++)RibbonCoverage.emit(builder,new Matrix4f(),new Vec3(i&1,i>>1,0),envelope,ratio,i&1,new float[]{1,1,1});
        var packed=builder.end();
        try {
            var bytes=packed.vertexBuffer().order(ByteOrder.nativeOrder());
            if(DefaultVertexFormat.POSITION_COLOR_TEX.getVertexSize()!=24)throw new AssertionError("unexpected POSITION_COLOR_TEX format");
            for(int i=0;i<4;i++) {
                int alpha=Byte.toUnsignedInt(bytes.get(i*24+15));float coverage=bytes.getFloat(i*24+16);
                if(alpha!=(int)(envelope*255)||coverage!=ratio||coverage<=0||alpha/255f*coverage<=0)throw new AssertionError("float coverage was lost in actual vertex packing");
            }
        } finally {packed.release();}
    }
}
