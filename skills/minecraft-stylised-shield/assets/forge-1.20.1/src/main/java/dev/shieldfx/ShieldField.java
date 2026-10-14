package dev.shieldfx;

/** Fixed-step height/velocity solver. Units: height in blocks, coordinates in cells. */
public final class ShieldField {
    public final int size;
    private float[] height, velocity, nextHeight, nextVelocity;
    private final float[] previous;
    public final ShieldRecipe recipe;
    public ShieldField(int size) {
        this(defaultRecipe(size));
    }
    private static ShieldRecipe defaultRecipe(int size) { ShieldRecipe r=new ShieldRecipe();r.grid=size;return r; }
    public ShieldField(ShieldRecipe recipe) {
        this.recipe=recipe.validate(); int size=recipe.grid;
        if(size<16 || size>256) throw new IllegalArgumentException("grid 16..256");
        this.size=size; int n=size*size;
        height=new float[n]; velocity=new float[n]; nextHeight=new float[n]; nextVelocity=new float[n]; previous=new float[n];
    }
    public void drop(double x,double z,double radius,double strength) {
        if(!Double.isFinite(x+z+radius+strength)||radius<=0||Math.abs(strength)>0.5) throw new IllegalArgumentException("invalid drop");
        for(int j=1;j<size-1;j++) for(int i=1;i<size-1;i++) {
            double d=Math.max(0,1-Math.hypot(i-x,j-z)/radius);
            int k=j*size+i;float h=(float)((0.5-0.5*Math.cos(Math.PI*d))*strength);
            height[k]=clamp(height[k]+h);
            if(recipe.solver.equals("history"))velocity[k]=clamp(velocity[k]+h); // previous height follows impulse: initially zero velocity
        }
    }
    public void tick() {
        System.arraycopy(height,0,previous,0,height.length);
        for(int s=0;s<3;s++) step(); // 60 Hz simulation, independent of render FPS
    }
    private void step() {
        for(int j=1;j<size-1;j++) for(int i=1;i<size-1;i++) {
            int k=j*size+i;
            float average=(height[k-1]+height[k+1]+height[k-size]+height[k+size])*0.25f;
            // A sponge boundary reduces artificial reflection from the finite patch.
            int edge=Math.min(Math.min(i,j),Math.min(size-1-i,size-1-j));
            float damping=(float)(recipe.boundary.equals("absorb")&&edge<8?0.90+(recipe.damping-0.90)*edge/8.0:recipe.damping);
            float acceleration=(average-height[k])*(float)(2*recipe.speed*recipe.speed);
            if(recipe.solver.equals("history")) {
                nextHeight[k]=clamp((2*height[k]-velocity[k]+acceleration)*damping);
                nextVelocity[k]=height[k];
            } else {
                float v=(velocity[k]+acceleration)*damping;
                nextHeight[k]=clamp(height[k]+v);nextVelocity[k]=nextHeight[k]-height[k];
            }
        }
        if(recipe.boundary.equals("reflect")) {
            for(int i=1;i<size-1;i++) {
                nextHeight[i]=nextHeight[size+i];nextHeight[(size-1)*size+i]=nextHeight[(size-2)*size+i];
                nextHeight[i*size]=nextHeight[i*size+1];nextHeight[i*size+size-1]=nextHeight[i*size+size-2];
                nextVelocity[i]=nextVelocity[size+i];nextVelocity[(size-1)*size+i]=nextVelocity[(size-2)*size+i];
                nextVelocity[i*size]=nextVelocity[i*size+1];nextVelocity[i*size+size-1]=nextVelocity[i*size+size-2];
            }
            nextHeight[0]=nextHeight[size+1];nextHeight[size-1]=nextHeight[2*size-2];
            nextHeight[(size-1)*size]=nextHeight[(size-2)*size+1];nextHeight[size*size-1]=nextHeight[(size-1)*size-2];
            nextVelocity[0]=nextVelocity[size+1];nextVelocity[size-1]=nextVelocity[2*size-2];
            nextVelocity[(size-1)*size]=nextVelocity[(size-2)*size+1];nextVelocity[size*size-1]=nextVelocity[(size-1)*size-2];
        }
        float[] t=height; height=nextHeight; nextHeight=t;
        t=velocity; velocity=nextVelocity; nextVelocity=t;
    }
    private float clamp(float value) { return (float)Math.max(-recipe.maxHeight,Math.min(recipe.maxHeight,value)); }
    public float sample(int x,int z,float partial) {
        int k=Math.max(0,Math.min(size-1,z))*size+Math.max(0,Math.min(size-1,x));
        return previous[k]+(height[k]-previous[k])*partial;
    }
    public double energy() { double e=0; for(int k=0;k<height.length;k++) e+=height[k]*height[k]+velocity[k]*velocity[k]; return e; }
}


