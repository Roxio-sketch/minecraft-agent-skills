package dev.firecoil;

import com.google.gson.*;
import dev.firecoil.FireCore.V;
import java.nio.file.*;
import java.util.*;

/** Meaningful fixed-step lifecycle, motion and strict-recipe checks without a Minecraft process. */
public final class CoreCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void reject(JsonObject value){try{Recipe.parse(value.toString());throw new AssertionError("Invalid recipe accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  String source=Files.readString(Path.of("../current-recipe.json"));Recipe r=Recipe.parse(source);JsonObject raw=r.json();check(raw.size()==36,"36-field schema");
  JsonObject bad=raw.deepCopy();bad.addProperty("unknown",0);reject(bad);bad=raw.deepCopy();bad.remove("height");reject(bad);bad=raw.deepCopy();bad.addProperty("schema","fire-coil/99");reject(bad);bad=raw.deepCopy();bad.addProperty("strands",1.5);reject(bad);bad=raw.deepCopy();bad.addProperty("width",0);reject(bad);bad=raw.deepCopy();bad.addProperty("intensity","1.0");reject(bad);bad=raw.deepCopy();bad.addProperty("edgeColor","red");reject(bad);
  V origin=new V(0,0,0);FireCore a=new FireCore(),b=new FireCore();a.resetSeed(r.i("seed"));b.resetSeed(r.i("seed"));
  for(int i=0;i<1200;i++){a.tick(r,origin,0,true,1);b.tick(r,origin,0,true,1);for(int frame=0;frame<3;frame++)Geometry.embers(b,new V(0,1,4),new V(1,0,0),new V(0,1,0),frame/3.0);}
  check(a.count()==b.count(),"Rendering never changes births or expiry");check(a.count()<50,"Default live particles below 50 after one minute");
  for(int i=0;i<FireCore.CAPACITY;i++)if(a.pool[i].alive){check(a.pool[i].position.equals(b.pool[i].position),"Frame-count-independent particle path");break;}
  check(a.count()>0,"Particles generated");check(a.spawnRejected==0,"Default births never overflow");
  for(int i=0;i<70;i++)a.tick(r,origin,0,false,1);check(a.count()==0&&a.ramp()==0,"Off drains all particle lifetimes and body ramp");
  FireCore capped=new FireCore();JsonObject maximum=raw.deepCopy();maximum.addProperty("emberRate",160);maximum.addProperty("wispRate",80);maximum.addProperty("emberLife",3);maximum.addProperty("wispLife",2);maximum.addProperty("rampTime",.05);Recipe max=Recipe.parse(maximum.toString());for(int i=0;i<400;i++)capped.tick(max,origin,0,true,1);check(capped.count()<=512&&capped.spawnRejected>0,"512 pool rejects overflow without growing");
  FireCore moving=new FireCore();for(int i=0;i<40;i++)moving.tick(r,origin,0,true,1);FireCore.Particle detached=Arrays.stream(moving.pool).filter(p->p.alive).findFirst().orElseThrow();V prior=detached.position;moving.tick(r,new V(1,0,0),0,false,1);check(detached.position.sub(prior).length()<.5,"Old wisps remain world-space during player movement");moving.tick(r,new V(15,0,0),0,false,1);check(moving.count()==0&&moving.teleports==1,"Teleport clears old particles");moving.world(new Object());check(moving.count()==0,"World replacement clears all particles");
  FireCore geometry=new FireCore();for(int i=0;i<20;i++)geometry.tick(r,origin,0,true,1);List<Geometry.Quad> mesh=Geometry.embers(geometry,new V(0,1,4),new V(1,0,0),new V(0,1,0),.5);check(mesh.size()==Arrays.stream(geometry.pool).filter(p->p.alive&&p.kind==2).count(),"Only embers use quads; no fire body or wisp cards");
  for(Geometry.Quad q:mesh)for(Geometry.Vertex v:List.of(q.a(),q.b(),q.c(),q.d()))check(Double.isFinite(v.position().length())&&Float.isFinite(v.u())&&Float.isFinite(v.v()),"Finite geometry");
  V p=FireCore.coil(r,origin,0,1,0,.5),translated=FireCore.coil(r,new V(7,2,-3),0,1,0,.5);check(translated.sub(p).sub(new V(7,2,-3)).length()<1e-9,"Body anchor is world-space");
  JsonObject report=new JsonObject();report.addProperty("passed",true);report.addProperty("checks",checks);report.addProperty("fields",raw.size());report.addProperty("defaultParticlesAfter60s",b.count());report.addProperty("maxParticles",capped.count());report.addProperty("rejectedAtMaximum",capped.spawnRejected);report.addProperty("recipeHash",r.hash());Files.createDirectories(Path.of("evidence"));Files.writeString(Path.of("evidence/core-check.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(report);
 }
}
