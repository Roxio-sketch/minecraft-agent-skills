package dev.vfxworkbench.effects.water;

import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.core.ParamState;

public final class WaterSurfaceCheck {
    public static void run() {
        long comparisons=0;
        for(int size:new int[]{16,65,129,256})for(String solver:new String[]{"velocity","history"})for(String boundary:new String[]{"absorb","reflect"}) {
            var state=new ParamState(ParamRegistry.schema(WaterSkillSchema.ID));state.set("grid",Integer.toString(size));state.set("solver",solver);state.set("boundary",boundary);
            var field=new WaveField(state);field.impulse(size*.43,size*.61,Math.max(2,size*.07),.18);for(int t=0;t<5;t++)field.tick();
            float width=state.getFloat("width"),strength=state.getFloat("normalStrength");double cell=width/(size-1);
            float[] h=new float[size*size],nx=h.clone(),ny=h.clone(),nz=h.clone();WaterSurface.sample(field,width,strength,.37f,h,nx,ny,nz);
            for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
                int i=z*size+x;
                double dx=(field.sample(x+1,z,.37f)-field.sample(x-1,z,.37f))/(2*cell)*strength;
                double dz=(field.sample(x,z+1,.37f)-field.sample(x,z-1,.37f))/(2*cell)*strength;
                double norm=Math.sqrt(1+dx*dx+dz*dz);
                if(Float.floatToIntBits(h[i])!=Float.floatToIntBits(field.sample(x,z,.37f))
                    ||Float.floatToIntBits(nx[i])!=Float.floatToIntBits((float)(.5-dx/norm*.5))
                    ||Float.floatToIntBits(ny[i])!=Float.floatToIntBits((float)(.5+.5/norm))
                    ||Float.floatToIntBits(nz[i])!=Float.floatToIntBits((float)(.5-dz/norm*.5)))throw new AssertionError("water surface changed at boundary/grid/solver "+x+","+z);
                comparisons+=4;
            }
        }
        System.out.println("PASS: "+comparisons+" exact water height/normal comparisons; 5 to 1 samples per node");
    }
}
