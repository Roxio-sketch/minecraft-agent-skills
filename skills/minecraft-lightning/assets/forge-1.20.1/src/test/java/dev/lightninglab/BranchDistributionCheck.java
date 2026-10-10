package dev.lightninglab;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Distribution checks use real primary root positions, not descendant counts. */
final class BranchDistributionCheck {
 static void run(){int checks=0;
  for(int seed=0;seed<32;seed++)for(int cap:new int[]{4,16,64})for(int budget:new int[]{512,1200,7000}){
   var r=new LightningRecipe();r.seed=seed;r.strikeGenerations=8;r.branchProbability=1;r.branchLimit=cap;r.maxBranchDepth=3;r.maxSegments=budget;
   var g=BoltGeometry.generate(new Vec3(0,96,0),Vec3.ZERO,r);var again=BoltGeometry.generate(new Vec3(0,96,0),Vec3.ZERO,r);
   if(!g.segments().equals(again.segments())||g.segments().size()>budget)throw new AssertionError("determinism/budget");checks++;
   var roots=new LinkedHashMap<Long,BoltGeometry.Segment>();for(var x:g.segments())if(x.depth()==1)roots.putIfAbsent(x.mask(),x);
   if(roots.size()<4)throw new AssertionError("primary siblings starved");
   double lo=1,hi=0;Set<Integer> bands=new HashSet<>();
   for(var x:roots.values()){lo=Math.min(lo,x.alongA());hi=Math.max(hi,x.alongA());bands.add(Math.min(3,(int)(x.alongA()*4)));}
   if(hi-lo<.5||bands.size()<3)throw new AssertionError("root clustering seed="+seed+" cap="+cap+" budget="+budget+" roots="+roots.size()+" span="+(hi-lo));checks++;
   long paths=g.segments().stream().filter(x->x.depth()>0).map(BoltGeometry.Segment::mask).distinct().count();
   if(paths>cap||g.segments().stream().anyMatch(x->x.depth()>3))throw new AssertionError("branch cap/depth");checks++;
  }
  for(int depth:new int[]{0,1,2,3,4}){var r=new LightningRecipe();r.branchProbability=1;r.branchLimit=64;r.maxBranchDepth=depth;r.maxSegments=7000;
   var g=BoltGeometry.generate(new Vec3(0,96,0),Vec3.ZERO,r);if(g.segments().stream().anyMatch(x->x.depth()>depth))throw new AssertionError("depth is recursion level");checks++;
   if(depth==0&&g.segments().stream().anyMatch(x->x.depth()>0))throw new AssertionError("zero depth branches");
  }
  System.out.println("PASS: breadth-first primary-root distribution, seeded replay and strict budgets/caps/depth ("+checks+" checks)");
 }
}
