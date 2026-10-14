package dev.firecoil;

import java.util.*;
import dev.firecoil.FireCore.V;

/** Only tiny embers use sprites; fire body and detached flames are 3D density. */
public final class Geometry {
 public record Vertex(V position,float u,float v,float kind,float age,float seed,float strength){}
 public record Quad(Vertex a,Vertex b,Vertex c,Vertex d){V center(){return a.position.add(c.position).mul(.5);}}
 private static Vertex vertex(V p,float u,float v,double age,double seed){return new Vertex(p,u,v,1,(float)age,(float)seed,1);}
 public static List<Quad> embers(FireCore core,V camera,V cameraRight,V cameraUp,double partial){
  List<Quad> quads=new ArrayList<>();
  for(FireCore.Particle p:core.pool)if(p.alive&&p.kind==2){
   double age=Math.max(0,Math.min(1,(p.age-.05+partial*.05)/p.life));V pos=p.previous.lerp(p.position,partial),right=cameraRight.mul(p.size*.5),up=cameraUp.mul(p.size*.5);
   quads.add(new Quad(vertex(pos.sub(right).sub(up),0,0,age,p.seed),vertex(pos.add(right).sub(up),1,0,age,p.seed),vertex(pos.add(right).add(up),1,1,age,p.seed),vertex(pos.sub(right).add(up),0,1,age,p.seed)));
  }
  quads.sort(Comparator.comparingDouble((Quad q)->q.center().sub(camera).length()).reversed());return quads;
 }
}
