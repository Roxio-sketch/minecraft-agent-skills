package dev.lightninglab;

import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public final class LightningCheck {
    public static void main(String[] args) {
        double[][] vectors={{0,0,0},{.1,.2,.8285183685716719},{1.25,-3.75,.7282445621599388},{289.2,7.1,-.6433033650825536},{-500,120,-.2022023529315641}};
        for(double[] v:vectors)if(Math.abs(SimplexNoise.sample(v[0],v[1])-v[2])>1e-12)throw new AssertionError("simplex Java/JS vector mismatch");
        for(String mode:List.of("grow","ring"))for(int direction:new int[]{1,-1}){
            LightningRecipe fixed=new LightningRecipe();fixed.orbitAnimation=mode;fixed.orbitDirection=direction;fixed.throttle=1;
            Map<Integer,Vec3> roots=new HashMap<>();
            for(double tick=0;tick<48;tick+=.35)for(var seg:new OrbitField(fixed).sample(Vec3.ZERO,tick)){
                if(seg.alongA()!=0)continue;
                Vec3 prior=roots.putIfAbsent(seg.strand(),seg.a());
                if(prior!=null&&!prior.equals(seg.a()))throw new AssertionError(mode+" root drift");
            }
            if(roots.size()!=fixed.strands)throw new AssertionError("unobserved roots");
        }
        var points=List.of(new Vec3(0,3,0),new Vec3(.2,2,0),new Vec3(-.1,1,.1),Vec3.ZERO);
        for(Vec3 cam:List.of(new Vec3(0,1,-5),new Vec3(5,1,0),new Vec3(0,10,0),Vec3.ZERO)){
            var joins=RibbonGeometry.joins(points,cam);
            if(joins.size()!=points.size())throw new AssertionError("missing shared join");
            for(Vec3 join:joins)if(!Double.isFinite(join.x)||!Double.isFinite(join.y)||!Double.isFinite(join.z)||join.length()>2.000001||join.length()<.999999)throw new AssertionError("invalid ribbon miter");
        }
        LightningRecipe r = new LightningRecipe().validate();
        OrbitField o = new OrbitField(r);
        var orbit0 = o.sample(new Vec3(0,0,0), 0);
        var orbit1 = o.sample(new Vec3(0,0,0), .35);
        if (orbit0.isEmpty() || orbit0.size() > r.strands * r.samples || orbit1.isEmpty()) throw new AssertionError("orbit pulse count");
        if (!Double.isFinite(orbit0.get(0).a().x) || !Double.isFinite(orbit0.get(0).a().y)) throw new AssertionError("orbit emitter invalid");
        if (orbit0.equals(orbit1)) throw new AssertionError("orbit pulse is static");
        r.orbitAnimation = "spektr";
        var source0 = new OrbitField(r).sample(new Vec3(0,0,0), 0);
        var source1 = new OrbitField(r).sample(new Vec3(0,0,0), .35);
        if (source0.isEmpty() || source1.isEmpty() || source0.equals(source1)) throw new AssertionError("spektr axial pulse");
        r.orbitAnimation = "ring";
        var ring = new OrbitField(r).sample(new Vec3(0,0,0), .35);
        if (ring.isEmpty()) throw new AssertionError("ring arc pulse");
        r.orbitAnimation = "grow";
        BoltGeometry b = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (b.segments().isEmpty() || b.segments().size() > r.maxSegments) throw new AssertionError("bolt budget");
        if (!b.segments().get(0).a().equals(new Vec3(0,18,0))) throw new AssertionError("endpoint moved");
        BoltGeometry same = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (!b.segments().equals(same.segments())) throw new AssertionError("seed is not deterministic");
        r.seed += 1;
        BoltGeometry different = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (b.segments().equals(different.segments())) throw new AssertionError("seed does not vary shape");
        for (BoltGeometry.Segment s : b.segments()) if (!Double.isFinite(s.a().x) || !Double.isFinite(s.b().x)) throw new AssertionError("nonfinite");
        LightningEnvelope env = new LightningEnvelope(r);
        if (env.propagation(0) != 0 || env.propagation(99) != 1 || env.brightness(99) != 0) throw new AssertionError("envelope");
        r.mode = "rain";
        var rain = RainGeometry.generate(r);
        if (rain.size() != r.rainCount) throw new AssertionError("rain count");
        for (RainGeometry.Bolt rb : rain) for (BoltGeometry.Segment s : rb.geometry().segments()) if (s.depth() > r.rainMaxBranchDepth) throw new AssertionError("rain branch depth");
        if (RainGeometry.finished(rain,r.durationTicks)) throw new AssertionError("staggered last bolt cleared early");
        if (!RainGeometry.finished(rain,r.durationTicks+(r.rainCount-1)*r.rainStagger)) throw new AssertionError("rain never cleared");
        for(int seed=0;seed<16;seed++){
            LightningRecipe extreme=new LightningRecipe();extreme.seed=seed;extreme.strikeRoughness=.85;extreme.strikeDisplacement=12;extreme.strikeGenerations=9;extreme.maxSegments=1600;
            var tree=BoltGeometry.generate(new Vec3(0,18,0),Vec3.ZERO,extreme);
            if(tree.segments().size()>extreme.maxSegments)throw new AssertionError("extreme budget");
            for(var seg:tree.segments())if(seg.depth()==0&&seg.b().y>=seg.a().y)throw new AssertionError("main channel folds back");
            extreme.branchProbability=1;extreme.rainBranchProbability=.12;extreme.rainMaxBranches=1;
            for(var rb:RainGeometry.generate(extreme))if(rb.geometry().segments().stream().filter(s->s.depth()>0).map(BoltGeometry.Segment::mask).distinct().count()>1)throw new AssertionError("rain hard branch cap");
        }
        for(String mode:List.of("grow","ring"))for(int direction:new int[]{1,-1})for(double speed:new double[]{.1,2,20,120})for(double pulse:new double[]{.5,6,20}){
            LightningRecipe speedRecipe=new LightningRecipe();speedRecipe.orbitAnimation=mode;speedRecipe.orbitDirection=direction;speedRecipe.orbitExtendSpeed=speed;speedRecipe.pulseInterval=pulse;speedRecipe.lengthRandomness=0;speedRecipe.orbitWobble=0;speedRecipe.throttle=1;
            OrbitField field=new OrbitField(speedRecipe);var timing=field.timing(speedRecipe.seed);
            double travel=mode.equals("ring")?Math.min(Math.PI*1.85*speedRecipe.orbitRadius,speedRecipe.arcLength):Math.min(speedRecipe.rodLength*.95,speedRecipe.arcLength);
            double start=timing.interval()*(2-LightningMath.unit(speedRecipe.seed,18)),age=timing.onset()+travel*20/speed*.25,dt=Math.min(1,travel*20/speed*.1);
            var first=field.sample(Vec3.ZERO,start+age).stream().filter(s->s.strand()==0).toList();
            var second=field.sample(Vec3.ZERO,start+age+dt).stream().filter(s->s.strand()==0).toList();
            Vec3 p=first.get(first.size()-1).b(),q=second.get(second.size()-1).b();
            double distance=mode.equals("grow")?Math.abs(q.x-p.x):Math.abs(Math.atan2(p.x*q.z-p.z*q.x,p.x*q.x+p.z*q.z))*speedRecipe.orbitRadius;
            if(Math.abs(distance/dt*20-speed)>1e-7||!first.get(0).a().equals(second.get(0).a()))throw new AssertionError("physical speed/root");
            var full=field.sample(Vec3.ZERO,start+timing.onset()+travel*20/speed+.000001).stream().filter(s->s.strand()==0).toList();
            if(full.size()!=speedRecipe.samples||full.get(full.size()-1).alongB()!=1)throw new AssertionError("slow extension cut off");
        }
        LightningRecipe cluster=new LightningRecipe();cluster.mode="rain";cluster.rainStyle="cluster";cluster.rainCount=9;cluster.rainSpread=3;cluster.rainDepthSpread=2.4;cluster.rainMaxBranchDepth=0;cluster.rainMaxBranches=0;cluster.impactDuration=40;
        var bundle=RainGeometry.generate(cluster);var replay=RainGeometry.generate(cluster);
        for(int i=0;i<bundle.size();i++){
            var rb=bundle.get(i);Vec3 hit=rb.impact().center();
            if(Math.pow(hit.x/(cluster.rainSpread*.5),2)+Math.pow(hit.z/(cluster.rainDepthSpread*.5),2)>1.000001)throw new AssertionError("cluster range");
            if(!rb.geometry().segments().equals(replay.get(i).geometry().segments()))throw new AssertionError("cluster replay");
            if(!rb.impact().sample(cluster.progressionTicks-.001).isEmpty()||!rb.impact().sample(cluster.progressionTicks+cluster.impactDuration).isEmpty())throw new AssertionError("impact timing");
            var hitSegments=rb.impact().sample(cluster.progressionTicks+2);
            if(hitSegments.isEmpty()||hitSegments.size()>cluster.impactRays*8+48+cluster.impactSparks+2)throw new AssertionError("impact budget");
            for(var seg:hitSegments)if(!Double.isFinite(seg.a().x)||!Double.isFinite(seg.b().y))throw new AssertionError("impact finite");
        }
        if(RainGeometry.finished(bundle,cluster.durationTicks+(cluster.rainCount-1)*cluster.rainStagger))throw new AssertionError("impact tail cut off");
        if(!RainGeometry.finished(bundle,cluster.progressionTicks+cluster.impactDuration+(cluster.rainCount-1)*cluster.rainStagger))throw new AssertionError("impact never expires");
        var elevated=RainGeometry.generate(cluster,Vec3.ZERO,(start,end)->new Vec3(end.x,2,end.z));
        if(elevated.get(0).impact().center().y!=2)throw new AssertionError("impact not at resolved ground");
        System.out.println("PASS: existing geometry/seed checks; physical tip speeds in both directions/modes, full slow growth, bounded cluster/replay, ground-resolved impact timing/budget, full impact tail lifetime");
    }
}
