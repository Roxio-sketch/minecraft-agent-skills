package dev.lightninglab;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;

final class ImpactColorCheck {
    static void run(){
        int checked=0;
        for(boolean enabled:new boolean[]{false,true})for(double ratio:new double[]{0,25,50,75,100}){
            var r=new LightningRecipe();r.gradientEnabled=enabled;r.gradientRatio=ratio;
            r.gradientStart="#ff0000";r.gradientEnd="#00ffff";r.coreColor="#ffffff";r.glowColor="#87cfff";
            var impact=new ImpactGeometry(Vec3.ZERO,r);
            for(double age:new double[]{.1,.8,2,5,10})for(var s:impact.sample(r.progressionTicks+age)){
                if(s.alongA()!=1||s.alongB()!=1)throw new AssertionError("impact restarts the parent gradient");
                float[] core=enabled?(ratio==100?new float[]{1,0,0}:new float[]{0,1,1}):new float[]{1,1,1};
                float[] glow=enabled?(ratio==100?new float[]{135/255f,0,0}:new float[]{0,207/255f,1}):new float[]{135/255f,207/255f,1};
                if(!Arrays.equals(core,LightningAppearance.colorAt(r,false,s.alongA()))||!Arrays.equals(glow,LightningAppearance.colorAt(r,true,s.alongB())))throw new AssertionError("impact does not inherit actual core/glow tip tint");
                checked++;
            }
        }
        System.out.println("PASS: impact rays/rings/sparks/flash inherit parent tip colors including ratio endpoints and independent base tints ("+checked+" segments)");
    }
}
