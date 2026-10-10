package dev.vfxworkbench.effects.lightning;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class PerformanceCheck {
    private static volatile double sink;
    static void run() {
        var r=new LightningRecipe();r.gradientEnabled=true;
        int checks=0;
        for(String target:List.of("both","core","glow"))for(double ratio:new double[]{0,23,50,60,100}) {
            r.gradientTarget=target;r.gradientRatio=ratio;
            for(boolean halo:new boolean[]{false,true}) {
                var palette=LightningAppearance.palette(r,halo);
                for(int i=-1;i<=1001;i++) {
                    double along=i/1000.0;
                    if(!Arrays.equals(palette.colorAt(along),LightningAppearance.colorAt(r,halo,along)))
                        throw new AssertionError("compiled palette changed material");
                    checks++;
                }
            }
        }
        r.gradientTarget="both";r.gradientRatio=60;
        Matrix4f clip=new Matrix4f().perspective((float)Math.toRadians(70),1.5f,.05f,500);
        var prepared=HaloGeometry.prepare(clip,900,600);
        var points=List.of(new HaloGeometry.Point(new Vec3(0,0,-6),.03,0,1),
            new HaloGeometry.Point(new Vec3(.3,1,-5),.02,.5,1),
            new HaloGeometry.Point(new Vec3(-.5,2,-7),.01,1,1));
        var expected=HaloGeometry.build(points,clip,900,600,r,true);
        clip.identity(); // The prepared camera must own its snapshot.
        for(boolean halo:new boolean[]{true,false}) {
            var mesh=HaloGeometry.build(points,prepared,r,halo);
            if(halo && (!mesh.bounds().equals(expected.bounds())||mesh.body().size()!=expected.body().size()))
                throw new AssertionError("prepared projection changed mesh");
            if(halo)for(int i=0;i<mesh.body().size();i++) {
                var a=mesh.body().get(i);var b=expected.body().get(i);
                if(!a.p().equals(b.p())||a.alpha()!=b.alpha()||a.u()!=b.u()||a.v()!=b.v()||!Arrays.equals(a.rgb(),b.rgb()))
                    throw new AssertionError("prepared vertex changed");
            }
        }
        var palette=LightningAppearance.palette(r,true);
        for(int round=0;round<4;round++){sample(r,palette,false,100000);sample(r,palette,true,100000);}
        long[] original=new long[7],compiled=new long[7];
        for(int round=0;round<7;round++) {
            if(round%2==0){original[round]=sample(r,palette,false,200000);compiled[round]=sample(r,palette,true,200000);}
            else {compiled[round]=sample(r,palette,true,200000);original[round]=sample(r,palette,false,200000);}
        }
        Arrays.sort(original);Arrays.sort(compiled);
        System.out.println("PASS: "+checks+" exact compiled color comparisons and projection snapshot parity");
        System.out.println("MATERIAL MICROBENCH: 200000 samples, median string="+original[3]/1e6+"ms compiled="+compiled[3]/1e6+"ms (CPU microbenchmark, not game FPS)");
    }
    private static long sample(LightningRecipe r,LightningAppearance.Palette palette,boolean compiled,int count) {
        double sum=0;long start=System.nanoTime();
        for(int i=0;i<count;i++) {
            float[] rgb=compiled?palette.colorAt((i%1001)/1000.0):LightningAppearance.colorAt(r,true,(i%1001)/1000.0);
            sum+=rgb[0]+rgb[1]+rgb[2];
        }
        sink=sum;return System.nanoTime()-start;
    }
}
