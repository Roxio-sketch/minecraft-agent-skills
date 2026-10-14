package dev.trailbeam;

import java.util.*;
import java.nio.file.*;

/** Deterministic behavioural checks, independent of OpenGL and a running game. */
public class GeometryCheck {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static Geometry.V v(double x,double y,double z){return new Geometry.V(x,y,z);}
    public static void main(String[] args)throws Exception {
        var h=new Geometry.History(1,.01,.2,5,32);
        h.add(0,v(0,0,0),v(0,1,0));h.add(.05,v(1,0,0),v(1,1,0));
        check(h.samples(.05).size()==6,"distance resampling");
        for(int i=1;i<h.samples(.05).size();i++)check(h.samples(.05).get(i).a().sub(h.samples(.05).get(i-1).a()).len()<=.200001,"step length");
        int old=h.samples(.05).size();h.add(.05,v(2,0,0),v(2,1,0));check(h.samples(.05).size()==old,"same timestamp dedup");
        h.expire(2);check(h.samples(2).isEmpty(),"expire without new emission");
        h.add(3,v(0,0,0),v(0,1,0));h.add(3.1,v(10,0,0),v(10,1,0));check(h.samples(3.1).size()==1,"teleport break");
        h.add(1,v(1,0,0),v(1,1,0));check(h.samples(1).size()==1,"time rollback resets");
        for(int i=1;i<200;i++)h.add(1+i*.001,v(1+i*.02,0,0),v(1+i*.02,1,0));check(h.samples(1.2).size()<=32,"memory cap");
        var p=Geometry.path(List.of(v(0,0,0),v(0,0,0),v(0,0,2),v(0,0,5)));
        check(p.size()==3&&p.get(2).distance()==5,"duplicate points and physical UV distance");
        for(String kind:List.of("ribbon","cross","tube")) {
            var mesh=Geometry.sweep(p,v(0,0,10),kind,8,.3,s->1);
            check(mesh.size()==(kind.equals("tube")?16:kind.equals("cross")?4:2),"topology "+kind);
            for(var q:mesh)check(q.a().position().sub(q.b().position()).len()>1e-6,"end-on camera width "+kind);
        }
        var paired=List.of(new Geometry.Sample(v(0,0,0),v(0,2,0),0),new Geometry.Sample(v(1,0,0),v(1,2,0),.2));
        var mesh=Geometry.blade(paired,.5,1,1,s->1);
        check(mesh.get(0).a().position().equals(v(0,0,0))&&mesh.get(0).b().position().equals(v(0,2,0)),"two blade sockets retained");
        check(Math.abs(mesh.get(0).a().age()-.5)<1e-8,"age normalisation");
        // Render-frequency loops must not mutate simulation histories.
        var sim=new Geometry.History(1,.01,.1,3,128);for(int i=0;i<21;i++)sim.add(i/20.0,v(i*.03,0,0),v(i*.03,1,0));
        int count=sim.samples(1).size();for(int i=0;i<144;i++)sim.centerline(1);check(sim.samples(1).size()==count,"read-only multiple render passes");
        Recipe recipe=Recipe.parse(Files.readString(Path.of("src/main/resources/assets/trailbeam/recipes/beam.json")));
        check(recipe.layers().size()>=2,"layered material parser");
        check(recipe.layers().get(0).colorAt(.5,1)[3]==0,"complete lifetime fade");
        var uvHistory=new Geometry.History(1,0,.5,5,32);for(int i=0;i<=4;i++)uvHistory.add(i*.25,v(i,0,0),v(i,1,0));
        double before=uvHistory.centerline(1).get(uvHistory.centerline(1).size()-1).distance();
        var after=uvHistory.centerline(1.4);check(Math.abs(before-after.get(after.size()-1).distance())<1e-8,"UV does not reset when tail expires");
        boolean rejected=false;try{Recipe.parse(Files.readString(Path.of("src/main/resources/assets/trailbeam/recipes/beam.json")).replace("\"schema\": 1","\"schema\": 1,\"fakeField\": 5"));}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"unknown fields rejected");
        Paths.Spec design=Paths.parse(Files.readString(Path.of("src/main/resources/assets/trailbeam/paths/custom.json")));
        var first=Paths.generate(design,v(0,0,0),v(1,0,0),v(0,1,0),v(0,0,1),2);
        var same=Paths.generate(design,v(0,0,0),v(1,0,0),v(0,1,0),v(0,0,1),2);
        check(first.equals(same),"path generation deterministic at fixed time");
        check(first.get(0).points().size()==design.samples()+1,"path point budget");
        check(!first.equals(Paths.generate(design,v(0,0,0),v(1,0,0),v(0,1,0),v(0,0,1),3)),"helix path animation");
        var forked=new Paths.Spec("lightning",4,.5,3,1,731,96,3,.6);
        check(Paths.generate(forked,v(0,0,0),v(1,0,0),v(0,1,0),v(0,0,1),0).size()==4,"branch count");
        check(Paths.generate(forked,v(0,0,0),v(1,0,0),v(0,1,0),v(0,0,1),0).get(0).points().size()==97,"main strand budget");
        check(Recipe.parse(Files.readString(Path.of("src/main/resources/assets/trailbeam/recipes/beam.json")).replace("\"profile\": \"double\"","\"profile\": \"plasma\"")).layers().get(0).profile().equals("plasma"),"extended profile parser");
        System.out.println("TRAIL_BEAM_CORE_CHECKS="+checks+" passed");
    }
}
