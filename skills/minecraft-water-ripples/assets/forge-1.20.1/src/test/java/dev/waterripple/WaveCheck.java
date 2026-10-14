package dev.waterripple;
public final class WaveCheck {
    public static void main(String[] args) {
        WaveField f=new WaveField(65);f.drop(32,32,4,0.18);
        for(int i=0;i<12;i++)f.tick();
        if(Math.abs(f.sample(44,32,1))<1e-7)throw new AssertionError("wave did not propagate");
        for(int z=0;z<65;z++)for(int x=0;x<65;x++)
            if(Math.abs(f.sample(x,z,1)-f.sample(z,x,1))>1e-5)throw new AssertionError("symmetry");
        double early=f.energy();for(int i=0;i<900;i++)f.tick();
        if(!Double.isFinite(f.energy())||f.energy()>early*0.01)throw new AssertionError("damping/stability");
        WaveField a=new WaveField(65),b=new WaveField(65),sum=new WaveField(65);
        a.drop(24,32,3,0.1);b.drop(40,32,3,-0.1);sum.drop(24,32,3,0.1);sum.drop(40,32,3,-0.1);
        for(int i=0;i<25;i++){a.tick();b.tick();sum.tick();}
        for(int z=0;z<65;z++)for(int x=0;x<65;x++)if(Math.abs(a.sample(x,z,1)+b.sample(x,z,1)-sum.sample(x,z,1))>1e-5)throw new AssertionError("superposition");
        System.out.println("PASS: propagation, symmetry, stability, decay, signed interference");
        for(String mode:new String[]{"velocity","history"})for(String edge:new String[]{"absorb","reflect"}) {
            RippleRecipe p=new RippleRecipe();p.solver=mode;p.boundary=edge;p.maxHeight=0.12;
            WaveField w=new WaveField(p);w.drop(25,32,3,0.1);
            for(int i=0;i<500;i++){if(i%20==0)w.drop(40,30,4,-0.3);w.tick();}
            for(int z=0;z<65;z++)for(int x=0;x<65;x++)if(!Float.isFinite(w.sample(x,z,1))||Math.abs(w.sample(x,z,1))>0.12001)throw new AssertionError(mode+edge);
            System.out.println("PASS: bounded "+mode+" / "+edge);
        }
        RippleRecipe parity=new RippleRecipe();parity.solver="history";parity.boundary="reflect";
        WaveField w=new WaveField(parity);w.drop(24,32,3,.1);for(int i=0;i<25;i++)w.tick();
        System.out.println("PARITY: "+w.sample(32,32,1)+","+w.sample(25,30,1)+","+w.sample(44,32,1));
    }
}
