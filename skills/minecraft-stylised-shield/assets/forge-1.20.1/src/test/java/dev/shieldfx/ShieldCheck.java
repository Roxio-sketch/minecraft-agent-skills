package dev.shieldfx;

import java.nio.file.Files;
import java.util.List;
import dev.shieldfx.ShieldGeometry.*;

/** Executable checks for lifecycle, exact surface partition and rigid shard motion. */
public final class ShieldCheck {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        ShieldLifecycle state=new ShieldLifecycle();state.reset(.16);
        require(state.hit(.08,2.4)&&state.life()==.08,"first damage");
        require(state.hit(.08,2.4)&&state.life()==0&&state.state()==ShieldLifecycle.State.INTACT,"zero must keep intact red shell");
        require(state.hit(.08,2.4)&&state.state()==ShieldLifecycle.State.SHATTERING,"next zero-life hit must break");
        require(!state.hit(.08,2.4),"breaking ignores hits");state.tick(10);require(state.state()==ShieldLifecycle.State.BROKEN&&!state.hit(.08,2.4),"finite one shot");
        state.reset(1);require(state.state()==ShieldLifecycle.State.INTACT&&state.life()==1&&state.age()==0,"reset restores shield");
        state.reset(.01);state.hit(.08,.6);require(state.life()==0&&state.state()==ShieldLifecycle.State.INTACT,"overshoot clamps");state.hit(.08,.6);for(int i=0;i<12;i++)state.tick(.05);require(state.state()==ShieldLifecycle.State.BROKEN,"fixed tick endpoint tolerance");
        for(String shape:List.of("sphere","dome","convex","flat")){
            ShieldRecipe r=new ShieldRecipe();r.shape=shape;r.segments=24;r.shardCount=48;r.distortion=0;r.pulseAmplitude=0;
            List<Triangle> mesh=ShieldGeometry.mesh(r);List<Patch> shards=ShieldGeometry.fracture(mesh,new V(0,0,2),r,42);
            require(shards.size()==48,shape+" shard count");double original=mesh.stream().mapToDouble(Triangle::area).sum(),partition=shards.stream().flatMap(p->p.triangles().stream()).mapToDouble(Triangle::area).sum();
            require(Math.abs(original-partition)<1e-6,shape+" exact partition area");
            for(Patch p:shards){require(!p.edges().isEmpty(),shape+" outline");Triangle t=p.triangles().get(0);V a=p.point(t.a().pos(),.7,r),b=p.point(t.b().pos(),.7,r);double shrink=1-.035;
                require(Math.abs(a.sub(b).length()-t.a().pos().sub(t.b().pos()).length()*shrink)<1e-6,shape+" rigid motion");
                require(Double.isFinite(a.x()+a.y()+a.z()),shape+" finite positions");
            }
            r.distortion=.15;r.pulseAmplitude=.1;List<Triangle> frozen=ShieldGeometry.freezeSurface(mesh,r,.7);require(frozen.get(0).a().pos().sub(mesh.get(0).a().pos()).length()>1e-6,shape+" freeze actual displaced surface");
        }
        java.nio.file.Path old=Files.createTempFile("shield-v2", ".json");try{Files.writeString(old,"{\"schema\":2,\"life\":0}");ShieldRecipe r=ShieldRecipe.read(old);require(r.shardCount==110&&r.shatterDuration==2.4&&r.life==0,"old schema2 defaults");}finally{Files.deleteIfExists(old);}
        try{ShieldRecipe bad=new ShieldRecipe();bad.shardCount=241;bad.validate();throw new AssertionError("invalid shard count accepted");}catch(IllegalArgumentException expected){}
        System.out.println("ShieldCheck passed: lifecycle, four surface partitions, rigid motion, displaced snapshot and schema2 defaults");
    }
}
