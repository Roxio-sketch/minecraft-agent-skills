package dev.vfxworkbench.effects.water;

/** One height sample per grid node, reused for the four normal neighbours. */
final class WaterSurface {
    static void sample(WaveField field,float width,float strength,float partial,
                       float[] heights,float[] nx,float[] ny,float[] nz) {
        int size=field.size;double cell=width/(size-1);
        for(int z=0;z<size;z++)for(int x=0;x<size;x++)heights[z*size+x]=field.sample(x,z,partial);
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            int i=z*size+x;
            double dx=(heights[z*size+Math.min(size-1,x+1)]-heights[z*size+Math.max(0,x-1)])/(2*cell)*strength;
            double dz=(heights[Math.min(size-1,z+1)*size+x]-heights[Math.max(0,z-1)*size+x])/(2*cell)*strength;
            double norm=Math.sqrt(1+dx*dx+dz*dz);
            nx[i]=(float)(.5-dx/norm*.5);ny[i]=(float)(.5+.5/norm);nz[i]=(float)(.5-dz/norm*.5);
        }
    }
}
